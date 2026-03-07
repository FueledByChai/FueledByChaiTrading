package com.fueledbychai.paradex.example.market.data;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

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

public class BybitOptionChainMarketDataExample {

    protected static final Logger logger = LoggerFactory.getLogger(BybitOptionChainMarketDataExample.class);
    protected static final String DEFAULT_UNDERLYING = "BTC";
    protected static final String DEFAULT_PREFERRED_OPTION_QUOTE = "USDC";
    protected static final Duration RUN_DURATION = Duration.ofMinutes(1);
    protected static final int CHAIN_PREVIEW_SIZE = 25;
    protected static final ITickerRegistry.OptionRightFilter DEFAULT_RIGHT_FILTER = ITickerRegistry.OptionRightFilter.ALL;
    protected static final QuoteType[] TRACKED_L1_TYPES = new QuoteType[] { QuoteType.BID, QuoteType.ASK, QuoteType.LAST,
            QuoteType.MARK_PRICE, QuoteType.BID_IV, QuoteType.ASK_IV, QuoteType.MARK_IV };

    protected final Map<String, L1Snapshot> l1Snapshots = new ConcurrentHashMap<>();

    public void start(String underlyingSymbol, String requestedOptionSymbol, ITickerRegistry.OptionRightFilter rightFilter,
            String preferredOptionQuoteCurrency)
            throws InterruptedException {
        ITickerRegistry tickerRegistry = TickerRegistryFactory.getInstance(Exchange.BYBIT);

        int expiryYear = 2026;
        int expiryMonth = 3;
        int expiryDay = 8;
        Ticker[] optionChain = tickerRegistry.getOptionChain(underlyingSymbol, expiryYear, expiryMonth, expiryDay, rightFilter);
        if (optionChain.length == 0) {
            throw new IllegalStateException("No Bybit options found for underlying " + normalize(underlyingSymbol)
                    + " and right filter " + rightFilter + ".");
        }

        long callCount = Arrays.stream(optionChain)
                .filter(t -> t != null && t.getRight() == Ticker.Right.CALL)
                .count();
        long putCount = Arrays.stream(optionChain)
                .filter(t -> t != null && t.getRight() == Ticker.Right.PUT)
                .count();

        logger.info("Bybit {} option chain loaded: total={} calls={} puts={} filter={}", normalize(underlyingSymbol),
                optionChain.length, callCount, putCount, rightFilter);
        logOptionChainPreview(optionChain);

        Ticker selectedTicker = selectTicker(optionChain, requestedOptionSymbol, preferredOptionQuoteCurrency);
        if (selectedTicker == null) {
            throw new IllegalStateException("Unable to pick an option contract from chain.");
        }

        logger.info("Subscribing to L1/L2 for {} (expiry={} strike={} right={} quote={})", selectedTicker.getSymbol(),
                formatExpiry(selectedTicker), selectedTicker.getStrike(), selectedTicker.getRight(),
                normalize(selectedTicker.getCurrency()));

        QuoteEngine quoteEngine = QuoteEngine.getInstance(Exchange.BYBIT);
        quoteEngine.startEngine();
        quoteEngine.subscribeLevel1(selectedTicker, this::onLevel1);
        quoteEngine.subscribeMarketDepth(selectedTicker, this::onLevel2);

        try {
            Thread.sleep(RUN_DURATION.toMillis());
        } finally {
            quoteEngine.stopEngine();
            l1Snapshots.clear();
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

    protected Ticker selectTicker(Ticker[] optionChain, String requestedOptionSymbol, String preferredOptionQuoteCurrency) {
        if (requestedOptionSymbol != null && !requestedOptionSymbol.isBlank()) {
            String requested = requestedOptionSymbol.trim().toUpperCase(Locale.US);
            for (Ticker ticker : optionChain) {
                if (ticker != null && requested.equalsIgnoreCase(ticker.getSymbol())) {
                    return ticker;
                }
            }
            throw new IllegalArgumentException("Requested option symbol " + requested + " was not found in chain.");
        }

        String preferredQuote = normalize(preferredOptionQuoteCurrency);
        if (preferredQuote.isBlank()) {
            preferredQuote = DEFAULT_PREFERRED_OPTION_QUOTE;
        }

        for (Ticker ticker : optionChain) {
            if (ticker != null && ticker.getRight() == Ticker.Right.CALL
                    && preferredQuote.equals(normalize(ticker.getCurrency()))) {
                return ticker;
            }
        }

        for (Ticker ticker : optionChain) {
            if (ticker != null && ticker.getRight() == Ticker.Right.CALL) {
                return ticker;
            }
        }
        return optionChain[0];
    }

    protected void onLevel1(ILevel1Quote quote) {
        if (quote == null || quote.getTicker() == null || quote.getTicker().getSymbol() == null) {
            return;
        }

        String symbol = quote.getTicker().getSymbol();
        L1Snapshot snapshot = l1Snapshots.computeIfAbsent(symbol, ignored -> new L1Snapshot());
        boolean changed = snapshot.merge(quote, TRACKED_L1_TYPES);
        if (!changed) {
            return;
        }
        logger.info("L1 quote: {}", quote);

        // logger.info("L1 {} bid={} ask={} last={} mark={} bidIv={} askIv={} markIv={} update={}", symbol,
        //         snapshot.get(QuoteType.BID), snapshot.get(QuoteType.ASK), snapshot.get(QuoteType.LAST),
        //         snapshot.get(QuoteType.MARK_PRICE), snapshot.get(QuoteType.BID_IV), snapshot.get(QuoteType.ASK_IV),
        //         snapshot.get(QuoteType.MARK_IV), updateTypes(quote));
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

        // logger.info("L2 {} bestBid={}x{} bestAsk={}x{}", quote.getTicker().getSymbol(), bestBid.getPrice(),
        //         bestBid.getSize(), bestAsk.getPrice(), bestAsk.getSize());
    }

    protected String updateTypes(ILevel1Quote quote) {
        StringJoiner joiner = new StringJoiner(",");
        for (QuoteType quoteType : TRACKED_L1_TYPES) {
            if (quote.containsType(quoteType)) {
                joiner.add(quoteType.name());
            }
        }
        if (joiner.length() == 0) {
            return "NONE";
        }
        return joiner.toString();
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

    protected static class L1Snapshot {

        protected final EnumMap<QuoteType, String> values = new EnumMap<>(QuoteType.class);

        public synchronized boolean merge(ILevel1Quote quote, QuoteType[] quoteTypes) {
            boolean changed = false;
            for (QuoteType quoteType : quoteTypes) {
                if (!quote.containsType(quoteType)) {
                    continue;
                }
                String newValue = String.valueOf(quote.getValue(quoteType));
                String previous = values.put(quoteType, newValue);
                if (!newValue.equals(previous)) {
                    changed = true;
                }
            }
            return changed;
        }

        public synchronized String get(QuoteType quoteType) {
            return values.getOrDefault(quoteType, "null");
        }
    }

    public static void main(String[] args) throws Exception {
        // System.setProperty("socksProxyHost", "127.0.0.1");
        // System.setProperty("socksProxyPort", "1080");
        System.setProperty("bybit.proxy.host", "127.0.0.1");
        System.setProperty("bybit.proxy.port", "1080");
        System.setProperty("bybit.run.proxy", "false");        
        String underlyingSymbol = args.length > 0 ? args[0] : DEFAULT_UNDERLYING;
        String requestedOptionSymbol = args.length > 1 ? args[1] : null;
        ITickerRegistry.OptionRightFilter rightFilter = args.length > 2
                ? ITickerRegistry.OptionRightFilter.valueOf(args[2].trim().toUpperCase(Locale.US))
                : DEFAULT_RIGHT_FILTER;
        String preferredOptionQuoteCurrency = args.length > 3 ? args[3] : DEFAULT_PREFERRED_OPTION_QUOTE;

        new BybitOptionChainMarketDataExample().start(underlyingSymbol, requestedOptionSymbol, rightFilter,
                preferredOptionQuoteCurrency);
    }
}
