package com.fueledbychai.paradex.example.market.data;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.IOrderBook;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class OkxOptionChainMarketDataExample {

    protected static final Logger logger = LoggerFactory.getLogger(OkxOptionChainMarketDataExample.class);
    protected static final String DEFAULT_UNDERLYING = "BTC";
    protected static final Duration RUN_DURATION = Duration.ofMinutes(1);
    protected static final int CHAIN_PREVIEW_SIZE = 25;
    protected static final ITickerRegistry.OptionRightFilter DEFAULT_RIGHT_FILTER = ITickerRegistry.OptionRightFilter.ALL;

    public void start(String underlyingSymbol, String requestedOptionSymbol, ITickerRegistry.OptionRightFilter rightFilter)
            throws InterruptedException {
        ITickerRegistry tickerRegistry = TickerRegistryFactory.getInstance(Exchange.OKX);

        Ticker[] optionChain = tickerRegistry.getOptionChain(underlyingSymbol, 0, 0, 0, rightFilter);
        if (optionChain.length == 0) {
            throw new IllegalStateException("No OKX options found for underlying " + normalize(underlyingSymbol)
                    + " and right filter " + rightFilter + ".");
        }

        long callCount = Arrays.stream(optionChain)
                .filter(t -> t != null && t.getRight() == Ticker.Right.CALL)
                .count();
        long putCount = Arrays.stream(optionChain)
                .filter(t -> t != null && t.getRight() == Ticker.Right.PUT)
                .count();

        logger.info("OKX {} option chain loaded: total={} calls={} puts={} filter={}", normalize(underlyingSymbol),
                optionChain.length, callCount, putCount, rightFilter);
        logOptionChainPreview(optionChain);

        Ticker selectedTicker = selectTicker(optionChain, requestedOptionSymbol);
        if (selectedTicker == null) {
            throw new IllegalStateException("Unable to pick an option contract from chain.");
        }

        logger.info("Subscribing to L1/L2 for {} (expiry={} strike={} right={})", selectedTicker.getSymbol(),
                formatExpiry(selectedTicker), selectedTicker.getStrike(), selectedTicker.getRight());

        QuoteEngine quoteEngine = QuoteEngine.getInstance(Exchange.OKX);
        quoteEngine.startEngine();
        quoteEngine.subscribeLevel1(selectedTicker, this::onLevel1);
        quoteEngine.subscribeMarketDepth(selectedTicker, this::onLevel2);

        try {
            Thread.sleep(RUN_DURATION.toMillis());
        } finally {
            quoteEngine.stopEngine();
        }
    }

    protected void logOptionChainPreview(Ticker[] optionChain) {
        int previewSize = Math.min(CHAIN_PREVIEW_SIZE, optionChain.length);
        for (int i = 0; i < previewSize; i++) {
            Ticker ticker = optionChain[i];
            if (ticker == null) {
                continue;
            }
            logger.info("Chain[{}] {} expiry={} strike={} right={}", i, ticker.getSymbol(), formatExpiry(ticker),
                    ticker.getStrike(), ticker.getRight());
        }
        if (optionChain.length > previewSize) {
            logger.info("... {} more contracts omitted", optionChain.length - previewSize);
        }
    }

    protected Ticker selectTicker(Ticker[] optionChain, String requestedOptionSymbol) {
        if (requestedOptionSymbol != null && !requestedOptionSymbol.isBlank()) {
            String requested = requestedOptionSymbol.trim().toUpperCase(Locale.US);
            for (Ticker ticker : optionChain) {
                if (ticker != null && requested.equalsIgnoreCase(ticker.getSymbol())) {
                    return ticker;
                }
            }
            throw new IllegalArgumentException("Requested option symbol " + requested + " was not found in chain.");
        }

        for (Ticker ticker : optionChain) {
            if (ticker != null && ticker.getRight() == Ticker.Right.CALL) {
                return ticker;
            }
        }
        return optionChain[0];
    }

    protected void onLevel1(ILevel1Quote quote) {
        logger.info("L1 {} bid={} ask={} last={} mark={} bidIv={} askIv={} markIv={}", quote.getTicker().getSymbol(),
                value(quote, QuoteType.BID), value(quote, QuoteType.ASK), value(quote, QuoteType.LAST),
                value(quote, QuoteType.MARK_PRICE), value(quote, QuoteType.BID_IV), value(quote, QuoteType.ASK_IV),
                value(quote, QuoteType.MARK_IV));
    }

    protected void onLevel2(ILevel2Quote quote) {
        IOrderBook orderBook = quote.getOrderBook();
        if (orderBook == null) {
            logger.info("L2 {} order book unavailable", quote.getTicker().getSymbol());
            return;
        }

        IOrderBook.BidSizePair bestBid = orderBook.getBestBid();
        IOrderBook.BidSizePair bestAsk = orderBook.getBestAsk();
        if (bestBid == null || bestAsk == null) {
            logger.info("L2 {} waiting for best bid/ask", quote.getTicker().getSymbol());
            return;
        }

        logger.info("L2 {} bestBid={}x{} bestAsk={}x{}", quote.getTicker().getSymbol(), bestBid.getPrice(),
                bestBid.getSize(), bestAsk.getPrice(), bestAsk.getSize());
    }

    protected String value(ILevel1Quote quote, QuoteType type) {
        if (quote == null || type == null || !quote.containsType(type)) {
            return "null";
        }
        return String.valueOf(quote.getValue(type));
    }

    protected String formatExpiry(Ticker ticker) {
        if (ticker == null || ticker.getExpiryYear() <= 0 || ticker.getExpiryMonth() <= 0 || ticker.getExpiryDay() <= 0) {
            return "UNKNOWN";
        }
        return LocalDate.of(ticker.getExpiryYear(), ticker.getExpiryMonth(), ticker.getExpiryDay()).toString();
    }

    protected String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.US);
    }

    public static void main(String[] args) throws Exception {
        String underlyingSymbol = args.length > 0 ? args[0] : DEFAULT_UNDERLYING;
        String requestedOptionSymbol = args.length > 1 ? args[1] : null;
        ITickerRegistry.OptionRightFilter rightFilter = args.length > 2
                ? ITickerRegistry.OptionRightFilter.valueOf(args[2].trim().toUpperCase(Locale.US))
                : DEFAULT_RIGHT_FILTER;

        new OkxOptionChainMarketDataExample().start(underlyingSymbol, requestedOptionSymbol, rightFilter);
    }
}
