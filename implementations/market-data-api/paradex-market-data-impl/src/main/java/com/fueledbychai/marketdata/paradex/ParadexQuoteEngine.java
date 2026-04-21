package com.fueledbychai.marketdata.paradex;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.IOrderBook;
import com.fueledbychai.marketdata.Level1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2Quote;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderBookUpdateListener;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

import com.google.gson.JsonObject;

public class ParadexQuoteEngine extends QuoteEngine
        implements OrderBookUpdateListener, TradesUpdateListener, MarketsSummaryUpdateListener {

    protected static final Logger logger = LoggerFactory.getLogger(ParadexQuoteEngine.class);
    protected Map<Ticker, TradesWebSocketClient> tradesClients = new HashMap<>();
    protected Map<Ticker, MarketsSummaryWebSocketClient> marketsSummaryClients = new HashMap<>();

    protected boolean started = false;
    protected final IParadexRestApi restApi;
    protected ITickerRegistry tickerRegistry;

    public ParadexQuoteEngine() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.PARADEX, IParadexRestApi.class),
                TickerRegistryFactory.getInstance(Exchange.PARADEX));
    }

    protected ParadexQuoteEngine(IParadexRestApi restApi, ITickerRegistry tickerRegistry) {
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        if (tickerRegistry == null) {
            throw new IllegalArgumentException("tickerRegistry is required");
        }
        this.restApi = restApi;
        this.tickerRegistry = tickerRegistry;
    }

    @Override
    public String getDataProviderName() {
        return "Paradex";
    }

    @Override
    public Date getServerTime() {
        return null;
    }

    @Override
    public boolean isConnected() {
        return started;
    }

    @Override
    public void startEngine() {
        started = true;

    }

    @Override
    public void startEngine(Properties props) {
        started = true;

    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public void stopEngine() {
        started = false;

    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        // Not implemented for Paradex

    }

    @Override
    public ILevel1Quote requestLevel1Snapshot(Ticker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
        Ticker canonical = canonicalize(ticker);
        JsonObject response = restApi.getBBO(canonical.getSymbol());
        if (response == null) {
            throw new IllegalStateException("No BBO data returned for " + canonical.getSymbol());
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        Level1Quote quote = new Level1Quote(canonical, now);
        BigDecimal bidPrice = getBigDecimalField(response, "bid");
        BigDecimal bidSize = getBigDecimalField(response, "bid_size");
        BigDecimal askPrice = getBigDecimalField(response, "ask");
        BigDecimal askSize = getBigDecimalField(response, "ask_size");
        if (bidPrice != null) {
            quote.addQuote(QuoteType.BID, canonical.formatPrice(bidPrice));
        }
        if (bidSize != null) {
            quote.addQuote(QuoteType.BID_SIZE, bidSize);
        }
        if (askPrice != null) {
            quote.addQuote(QuoteType.ASK, canonical.formatPrice(askPrice));
        }
        if (askSize != null) {
            quote.addQuote(QuoteType.ASK_SIZE, askSize);
        }
        return quote;
    }

    /**
     * Returns the canonical {@link Ticker} for the given input by resolving it
     * through the ticker registry. If the input ticker's symbol already
     * matches a registered Paradex exchange symbol it is returned as-is;
     * otherwise the registry is consulted (first by broker symbol, then by
     * common symbol) to find the canonical Ticker. This protects callers that
     * hand the engine tickers built from user-friendly forms — e.g. an option
     * carrying {@code BTC/USD/24APR26/75000/C} or {@code BTC/USD-20260424-75000-C}
     * instead of Paradex's {@code BTC-USD-24APR26-75000-C} — by returning the
     * registry-cached canonical Ticker so all downstream calls (REST URLs,
     * WebSocket channel names, OrderBookRegistry keys) use a consistent symbol.
     * If lookup fails the original ticker is returned unchanged.
     */
    protected Ticker canonicalize(Ticker ticker) {
        if (ticker == null) {
            return null;
        }
        String symbol = ticker.getSymbol();
        if (symbol == null) {
            return ticker;
        }
        InstrumentType instrumentType = ticker.getInstrumentType();
        if (instrumentType == null) {
            return ticker;
        }
        // Fast path: a symbol that already matches a registered broker symbol
        // (no slash in it, in practice) maps directly to its cached Ticker.
        Ticker direct = tickerRegistry.lookupByBrokerSymbol(instrumentType, symbol);
        if (direct != null) {
            return direct;
        }
        // Otherwise treat the input as a common-symbol form and let the
        // registry's commonSymbolToExchangeSymbol logic translate it.
        Ticker viaCommon = tickerRegistry.lookupByCommonSymbol(instrumentType, symbol);
        if (viaCommon != null) {
            return viaCommon;
        }
        return ticker;
    }

    protected BigDecimal getBigDecimalField(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return new BigDecimal(object.get(key).getAsString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        Ticker canonical = canonicalize(ticker);
        logger.debug("Subscribing to market depth for ticker: {} Listener: {}", canonical.getSymbol(), listener);
        super.subscribeMarketDepth(canonical, listener);
        OrderBookRegistry.getInstance().getOrderBook(canonical).addOrderBookUpdateListener(this);
    }

    @Override
    public void unsubscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        super.unsubscribeMarketDepth(canonicalize(ticker), listener);
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        Ticker canonical = canonicalize(ticker);
        super.subscribeLevel1(canonical, listener);
        OrderBookRegistry.getInstance().getOrderBook(canonical).addOrderBookUpdateListener(this);
        MarketsSummaryWebSocketClient marketsSummaryClient = marketsSummaryClients.get(canonical);
        if (marketsSummaryClient == null) {
            marketsSummaryClient = new MarketsSummaryWebSocketClient();
            marketsSummaryClients.put(canonical, marketsSummaryClient);
            marketsSummaryClient.startMarketsSummaryWSClient(canonical, this);
        }

    }

    @Override
    public void unsubscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        super.unsubscribeLevel1(canonicalize(ticker), listener);
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        Ticker canonical = canonicalize(ticker);
        super.subscribeOrderFlow(canonical, listener);
        TradesWebSocketClient tradesClient = tradesClients.get(canonical);
        if (tradesClient == null) {
            tradesClient = new TradesWebSocketClient();
            tradesClients.put(canonical, tradesClient);
            tradesClient.startTradesWSClient(canonical, this);
        }

    }

    @Override
    public void unsubscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        super.unsubscribeOrderFlow(canonicalize(ticker), listener);
    }

    @Override
    public void bestBidUpdated(Ticker ticker, BigDecimal bestBid, Double bidSize, ZonedDateTime timestamp) {
        Level1Quote quote = new Level1Quote(ticker, timestamp);
        quote.addQuote(QuoteType.BID, ticker.formatPrice(bestBid));
        quote.addQuote(QuoteType.BID_SIZE, BigDecimal.valueOf(bidSize));
        super.fireLevel1Quote(quote);

    }

    @Override
    public void bestAskUpdated(Ticker ticker, BigDecimal bestAsk, Double askSize, ZonedDateTime timestamp) {
        Level1Quote quote = new Level1Quote(ticker, timestamp);
        quote.addQuote(QuoteType.ASK, ticker.formatPrice(bestAsk));
        quote.addQuote(QuoteType.ASK_SIZE, BigDecimal.valueOf(askSize));
        super.fireLevel1Quote(quote);

    }

    @Override
    public void orderBookImbalanceUpdated(Ticker ticker, BigDecimal imbalance, ZonedDateTime timestamp) {
        // Level2Quote quote = new Level2Quote(ticker, timestamp);
        // quote.addQuote(QuoteType.ORDER_BOOK_IMBALANCE, imbalance);
        // super.fireMarketDepthQuote(quote);
    }

    @Override
    public void orderBookUpdated(Ticker ticker, IOrderBook book, ZonedDateTime timestamp) {
        logger.debug("Order book updated for {}: bestBid: {}, bestAsk: {}", ticker, book.getBestBid().price,
                book.getBestAsk().price);
        Level2Quote quote = new Level2Quote(ticker, book, timestamp);
        super.fireMarketDepthQuote(quote);
    }

    @Override
    public void newTrade(long createdAtTimestamp, String market, String price, String side, String size) {

        Ticker ticker = resolveTicker(market);
        if (ticker == null) {
            logger.warn("Skipping trade update for unknown symbol '{}'", market);
            return;
        }

        BigDecimal sizeDecimal = parseBigDecimal(size);
        if (sizeDecimal == null) {
            logger.debug("Skipping trade update for '{}' with missing/blank size", market);
            return;
        }

        // Format the price according to the ticker's minimum tick size precision
        BigDecimal formattedPrice = ticker.formatPrice(parseBigDecimal(price));
        if (formattedPrice == null) {
            logger.debug("Skipping trade update for '{}' with missing/blank price", market);
            return;
        }

        OrderFlow orderFlow = new OrderFlow(ticker, formattedPrice, sizeDecimal, OrderFlow.Side.valueOf(side),
                convertToZonedDateTime(createdAtTimestamp));
        super.fireOrderFlow(orderFlow);
    }

    @Override
    public void newSummaryUpdate(long createdAtTimestamp, String symbol, String bid, String ask, String lastPrice,
            String markPrice, String openInterest, String volume24h, String underlyingPrice, String fundingRate) {

        Ticker ticker = resolveTicker(symbol);
        if (ticker == null) {
            logger.warn("Skipping market summary update for unknown symbol '{}'", symbol);
            return;
        }

        // Paradex sends empty strings for fields that have no value yet
        // (common for newly-listed options without trading activity). Parse
        // each field defensively and only populate the corresponding quote
        // entry when we actually have a number.
        BigDecimal lastPriceDecimal = ticker.formatPrice(parseBigDecimal(lastPrice));
        BigDecimal markPriceDecimal = ticker.formatPrice(parseBigDecimal(markPrice));
        BigDecimal openInterestDecimal = parseBigDecimal(openInterest);
        BigDecimal volume24hDecimal = parseBigDecimal(volume24h);
        BigDecimal underlyingPriceDecimal = ticker.formatPrice(parseBigDecimal(underlyingPrice));

        // The bid and the ask from this market summary websocket aren't reliably
        // updated, better to use the top of the order book.
        Map<QuoteType, BigDecimal> quoteValues = new HashMap<>();
        putIfNotNull(quoteValues, QuoteType.LAST, lastPriceDecimal);
        putIfNotNull(quoteValues, QuoteType.MARK_PRICE, markPriceDecimal);
        putIfNotNull(quoteValues, QuoteType.OPEN_INTEREST, openInterestDecimal);
        putIfNotNull(quoteValues, QuoteType.UNDERLYING_PRICE, underlyingPriceDecimal);
        // Paradex reports volume_24h in USD (quote currency), not base units.
        putIfNotNull(quoteValues, QuoteType.VOLUME_NOTIONAL, volume24hDecimal);
        if (lastPriceDecimal != null && lastPriceDecimal.signum() != 0 && volume24hDecimal != null) {
            quoteValues.put(QuoteType.VOLUME, volume24hDecimal.divide(lastPriceDecimal, MathContext.DECIMAL64));
        }
        if (lastPriceDecimal != null && openInterestDecimal != null) {
            quoteValues.put(QuoteType.OPEN_INTEREST_NOTIONAL, lastPriceDecimal.multiply(openInterestDecimal));
        }
        addFundingRateQuotes(ticker, fundingRate, quoteValues);

        ILevel1Quote quote = new Level1Quote(ticker, convertToZonedDateTime(createdAtTimestamp), quoteValues);
        super.fireLevel1Quote(quote);
    }

    /**
     * Parses a possibly blank/null string into a {@link BigDecimal}, returning
     * {@code null} for blank inputs and unparseable values. Paradex's WebSocket
     * feeds send empty strings for fields with no value yet (common for
     * newly-listed options without activity), so callers must guard against
     * these instead of letting them throw {@link NumberFormatException}.
     */
    protected BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            logger.debug("Could not parse BigDecimal from value '{}'", value);
            return null;
        }
    }

    private static void putIfNotNull(Map<QuoteType, BigDecimal> map, QuoteType key, BigDecimal value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    protected Ticker resolveTicker(String symbol) {
        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (ticker == null) {
            ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.CRYPTO_SPOT, symbol);
        }
        if (ticker == null) {
            ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.OPTION, symbol);
        }
        return ticker;
    }

    protected void addFundingRateQuotes(Ticker ticker, String fundingRateString, Map<QuoteType, BigDecimal> quoteValues) {
        if (fundingRateString == null || fundingRateString.isBlank()) {
            return;
        }

        int fundingInterval = ticker.getFundingRateInterval();
        if (fundingInterval <= 0) {
            logger.debug("Skipping funding rate for symbol '{}' due to non-positive funding interval {}",
                    ticker.getSymbol(), fundingInterval);
            return;
        }

        try {
            double hourlyFundingRate = Double.parseDouble(fundingRateString) / (double) fundingInterval;
            double annualizedFundingRate = hourlyFundingRate * 24 * 365 * 100;
            double fundingRateHourlyBps = hourlyFundingRate * 10000;
            quoteValues.put(QuoteType.FUNDING_RATE_APR, BigDecimal.valueOf(annualizedFundingRate));
            quoteValues.put(QuoteType.FUNDING_RATE_HOURLY_BPS, BigDecimal.valueOf(fundingRateHourlyBps));
        } catch (NumberFormatException e) {
            logger.warn("Skipping malformed funding_rate '{}' for symbol '{}'", fundingRateString, ticker.getSymbol());
        }
    }

    protected ZonedDateTime convertToZonedDateTime(long timestamp) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"));
    }

}
