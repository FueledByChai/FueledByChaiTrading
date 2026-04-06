package com.fueledbychai.paradex.example.trading;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.aster.common.api.AsterConfiguration;
import com.fueledbychai.broker.BrokerAccountInfoListener;
import com.fueledbychai.broker.BrokerFactory;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;
import com.fueledbychai.websocket.ProxyConfig;

/**
 * Example that places a market order for BTC on Aster using the broker API.
 *
 * Credentials are read from a local properties file that is NOT checked into
 * version control. Copy {@code aster-credentials.properties.template} to
 * {@code aster-credentials.properties} and fill in your values before running.
 *
 * Required properties:
 * <ul>
 *   <li>{@code aster.l1.account} - your L1 wallet address (account owner)</li>
 *   <li>{@code aster.api.secret} - your signer private key (hex)</li>
 *   <li>{@code aster.api.wallet} - your API/signer wallet address</li>
 * </ul>
 *
 * Usage:
 * <pre>
 *   mvn compile exec:java \
 *       -Dexec.mainClass="com.fueledbychai.paradex.example.trading.AsterMarketOrderExample" \
 *       -Dexec.args="BUY 1"
 * </pre>
 */
public class AsterMarketOrderExample {

    private static final Logger logger = LoggerFactory.getLogger(AsterMarketOrderExample.class);

    private static final String[] CREDENTIALS_SEARCH_PATHS = {
            "examples/CryptoExamples/aster-credentials.properties",
            "aster-credentials.properties"
    };
    private static final String DEFAULT_SYMBOL = "BTCUSDT";
    private static final BigDecimal DEFAULT_SIZE = new BigDecimal("1");
    private static final Duration FILL_WAIT = Duration.ofSeconds(15);

    public static void main(String[] args) throws Exception {
        TradeDirection direction = TradeDirection.BUY;
        BigDecimal size = DEFAULT_SIZE;

        if (args.length >= 1) {
            direction = TradeDirection.valueOf(args[0].toUpperCase());
        }
        if (args.length >= 2) {
            size = new BigDecimal(args[1]);
        }

        loadCredentials();

        // Enable SOCKS proxy (default 127.0.0.1:1080) if configured in the properties file
        ProxyConfig.getInstance().setRunningLocally(
                Boolean.parseBoolean(System.getProperty("aster.run.proxy", "true")));
        logger.info("Proxy config: {}", ProxyConfig.getInstance().getProxy());

        new AsterMarketOrderExample().placeMarketOrder(DEFAULT_SYMBOL, direction, size);
    }

    private static void loadCredentials() throws IOException {
        String override = System.getProperty("credentials.file");
        Path path = null;
        if (override != null) {
            path = Paths.get(override);
        } else {
            for (String candidate : CREDENTIALS_SEARCH_PATHS) {
                Path p = Paths.get(candidate);
                if (Files.exists(p)) {
                    path = p;
                    break;
                }
            }
        }
        if (path == null || !Files.exists(path)) {
            throw new IllegalStateException(
                    "Credentials file not found. Searched: "
                            + String.join(", ", CREDENTIALS_SEARCH_PATHS)
                            + "\nCopy aster-credentials.properties.template to "
                            + "aster-credentials.properties and fill in your values.");
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            props.load(fis);
        }

        for (String key : props.stringPropertyNames()) {
            if (System.getProperty(key) == null) {
                System.setProperty(key, props.getProperty(key));
            }
        }

        AsterConfiguration.reset();

        if (!AsterConfiguration.getInstance().hasPrivateKeyConfiguration()) {
            throw new IllegalStateException(
                    "Credentials file is missing required properties: "
                            + "aster.l1.account, aster.api.secret, aster.api.wallet");
        }

        AsterConfiguration cfg = AsterConfiguration.getInstance();
        logger.info("Loaded Aster credentials from {}", path.toAbsolutePath());
        logger.info("  api.key (user):   {}...{}", cfg.getApiKey().substring(0, 6),
                cfg.getApiKey().substring(cfg.getApiKey().length() - 4));
        logger.info("  api.signer:       {}...{}", cfg.getApiSigner().substring(0, 6),
                cfg.getApiSigner().substring(cfg.getApiSigner().length() - 4));
        logger.info("  api.secret length: {}", cfg.getApiSecret().length());
    }

    public void placeMarketOrder(String symbol, TradeDirection direction, BigDecimal size) throws Exception {
        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.ASTER);
        Ticker ticker = registry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (ticker == null) {
            ticker = registry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        }
        if (ticker == null) {
            throw new IllegalArgumentException("Unknown Aster perpetual ticker: " + symbol);
        }

        IBroker broker = BrokerFactory.getInstance(Exchange.ASTER);
        CountDownLatch fillLatch = new CountDownLatch(1);

        broker.addOrderEventListener(event -> logger.info("Order event: {}", event));
        broker.addFillEventListener(fill -> {
            logger.info("Fill: {}", fill);
            fillLatch.countDown();
        });
        broker.addBrokerAccountInfoListener(new BrokerAccountInfoListener() {
            @Override
            public void accountEquityUpdated(double equity) {
                logger.info("Account equity: {}", equity);
            }

            @Override
            public void availableFundsUpdated(double availableFunds) {
                logger.info("Available funds: {}", availableFunds);
            }
        });

        try {
            broker.connect();

            OrderTicket order = new OrderTicket();
            order.setTicker(ticker)
                    .setClientOrderId(broker.getNextOrderId())
                    .setDirection(direction)
                    .setSize(size)
                    .setType(OrderTicket.Type.MARKET)
                    .setDuration(OrderTicket.Duration.GOOD_UNTIL_CANCELED);

            logger.info("Placing {} market order for {} size={}", direction, ticker.getSymbol(), size);
            BrokerRequestResult result = broker.placeOrder(order);
            logger.info("Order result: {}", result);

            logger.info("Waiting up to {}s for fill...", FILL_WAIT.toSeconds());
            if (!fillLatch.await(FILL_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warn("No fill received within timeout - order may still be processing");
            }
        } finally {
            if (broker.isConnected()) {
                broker.disconnect();
            }
        }
    }
}
