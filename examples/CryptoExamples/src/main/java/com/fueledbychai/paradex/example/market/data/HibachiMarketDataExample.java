package com.fueledbychai.paradex.example.market.data;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

/**
 * Subscribes to Hibachi L1, L2, and order-flow streams for a single perp.
 *
 * <p>L1 is composed from two Hibachi topics — {@code ask_bid_price} (BBO) and
 * {@code mark_price}. L2 is the {@code orderbook} topic and OrderFlow is {@code trades}.
 *
 * <p>Hibachi symbols are canonical, e.g. {@code BTC/USDT-P}. The default below is
 * {@code BTC} which the Hibachi ticker registry expands to {@code BTC/USDT-P}.
 */
public class HibachiMarketDataExample {

    protected static final Logger logger = LoggerFactory.getLogger(HibachiMarketDataExample.class);
    protected static final String DEFAULT_SYMBOL = "BTC";
    protected static final InstrumentType DEFAULT_TYPE = InstrumentType.PERPETUAL_FUTURES;
    protected static final Duration RUN_DURATION = Duration.ofMinutes(1);

    public void start(String symbol, InstrumentType instrumentType) throws InterruptedException {
        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.HIBACHI);
        Ticker ticker = registry.lookupByBrokerSymbol(instrumentType, symbol);
        if (ticker == null) {
            ticker = registry.lookupByCommonSymbol(instrumentType, symbol);
        }
        if (ticker == null) {
            throw new IllegalArgumentException("Unknown Hibachi ticker for " + instrumentType + ": " + symbol);
        }

        QuoteEngine quoteEngine = QuoteEngine.getInstance(Exchange.HIBACHI);
        quoteEngine.startEngine();

        quoteEngine.subscribeLevel1(ticker, this::onLevel1);
        quoteEngine.subscribeMarketDepth(ticker, this::onLevel2);
        quoteEngine.subscribeOrderFlow(ticker, this::onOrderFlow);

        logger.info("Subscribed to Hibachi market data for {} ({})", ticker.getSymbol(), ticker.getInstrumentType());

        try {
            Thread.sleep(RUN_DURATION.toMillis());
        } finally {
            quoteEngine.stopEngine();
        }
    }

    protected void onLevel1(ILevel1Quote quote) {
        logger.info("L1 {} bid={} bidSize={} ask={} askSize={} mark={}",
                quote.getTicker().getSymbol(),
                value(quote, QuoteType.BID),
                value(quote, QuoteType.BID_SIZE),
                value(quote, QuoteType.ASK),
                value(quote, QuoteType.ASK_SIZE),
                value(quote, QuoteType.MARK_PRICE));
    }

    protected void onLevel2(ILevel2Quote quote) {
        logger.info("L2 {} bestBid={}x{} bestAsk={}x{}",
                quote.getTicker().getSymbol(),
                quote.getOrderBook().getBestBid().getPrice(),
                quote.getOrderBook().getBestBid().getSize(),
                quote.getOrderBook().getBestAsk().getPrice(),
                quote.getOrderBook().getBestAsk().getSize());
    }

    protected void onOrderFlow(OrderFlow orderFlow) {
        logger.info("Trade {} side={} price={} size={}",
                orderFlow.getTicker().getSymbol(), orderFlow.getSide(),
                orderFlow.getPrice(), orderFlow.getSize());
    }

    protected String value(ILevel1Quote quote, QuoteType type) {
        if (quote == null || type == null || !quote.containsType(type)) {
            return "null";
        }
        return String.valueOf(quote.getValue(type));
    }

    public static void main(String[] args) throws Exception {
        String symbol = args.length > 0 ? args[0] : DEFAULT_SYMBOL;
        InstrumentType instrumentType = args.length > 1
                ? InstrumentType.valueOf(args[1].trim().toUpperCase())
                : DEFAULT_TYPE;

        new HibachiMarketDataExample().start(symbol, instrumentType);
    }
}
