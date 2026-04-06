package com.fueledbychai.paradex.example.trading;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.aster.common.api.AsterConfiguration;
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
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class AsterTradingExample {

    protected static final Logger logger = LoggerFactory.getLogger(AsterTradingExample.class);
    protected static final String DEFAULT_SYMBOL = "BTCUSDT";
    protected static final BigDecimal DEFAULT_SIZE = new BigDecimal("0.001");
    protected static final Duration INITIAL_QUOTE_TIMEOUT = Duration.ofSeconds(10);
    protected static final Duration ORDER_OBSERVATION_WINDOW = Duration.ofSeconds(10);
    protected static final BigDecimal FALLBACK_TICK_SIZE = new BigDecimal("0.1");

    public void executeTrade(String symbol) throws Exception {
        if (!AsterConfiguration.getInstance().hasPrivateKeyConfiguration()) {
            throw new IllegalStateException("Aster trading requires aster.l1.account, aster.api.secret, and aster.api.wallet.");
        }

        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.ASTER);
        Ticker ticker = registry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (ticker == null) {
            ticker = registry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        }
        if (ticker == null) {
            throw new IllegalArgumentException("Unknown Aster perpetual ticker: " + symbol);
        }

        QuoteEngine quoteEngine = QuoteEngine.getInstance(Exchange.ASTER);
        IBroker broker = BrokerFactory.getInstance(Exchange.ASTER);
        AtomicReference<ILevel1Quote> latestQuote = new AtomicReference<>();
        CountDownLatch initialQuoteLatch = new CountDownLatch(1);

        quoteEngine.startEngine();
        quoteEngine.subscribeLevel1(ticker, quote -> {
            latestQuote.set(quote);
            logger.info("L1 {} bid={} ask={} mark={}", quote.getTicker().getSymbol(), value(quote, QuoteType.BID),
                    value(quote, QuoteType.ASK), value(quote, QuoteType.MARK_PRICE));
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
                throw new IllegalStateException("Timed out waiting for initial Aster quote for " + ticker.getSymbol());
            }

            broker.connect();

            // Aster order entry is signed REST; fills/order updates arrive on the user-data websocket.
            OrderTicket order = new OrderTicket();
            order.setTicker(ticker).setClientOrderId(broker.getNextOrderId()).setDirection(TradeDirection.BUY)
                    .setSize(DEFAULT_SIZE).setType(Type.LIMIT)
                    .setDuration(OrderTicket.Duration.GOOD_UNTIL_CANCELED)
                    .setLimitPrice(toPassiveBuyPrice(ticker, latestQuote.get())).addModifier(Modifier.POST_ONLY);

            logger.info("Placing Aster post-only limit order for {} size={} price={}", ticker.getSymbol(),
                    order.getSize(), order.getLimitPrice());
            BrokerRequestResult result = broker.placeOrder(order);
            logger.info("Place order result: {}", result);

            Thread.sleep(ORDER_OBSERVATION_WINDOW.toMillis());

            if (!broker.getOpenOrders().isEmpty()) {
                logger.info("Canceling remaining Aster open orders for {}", ticker.getSymbol());
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

    public static void main(String[] args) throws Exception {
        // Optional overrides:
        // System.setProperty(AsterConfiguration.ASTER_ENVIRONMENT, "testnet");
        // System.setProperty(AsterConfiguration.ASTER_L1_ACCOUNT, "your-l1-wallet-address");
        // System.setProperty(AsterConfiguration.ASTER_API_SECRET, "your-signer-private-key");
        // System.setProperty(AsterConfiguration.ASTER_API_WALLET, "your-api-wallet-address");
        String symbol = args.length > 0 ? args[0] : DEFAULT_SYMBOL;

        new AsterTradingExample().executeTrade(symbol);
    }
}
