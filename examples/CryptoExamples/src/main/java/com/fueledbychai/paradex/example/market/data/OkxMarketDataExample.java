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

public class OkxMarketDataExample {

    protected static final Logger logger = LoggerFactory.getLogger(OkxMarketDataExample.class);
    protected static final String DEFAULT_SYMBOL = "BTC-USDT";
    protected static final InstrumentType DEFAULT_TYPE = InstrumentType.PERPETUAL_FUTURES;
    protected static final Duration RUN_DURATION = Duration.ofMinutes(1);

    public void start(String symbol, InstrumentType instrumentType) throws InterruptedException {
        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.OKX);
        Ticker ticker = registry.lookupByBrokerSymbol(instrumentType, symbol);
        if (ticker == null) {
            ticker = registry.lookupByCommonSymbol(instrumentType, symbol);
        }
        if (ticker == null) {
            throw new IllegalArgumentException("Unknown OKX ticker for " + instrumentType + ": " + symbol);
        }

        QuoteEngine quoteEngine = QuoteEngine.getInstance(Exchange.OKX);
        quoteEngine.startEngine();

        quoteEngine.subscribeLevel1(ticker, this::onLevel1);
        quoteEngine.subscribeMarketDepth(ticker, this::onLevel2);
        quoteEngine.subscribeOrderFlow(ticker, this::onOrderFlow);

        logger.info("Subscribed to OKX market data for {} ({})", ticker.getSymbol(), ticker.getInstrumentType());
        if (ticker.getInstrumentType() != InstrumentType.PERPETUAL_FUTURES) {
            logger.warn(
                    "Funding, mark price, and open interest are expected to be null for {}. Use BTC-USDT-SWAP with {} if you want perpetual funding data.",
                    ticker.getInstrumentType(), InstrumentType.PERPETUAL_FUTURES);
        }

        try {
            Thread.sleep(RUN_DURATION.toMillis());
        } finally {
            quoteEngine.stopEngine();
        }
    }

    protected void onLevel1(ILevel1Quote quote) {
        logger.info("L1 {} bid={} ask={} last={} mark={} oi={} vol={} fundingHourlyBps={} fundingApr={}",
                quote.getTicker().getSymbol(), value(quote, QuoteType.BID), value(quote, QuoteType.ASK),
                value(quote, QuoteType.LAST), value(quote, QuoteType.MARK_PRICE), value(quote, QuoteType.OPEN_INTEREST),
                value(quote, QuoteType.VOLUME), value(quote, QuoteType.FUNDING_RATE_HOURLY_BPS),
                value(quote, QuoteType.FUNDING_RATE_APR));
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
        String symbol = args.length > 0 ? args[0] : DEFAULT_SYMBOL;
        InstrumentType instrumentType = args.length > 1 ? InstrumentType.valueOf(args[1].trim().toUpperCase())
                : DEFAULT_TYPE;

        new OkxMarketDataExample().start(symbol, instrumentType);
    }
}
