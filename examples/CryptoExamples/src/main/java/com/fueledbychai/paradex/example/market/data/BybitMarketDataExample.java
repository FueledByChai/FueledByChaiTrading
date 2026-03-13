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

public class BybitMarketDataExample {

    protected static final Logger logger = LoggerFactory.getLogger(BybitMarketDataExample.class);
    protected static final String DEFAULT_SYMBOL = "BTCUSDT";
    protected static final InstrumentType DEFAULT_TYPE = InstrumentType.CRYPTO_SPOT;
    protected static final Duration RUN_DURATION = Duration.ofMinutes(1);

    public void start(String symbol, InstrumentType instrumentType) throws InterruptedException {
        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.BYBIT);
        Ticker ticker = registry.lookupByBrokerSymbol(instrumentType, symbol);
        if (ticker == null) {
            ticker = registry.lookupByCommonSymbol(instrumentType, symbol);
        }
        if (ticker == null) {
            throw new IllegalArgumentException("Unknown Bybit ticker for " + instrumentType + ": " + symbol);
        }

        QuoteEngine quoteEngine = QuoteEngine.getInstance(Exchange.BYBIT);
        quoteEngine.startEngine();

        quoteEngine.subscribeLevel1(ticker, this::onLevel1);
        quoteEngine.subscribeMarketDepth(ticker, this::onLevel2);
        quoteEngine.subscribeOrderFlow(ticker, this::onOrderFlow);

        logger.info("Subscribed to Bybit market data for {} ({})", ticker.getSymbol(), ticker.getInstrumentType());

        try {
            Thread.sleep(RUN_DURATION.toMillis());
        } finally {
            quoteEngine.stopEngine();
        }
    }

    protected void onLevel1(ILevel1Quote quote) {
        logger.info("L1 {} bid={} ask={} last={}", quote.getTicker().getSymbol(), value(quote, QuoteType.BID),
                value(quote, QuoteType.ASK), value(quote, QuoteType.LAST));
    }

    protected void onLevel2(ILevel2Quote quote) {
        logger.info("L2 {} bestBid={}x{} bestAsk={}x{}", quote.getTicker().getSymbol(),
                quote.getOrderBook().getBestBid().getPrice(), quote.getOrderBook().getBestBid().getSize(),
                quote.getOrderBook().getBestAsk().getPrice(), quote.getOrderBook().getBestAsk().getSize());
    }

    protected void onOrderFlow(OrderFlow orderFlow) {
        logger.info("Trade {} side={} price={} size={}", orderFlow.getTicker().getSymbol(), orderFlow.getSide(),
                orderFlow.getPrice(), orderFlow.getSize());
    }

    protected String value(ILevel1Quote quote, QuoteType type) {
        if (quote == null || type == null || !quote.containsType(type)) {
            return "null";
        }
        return String.valueOf(quote.getValue(type));
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("proxy.host", "localhost");
        System.setProperty("proxy.port", "1080");
        String symbol = args.length > 0 ? args[0] : DEFAULT_SYMBOL;
        InstrumentType instrumentType = args.length > 1 ? InstrumentType.valueOf(args[1].trim().toUpperCase())
                : DEFAULT_TYPE;

        new BybitMarketDataExample().start(symbol, instrumentType);
    }
}
