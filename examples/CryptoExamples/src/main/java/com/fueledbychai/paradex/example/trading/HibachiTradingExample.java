package com.fueledbychai.paradex.example.trading;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.BrokerAccountInfoListener;
import com.fueledbychai.broker.BrokerFactory;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Modifier;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.hibachi.common.api.HibachiConfiguration;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;
import com.fueledbychai.websocket.ProxyConfig;

/**
 * Places a single post-only limit buy on Hibachi at the current best bid, waits briefly,
 * then cancels any remainder.
 *
 * <p>Credentials are loaded from a {@code .env} file. By default the example looks for
 * {@code .env} in the current working directory; pass {@code --env=/path/to/.env} as the
 * first CLI arg, or set {@code -Dhibachi.env.file=/path/to/.env}, to override.
 *
 * <p>Expected keys (either form works — the loader normalizes to System properties that
 * {@link HibachiConfiguration} reads):
 * <pre>
 * HIBACHI_API_KEY=...
 * HIBACHI_API_SECRET=...
 * HIBACHI_ACCOUNT_ID=...
 * HIBACHI_ENVIRONMENT=prod        # or "testnet"
 * </pre>
 */
public class HibachiTradingExample {

    protected static final Logger logger = LoggerFactory.getLogger(HibachiTradingExample.class);
    protected static final String DEFAULT_SYMBOL = "SOL";
    protected static final BigDecimal DEFAULT_SIZE = new BigDecimal("0.0001");
    protected static final Duration INITIAL_QUOTE_TIMEOUT = Duration.ofSeconds(10);
    protected static final Duration ORDER_OBSERVATION_WINDOW = Duration.ofSeconds(30);
    protected static final BigDecimal FALLBACK_TICK_SIZE = new BigDecimal("0.001");

    public void executeTrade(String symbol) throws Exception {
        ProxyConfig.getInstance().setRunningLocally(
                Boolean.parseBoolean(System.getProperty("hibachi.run.proxy", "true")));
        logger.info("Proxy config: {}", ProxyConfig.getInstance().getProxy());

        if (!HibachiConfiguration.getInstance().hasPrivateApiConfiguration()) {
            throw new IllegalStateException("Hibachi trading requires "
                    + HibachiConfiguration.HIBACHI_API_KEY + ", "
                    + HibachiConfiguration.HIBACHI_API_SECRET + ", and "
                    + HibachiConfiguration.HIBACHI_ACCOUNT_ID
                    + " — set them in your .env file.");
        }

        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.HIBACHI);
        Ticker ticker = registry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (ticker == null) {
            ticker = registry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        }
        if (ticker == null) {
            throw new IllegalArgumentException("Unknown Hibachi perpetual ticker: " + symbol);
        }

        QuoteEngine quoteEngine = QuoteEngine.getInstance(Exchange.HIBACHI);
        IBroker broker = BrokerFactory.getInstance(Exchange.HIBACHI);
        AtomicReference<ILevel1Quote> latestQuote = new AtomicReference<>();
        CountDownLatch initialQuoteLatch = new CountDownLatch(1);

        quoteEngine.startEngine();
        quoteEngine.subscribeLevel1(ticker, quote -> {
            latestQuote.set(quote);
            // logger.info("L1 {} bid={} ask={} mark={}", quote.getTicker().getSymbol(),
            //         value(quote, QuoteType.BID), value(quote, QuoteType.ASK),
            //         value(quote, QuoteType.MARK_PRICE));
            if (quote.containsType(QuoteType.BID) || quote.containsType(QuoteType.ASK)) {
                initialQuoteLatch.countDown();
            }
        });

        broker.addOrderEventListener(event -> logger.info("Order Event: {}", event));
        broker.addFillEventListener(fill -> logger.info("Fill Event: {}", fill));
        broker.addBrokerAccountInfoListener(new BrokerAccountInfoListener() {
            @Override
            public void accountEquityUpdated(double equity) {
                logger.info("Account equity updated: {}", equity);
            }

            @Override
            public void availableFundsUpdated(double availableFunds) {
                logger.info("Available funds updated: {}", availableFunds);
            }
        });

        try {
            if (!initialQuoteLatch.await(INITIAL_QUOTE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timed out waiting for initial Hibachi quote for " + ticker.getSymbol());
            }

            broker.connect();

            OrderTicket order = new OrderTicket();
            order.setTicker(ticker)
                    .setClientOrderId(broker.getNextOrderId())
                    .setDirection(TradeDirection.BUY)
                    .setSize(DEFAULT_SIZE)
                    .setType(Type.LIMIT)
                    .setDuration(OrderTicket.Duration.GOOD_UNTIL_CANCELED)
                    .setLimitPrice(new BigDecimal("80.00"))
                    .addModifier(Modifier.POST_ONLY);

            logger.info("Placing Hibachi post-only limit order for {} size={} price={}",
                    ticker.getSymbol(), order.getSize(), order.getLimitPrice());
            BrokerRequestResult result = broker.placeOrder(order);
            logger.info("Place order result: {}", result);

            Thread.sleep(ORDER_OBSERVATION_WINDOW.toMillis());

            List<OrderTicket> open = broker.getOpenOrders();
            if (!open.isEmpty()) {
                logger.info("Canceling {} remaining Hibachi open order(s)", open.size());
                logger.info("Cancel result: {}", broker.cancelAllOrders(ticker));
            }
        } finally {
            if (broker.isConnected()) {
                broker.disconnect();
            }
            quoteEngine.stopEngine();
        }
    }

    protected BigDecimal toPassiveBuyPrice(Ticker ticker, ILevel1Quote quote) {
        if (quote == null) {
            throw new IllegalStateException("No quote available for " + ticker.getSymbol());
        }

        BigDecimal tickSize = ticker.getMinimumTickSize();
        if (tickSize == null || tickSize.signum() <= 0) {
            tickSize = FALLBACK_TICK_SIZE;
        }

        BigDecimal bestBid = quote.containsType(QuoteType.BID) ? quote.getValue(QuoteType.BID) : null;
        if (bestBid != null && bestBid.signum() > 0) {
            return bestBid.stripTrailingZeros();
        }

        BigDecimal bestAsk = quote.containsType(QuoteType.ASK) ? quote.getValue(QuoteType.ASK) : null;
        if (bestAsk != null && bestAsk.compareTo(tickSize) > 0) {
            return bestAsk.subtract(tickSize).stripTrailingZeros();
        }

        throw new IllegalStateException("Unable to derive passive buy price for " + ticker.getSymbol());
    }

    protected String value(ILevel1Quote quote, QuoteType type) {
        if (quote == null || type == null || !quote.containsType(type)) {
            return "null";
        }
        return String.valueOf(quote.getValue(type));
    }

    /**
     * Loads {@code KEY=VALUE} pairs from a dotenv-style file into System properties so
     * {@link HibachiConfiguration} can pick them up. Keys in the file may use either the
     * env-style form ({@code HIBACHI_API_KEY}) or the dotted form ({@code hibachi.api.key}) —
     * each is set under both forms so downstream lookups work regardless of which the
     * configuration class checks.
     *
     * <p>Lines that are blank, start with {@code #}, or are missing {@code =} are ignored.
     * Surrounding single/double quotes around the value are stripped. Existing System
     * properties take precedence and are not overwritten.
     */
    protected static void loadDotEnv(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            logger.info("No .env file at {} — relying on existing system properties / env vars", file);
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read .env file: " + file, e);
        }

        int loaded = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }

            String envForm = key.toUpperCase().replace('.', '_');
            String dottedForm = key.toLowerCase().replace('_', '.');

            if (System.getProperty(envForm) == null) {
                System.setProperty(envForm, value);
            }
            if (System.getProperty(dottedForm) == null) {
                System.setProperty(dottedForm, value);
            }
            loaded++;
        }
        logger.info("Loaded {} entries from {}", loaded, file);
        // Reset so the singleton picks up the values we just set.
        HibachiConfiguration.reset();
    }

    protected static Path resolveEnvPath(String[] args) {
        for (String arg : args) {
            if (arg != null && arg.startsWith("--env=")) {
                return Paths.get(arg.substring("--env=".length()));
            }
        }
        String prop = System.getProperty("hibachi.env.file");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop);
        }
        return Paths.get(".env");
    }

    public static void main(String[] args) throws Exception {
        loadDotEnv(resolveEnvPath(args));

        String symbol = DEFAULT_SYMBOL;
        for (String arg : args) {
            if (arg != null && !arg.startsWith("--")) {
                symbol = arg;
                break;
            }
        }

        new HibachiTradingExample().executeTrade(symbol);
    }
}
