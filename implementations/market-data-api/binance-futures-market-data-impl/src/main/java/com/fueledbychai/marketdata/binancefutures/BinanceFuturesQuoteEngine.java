package com.fueledbychai.marketdata.binancefutures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.binancefutures.common.api.BinanceFuturesConfiguration;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesRestApi;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesWebSocketApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.Level1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2Quote;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderBook;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class BinanceFuturesQuoteEngine extends QuoteEngine
        implements com.fueledbychai.marketdata.RawOrderBookSubscribable {

    private static final Logger logger = LoggerFactory.getLogger(BinanceFuturesQuoteEngine.class);
    private static final BigDecimal BPS_MULTIPLIER = new BigDecimal("10000");
    private static final BigDecimal APR_MULTIPLIER = new BigDecimal("876000");
    private static final int DEFAULT_DEPTH = 20;
    private static final int DEFAULT_FUNDING_INTERVAL_HOURS = 8;

    protected volatile boolean started = false;
    protected final IBinanceFuturesRestApi restApi;
    protected final IBinanceFuturesWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;
    protected final Set<Ticker> level1StreamsStarted = ConcurrentHashMap.newKeySet();
    protected final Set<Ticker> depthStreamsStarted = ConcurrentHashMap.newKeySet();
    protected final Set<Ticker> tradeStreamsStarted = ConcurrentHashMap.newKeySet();

    public BinanceFuturesQuoteEngine() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.BINANCE_FUTURES, IBinanceFuturesRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.BINANCE_FUTURES, IBinanceFuturesWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.BINANCE_FUTURES));
        BinanceFuturesConfiguration config = BinanceFuturesConfiguration.getInstance();
        logger.info("Binance Futures WebSocket URLs: futures={}, options={}", config.getWebSocketUrl(),
                config.getOptionsWebSocketUrl());
    }

    protected BinanceFuturesQuoteEngine(IBinanceFuturesRestApi restApi, IBinanceFuturesWebSocketApi webSocketApi,
            ITickerRegistry tickerRegistry) {
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        if (webSocketApi == null) {
            throw new IllegalArgumentException("webSocketApi is required");
        }
        if (tickerRegistry == null) {
            throw new IllegalArgumentException("tickerRegistry is required");
        }
        this.restApi = restApi;
        this.webSocketApi = webSocketApi;
        this.tickerRegistry = tickerRegistry;
    }

    @Override
    public String getDataProviderName() {
        return "BinanceFutures";
    }

    @Override
    public Date getServerTime() {
        return restApi.getServerTime();
    }

    @Override
    public boolean isConnected() {
        return started;
    }

    @Override
    public void startEngine() {
        started = true;
        webSocketApi.connect();
    }

    @Override
    public void startEngine(Properties props) {
        startEngine();
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public void stopEngine() {
        started = false;
        level1StreamsStarted.clear();
        depthStreamsStarted.clear();
        tradeStreamsStarted.clear();
        rawBookListenerMap.clear();
        rawDepthStreamsStarted.clear();
        clearSessionListeners();
        webSocketApi.disconnectAll();
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        requireTicker(ticker);
        boolean startStreams = level1StreamsStarted.add(ticker);
        super.subscribeLevel1(ticker, listener);
        if (startStreams) {
            startBookTickerStream(ticker);
            startSymbolTickerStream(ticker);
            if (!isOptionTicker(ticker)) {
                startMarkPriceStream(ticker);
            }
        }
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        requireTicker(ticker);
        boolean startStream = depthStreamsStarted.add(ticker);
        super.subscribeMarketDepth(ticker, listener);
        if (startStream) {
            startDepthStream(ticker);
        }
    }

    // Raw full-depth diff tap for the data recorder (the @depth incremental stream + a REST
    // snapshot anchor) — independent of the algo's @depth20 partial-snapshot path above.
    protected final java.util.Map<Ticker, List<com.fueledbychai.marketdata.RawOrderBookEventListener>>
            rawBookListenerMap = new ConcurrentHashMap<>();
    protected final Set<Ticker> rawDepthStreamsStarted = ConcurrentHashMap.newKeySet();

    /**
     * Register a raw full-depth listener (the recorder) and, on first call per ticker, start the
     * {@code @depth} diff stream and fetch a REST depth snapshot to anchor the absolute book. The
     * stream is started <i>before</i> the snapshot so no deltas are missed; deltas that arrive
     * before the anchor are recorded but flagged out-of-epoch by the downstream sequencer.
     */
    public void subscribeRawOrderBook(Ticker ticker,
            com.fueledbychai.marketdata.RawOrderBookEventListener listener) {
        requireTicker(ticker);
        List<com.fueledbychai.marketdata.RawOrderBookEventListener> ls =
                rawBookListenerMap.computeIfAbsent(ticker, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        if (!ls.contains(listener)) {
            ls.add(listener);
        }
        if (rawDepthStreamsStarted.add(ticker)) {
            webSocketApi.subscribeDiffDepth(ticker,
                    message -> safeRun(() -> onRawDiffDepth(ticker, message), "diffDepth", ticker));
            try {
                JsonNode snap = restApi.getDepthSnapshot(ticker.getSymbol(), 1000);
                fireRawAnchor(ticker, snap);
            } catch (RuntimeException e) {
                logger.warn("Failed to fetch raw depth snapshot anchor for {}: {}", ticker.getSymbol(),
                        e.getMessage());
            }
        }
    }

    /** Fire a full-book snapshot anchor (from the REST {@code /depth} response) to raw listeners. */
    protected void fireRawAnchor(Ticker ticker, JsonNode snapshot) {
        if (snapshot == null) {
            return;
        }
        long lastUpdateId = snapshot.path("lastUpdateId").asLong(com.fueledbychai.marketdata.RawBookUpdate.NO_SEQUENCE);
        List<com.fueledbychai.marketdata.RawBookUpdate.Entry> entries = new ArrayList<>();
        addRawEntries(entries, snapshot.path("bids"), com.fueledbychai.marketdata.RawBookUpdate.Side.BUY, true);
        addRawEntries(entries, snapshot.path("asks"), com.fueledbychai.marketdata.RawBookUpdate.Side.SELL, true);
        fireRaw(ticker, new com.fueledbychai.marketdata.RawBookUpdate(true, lastUpdateId,
                com.fueledbychai.marketdata.RawBookUpdate.NO_SEQUENCE, ZonedDateTime.now(ZoneId.of("UTC")),
                entries, null));
    }

    /**
     * Map a {@code @depth} diff frame ({@code U}/{@code u}/{@code pu}, {@code b}/{@code a}; qty 0 =
     * remove) to a sequenced {@link com.fueledbychai.marketdata.RawBookUpdate} and deliver it.
     */
    protected void onRawDiffDepth(Ticker ticker, JsonNode message) {
        List<com.fueledbychai.marketdata.RawOrderBookEventListener> listeners = rawBookListenerMap.get(ticker);
        if (listeners == null || listeners.isEmpty() || message == null) {
            return;
        }
        long u = message.path("u").asLong(com.fueledbychai.marketdata.RawBookUpdate.NO_SEQUENCE);
        long pu = message.path("pu").asLong(com.fueledbychai.marketdata.RawBookUpdate.NO_SEQUENCE);
        List<com.fueledbychai.marketdata.RawBookUpdate.Entry> entries = new ArrayList<>();
        addRawEntries(entries, message.path("b"), com.fueledbychai.marketdata.RawBookUpdate.Side.BUY, false);
        addRawEntries(entries, message.path("a"), com.fueledbychai.marketdata.RawBookUpdate.Side.SELL, false);
        fireRaw(ticker, new com.fueledbychai.marketdata.RawBookUpdate(false, u, pu,
                toTimestamp(message, "E"), entries, null));
    }

    private void fireRaw(Ticker ticker, com.fueledbychai.marketdata.RawBookUpdate update) {
        List<com.fueledbychai.marketdata.RawOrderBookEventListener> listeners = rawBookListenerMap.get(ticker);
        if (listeners == null) {
            return;
        }
        for (com.fueledbychai.marketdata.RawOrderBookEventListener listener : listeners) {
            try {
                listener.onBookUpdate(ticker, update);
            } catch (RuntimeException e) {
                logger.warn("Raw book listener failed for {}: {}", ticker.getSymbol(), e.getMessage());
            }
        }
    }

    /** Map Binance {@code [price, qty]} level arrays to raw entries; qty 0 = delete. */
    private static void addRawEntries(List<com.fueledbychai.marketdata.RawBookUpdate.Entry> out,
            JsonNode levels, com.fueledbychai.marketdata.RawBookUpdate.Side side, boolean isSnapshot) {
        if (levels == null || !levels.isArray()) {
            return;
        }
        for (JsonNode lvl : levels) {
            if (lvl == null || !lvl.isArray() || lvl.size() < 2) {
                continue;
            }
            String priceStr = lvl.get(0).asText("");
            String qtyStr = lvl.get(1).asText("");
            if (priceStr.isBlank() || qtyStr.isBlank()) {
                continue;
            }
            double qty = Double.parseDouble(qtyStr);
            com.fueledbychai.marketdata.RawBookUpdate.Action action;
            if (isSnapshot) {
                action = com.fueledbychai.marketdata.RawBookUpdate.Action.INSERT;
            } else if (qty <= 0.0) {
                action = com.fueledbychai.marketdata.RawBookUpdate.Action.DELETE;
            } else {
                action = com.fueledbychai.marketdata.RawBookUpdate.Action.UPDATE;
            }
            out.add(new com.fueledbychai.marketdata.RawBookUpdate.Entry(side, action, new BigDecimal(priceStr), qty));
        }
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        requireTicker(ticker);
        boolean startStream = tradeStreamsStarted.add(ticker);
        super.subscribeOrderFlow(ticker, listener);
        if (startStream) {
            startTradeStream(ticker);
        }
    }

    public ILevel1Quote requestLevel1Snapshot(Ticker ticker) {
        requireTicker(ticker);
        String symbol = ticker.getSymbol();
        JsonNode response = restApi.getBookTicker(symbol);
        if (response == null || response.isNull() || response.isMissingNode()) {
            throw new IllegalStateException("No book ticker data returned for " + symbol);
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        Level1Quote quote = new Level1Quote(ticker, now);
        BigDecimal bidPrice = decimalValue(response, "bidPrice");
        BigDecimal bidQty = decimalValue(response, "bidQty");
        BigDecimal askPrice = decimalValue(response, "askPrice");
        BigDecimal askQty = decimalValue(response, "askQty");
        if (bidPrice != null) quote.addQuote(QuoteType.BID, bidPrice);
        if (bidQty != null) quote.addQuote(QuoteType.BID_SIZE, bidQty);
        if (askPrice != null) quote.addQuote(QuoteType.ASK, askPrice);
        if (askQty != null) quote.addQuote(QuoteType.ASK_SIZE, askQty);
        return quote;
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.error("useDelayedData() is not supported for Binance futures market data");
    }

    void onBookTickerUpdate(Ticker ticker, JsonNode message) {
        BigDecimal bestBid = decimalValue(message, "b");
        BigDecimal bidSize = decimalValue(message, "B");
        BigDecimal bestAsk = decimalValue(message, "a");
        BigDecimal askSize = decimalValue(message, "A");
        if (bestBid == null && bestAsk == null) {
            return;
        }

        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "E"));
        if (bestBid != null) {
            quote.addQuote(QuoteType.BID, bestBid);
        }
        if (bidSize != null) {
            quote.addQuote(QuoteType.BID_SIZE, bidSize);
        }
        if (bestAsk != null) {
            quote.addQuote(QuoteType.ASK, bestAsk);
        }
        if (askSize != null) {
            quote.addQuote(QuoteType.ASK_SIZE, askSize);
        }
        fireLevel1Quote(quote);
    }

    void onMarkPriceUpdate(Ticker ticker, JsonNode message) {
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "E"));
        boolean hasValues = false;

        BigDecimal markPrice = decimalValue(message, "p");
        if (markPrice != null) {
            quote.addQuote(QuoteType.MARK_PRICE, markPrice);
            hasValues = true;
        }

        BigDecimal underlyingPrice = decimalValue(message, "i");
        if (underlyingPrice != null) {
            quote.addQuote(QuoteType.UNDERLYING_PRICE, underlyingPrice);
            hasValues = true;
        }

        BigDecimal fundingRate = decimalValue(message, "r");
        if (fundingRate != null) {
            BigDecimal hourlyFundingRate = toHourlyFundingRate(ticker, fundingRate);
            quote.addQuote(QuoteType.FUNDING_RATE_HOURLY_BPS, hourlyFundingRate.multiply(BPS_MULTIPLIER));
            quote.addQuote(QuoteType.FUNDING_RATE_APR, hourlyFundingRate.multiply(APR_MULTIPLIER));
            hasValues = true;
        }

        if (hasValues) {
            fireLevel1Quote(quote);
        }
    }

    void onSymbolTickerUpdate(Ticker ticker, JsonNode message) {
        if (isOptionTicker(ticker)) {
            onOptionTickerUpdate(ticker, message);
            return;
        }

        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "E"));
        boolean hasValues = false;

        BigDecimal lastPrice = decimalValue(message, "c");
        if (lastPrice != null) {
            quote.addQuote(QuoteType.LAST, lastPrice);
            hasValues = true;
        }

        BigDecimal lastSize = decimalValue(message, "Q");
        if (lastSize != null) {
            quote.addQuote(QuoteType.LAST_SIZE, lastSize);
            hasValues = true;
        }

        BigDecimal volume = decimalValue(message, "v");
        if (volume != null) {
            quote.addQuote(QuoteType.VOLUME, volume);
            hasValues = true;
        }

        BigDecimal volumeNotional = decimalValue(message, "q");
        if (volumeNotional != null) {
            quote.addQuote(QuoteType.VOLUME_NOTIONAL, volumeNotional);
            hasValues = true;
        }

        if (hasValues) {
            fireLevel1Quote(quote);
        }
    }

    void onOptionTickerUpdate(Ticker ticker, JsonNode message) {
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "E"));
        boolean hasValues = false;

        BigDecimal bestBid = decimalValue(message, "bo");
        if (bestBid != null) {
            quote.addQuote(QuoteType.BID, bestBid);
            hasValues = true;
        }

        BigDecimal bidSize = decimalValue(message, "bq");
        if (bidSize != null) {
            quote.addQuote(QuoteType.BID_SIZE, bidSize);
            hasValues = true;
        }

        BigDecimal bestAsk = decimalValue(message, "ao");
        if (bestAsk != null) {
            quote.addQuote(QuoteType.ASK, bestAsk);
            hasValues = true;
        }

        BigDecimal askSize = decimalValue(message, "aq");
        if (askSize != null) {
            quote.addQuote(QuoteType.ASK_SIZE, askSize);
            hasValues = true;
        }

        BigDecimal volume = decimalValue(message, "V");
        if (volume != null) {
            quote.addQuote(QuoteType.VOLUME, volume);
            hasValues = true;
        }

        BigDecimal volumeNotional = decimalValue(message, "A");
        if (volumeNotional != null) {
            quote.addQuote(QuoteType.VOLUME_NOTIONAL, volumeNotional);
            hasValues = true;
        }

        BigDecimal lastPrice = decimalValue(message, "c");
        if (lastPrice != null) {
            quote.addQuote(QuoteType.LAST, lastPrice);
            hasValues = true;
        }

        BigDecimal lastSize = decimalValue(message, "Q");
        if (lastSize != null) {
            quote.addQuote(QuoteType.LAST_SIZE, lastSize);
            hasValues = true;
        }

        BigDecimal markPrice = decimalValue(message, "mp");
        if (markPrice != null) {
            quote.addQuote(QuoteType.MARK_PRICE, markPrice);
            hasValues = true;
        }

        if (hasValues) {
            fireLevel1Quote(quote);
        }
    }

    void onOrderBookUpdate(Ticker ticker, JsonNode message) {
        List<OrderBook.PriceLevel> bids = toPriceLevels(message.path("b"));
        List<OrderBook.PriceLevel> asks = toPriceLevels(message.path("a"));
        if (bids.isEmpty() && asks.isEmpty()) {
            return;
        }

        OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());
        ZonedDateTime timestamp = isOptionTicker(ticker) ? toTimestamp(message, "T") : toTimestamp(message, "E");
        orderBook.updateFromSnapshot(bids, asks, timestamp);
        fireMarketDepthQuote(new Level2Quote(ticker, orderBook, timestamp));
    }

    void onTradeUpdate(Ticker ticker, JsonNode message) {
        BigDecimal price = decimalValue(message, "p");
        BigDecimal size = decimalValue(message, "q");
        if (price == null || size == null) {
            return;
        }

        OrderFlow.Side side = resolveTradeSide(ticker, message);
        OrderFlow orderFlow = new OrderFlow(ticker, price, size, side, toTimestamp(message, "T"));
        fireOrderFlow(orderFlow);
    }

    protected void startBookTickerStream(Ticker ticker) {
        webSocketApi.subscribeBookTicker(ticker, message -> safeRun(() -> onBookTickerUpdate(ticker, message),
                "bookTicker", ticker));
    }

    protected void startMarkPriceStream(Ticker ticker) {
        webSocketApi.subscribeMarkPrice(ticker, message -> safeRun(() -> onMarkPriceUpdate(ticker, message),
                "markPrice", ticker));
    }

    protected void startSymbolTickerStream(Ticker ticker) {
        webSocketApi.subscribeSymbolTicker(ticker, message -> safeRun(() -> onSymbolTickerUpdate(ticker, message),
                "ticker", ticker));
    }

    protected void startDepthStream(Ticker ticker) {
        webSocketApi.subscribePartialDepth(ticker, DEFAULT_DEPTH,
                message -> safeRun(() -> onOrderBookUpdate(ticker, message), "depth", ticker));
    }

    protected void startTradeStream(Ticker ticker) {
        String streamName = isOptionTicker(ticker) ? "optionTrade" : "aggTrade";
        webSocketApi.subscribeAggTrades(ticker,
                message -> safeRun(() -> onTradeUpdate(ticker, message), streamName, ticker));
    }

    protected void safeRun(Runnable action, String streamType, Ticker ticker) {
        try {
            action.run();
        } catch (Exception e) {
            logger.warn("Failed to process {} update for {}", streamType, ticker.getSymbol(), e);
        }
    }

    protected void requireTicker(Ticker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
    }

    protected BigDecimal decimalValue(JsonNode message, String field) {
        if (message == null || field == null) {
            return null;
        }
        String value = message.path(field).asText("");
        if (value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    protected ZonedDateTime toTimestamp(JsonNode message, String field) {
        long epochMillis = message == null ? 0L : message.path(field).asLong(0L);
        if (epochMillis <= 0L) {
            return ZonedDateTime.now(ZoneId.of("UTC"));
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("UTC"));
    }

    protected BigDecimal toHourlyFundingRate(Ticker ticker, BigDecimal fundingRate) {
        int intervalHours = ticker == null || ticker.getFundingRateInterval() <= 0 ? DEFAULT_FUNDING_INTERVAL_HOURS
                : ticker.getFundingRateInterval();
        return fundingRate.divide(BigDecimal.valueOf(intervalHours), MathContext.DECIMAL64);
    }

    protected List<OrderBook.PriceLevel> toPriceLevels(JsonNode levels) {
        List<OrderBook.PriceLevel> priceLevels = new ArrayList<>();
        if (levels == null || !levels.isArray()) {
            return priceLevels;
        }
        for (JsonNode level : levels) {
            if (level == null || !level.isArray() || level.size() < 2) {
                continue;
            }
            String priceString = level.get(0).asText("");
            String quantityString = level.get(1).asText("");
            if (priceString.isBlank() || quantityString.isBlank()) {
                continue;
            }
            priceLevels.add(new OrderBook.PriceLevel(new BigDecimal(priceString), Double.parseDouble(quantityString)));
        }
        return priceLevels;
    }

    protected OrderFlow.Side resolveTradeSide(Ticker ticker, JsonNode message) {
        if (isOptionTicker(ticker)) {
            int signedDirection = message == null ? 0 : message.path("S").asInt(0);
            return signedDirection < 0 ? OrderFlow.Side.SELL : OrderFlow.Side.BUY;
        }
        return message != null && message.path("m").asBoolean(false) ? OrderFlow.Side.SELL : OrderFlow.Side.BUY;
    }

    protected boolean isOptionTicker(Ticker ticker) {
        if (ticker == null) {
            return false;
        }
        InstrumentType instrumentType = ticker.getInstrumentType();
        return instrumentType == InstrumentType.OPTION || instrumentType == InstrumentType.PERPETUAL_OPTION;
    }
}
