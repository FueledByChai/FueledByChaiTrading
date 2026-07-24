package com.fueledbychai.marketdata.grvt;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.grvt.common.api.IGrvtRestApi;
import com.fueledbychai.grvt.common.api.IGrvtWebSocketApi;
import com.fueledbychai.marketdata.ILevel1Quote;
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

/**
 * GRVT market-data quote engine. Subscribes to the public market-data WebSocket streams:
 * {@code mini.s}/{@code ticker.s} for Level 1, {@code book.d} for Level 2 depth, and {@code trade}
 * for order flow.
 * <p>
 * GRVT market-data payloads express prices and sizes as human-readable decimal strings. The
 * {@code 10^9} fixed-point conversion used by GRVT's EIP-712 order signer does not apply to public
 * market data.
 */
public class GrvtQuoteEngine extends QuoteEngine {

    private static final Logger logger = LoggerFactory.getLogger(GrvtQuoteEngine.class);
    private static final long NO_SEQUENCE = -1L;
    private static final String LEVEL1_RATE_MS = "500";
    private static final String BOOK_DELTA_RATE_MS = "50";
    private static final String DEFAULT_TRADE_LIMIT = "50";
    private static final int RESYNC_DEPTH = 500;
    private static final int PUBLISHED_DEPTH = 10;
    private static final BigDecimal BPS_PER_PERCENT = new BigDecimal("100");
    private static final BigDecimal HOURS_PER_YEAR = new BigDecimal("8760");

    protected volatile boolean started = false;
    protected final IGrvtRestApi restApi;
    protected final IGrvtWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;
    protected final Set<Ticker> level1StreamsStarted = ConcurrentHashMap.newKeySet();
    protected final Set<Ticker> depthStreamsStarted = ConcurrentHashMap.newKeySet();
    protected final Set<Ticker> tradeStreamsStarted = ConcurrentHashMap.newKeySet();
    protected final Map<Ticker, BookState> bookStates = new ConcurrentHashMap<>();

    public GrvtQuoteEngine() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.GRVT, IGrvtRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.GRVT, IGrvtWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.GRVT));
    }

    protected GrvtQuoteEngine(IGrvtRestApi restApi, IGrvtWebSocketApi webSocketApi, ITickerRegistry tickerRegistry) {
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
        return "GRVT";
    }

    @Override
    public Date getServerTime() {
        return new Date();
    }

    @Override
    public boolean isConnected() {
        return started && webSocketApi.isMarketDataConnected();
    }

    /**
     * Replaces the public market-data socket without disturbing GRVT's authenticated
     * trade/order channel. Existing stream subscriptions are replayed by the API.
     */
    public void forceReconnectMarketData() {
        if (!started) {
            throw new IllegalStateException("GRVT quote engine is not started");
        }
        webSocketApi.forceReconnectMarketData();
    }

    @Override
    public void startEngine() {
        started = true;
        webSocketApi.connectMarketData();
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
        bookStates.clear();
        clearSessionListeners();
        // Market-data lifecycle is independent from the authenticated broker
        // socket, even though both are managed by the cached GRVT API.
        webSocketApi.disconnectMarketData();
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        requireTicker(ticker);
        boolean startStreams = level1StreamsStarted.add(ticker);
        super.subscribeLevel1(ticker, listener);
        if (startStreams) {
            String symbol = ticker.getSymbol();
            webSocketApi.subscribeMarketData("mini.s", symbol + "@" + LEVEL1_RATE_MS,
                    message -> safeRun(() -> onMiniTicker(ticker, message), "mini.s", ticker));
            webSocketApi.subscribeMarketData("ticker.s", symbol + "@" + LEVEL1_RATE_MS,
                    message -> safeRun(() -> onTicker(ticker, message), "ticker.s", ticker));
        }
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        requireTicker(ticker);
        boolean startStream = depthStreamsStarted.add(ticker);
        super.subscribeMarketDepth(ticker, listener);
        if (startStream) {
            bookStates.computeIfAbsent(ticker, ignored -> new BookState());
            String selector = ticker.getSymbol() + "@" + BOOK_DELTA_RATE_MS;
            webSocketApi.subscribeMarketData("book.d", selector,
                    message -> safeRun(() -> onOrderBook(ticker, message), "book.d", ticker));
        }
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        requireTicker(ticker);
        boolean startStream = tradeStreamsStarted.add(ticker);
        super.subscribeOrderFlow(ticker, listener);
        if (startStream) {
            webSocketApi.subscribeMarketData("trade", ticker.getSymbol() + "@" + DEFAULT_TRADE_LIMIT,
                    message -> safeRun(() -> onTrade(ticker, message), "trade", ticker));
        }
    }

    @Override
    public ILevel1Quote requestLevel1Snapshot(Ticker ticker) {
        requireTicker(ticker);
        JsonNode mini = restApi.getMiniTicker(ticker.getSymbol());
        if (mini == null || mini.isNull() || mini.isMissingNode()) {
            throw new IllegalStateException("No mini ticker data returned for " + ticker.getSymbol());
        }
        Level1Quote quote = new Level1Quote(ticker, ZonedDateTime.now(ZoneId.of("UTC")));
        applyTopOfBook(quote, mini);
        return quote;
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.error("useDelayedData() is not supported for GRVT market data");
    }

    // ------------------------------------------------------------- handlers

    protected void onMiniTicker(Ticker ticker, JsonNode message) {
        JsonNode feed = feed(message);
        Level1Quote quote = new Level1Quote(ticker, timestamp(feed));
        if (applyTopOfBook(quote, feed)) {
            fireLevel1Quote(quote);
        }
    }

    protected void onTicker(Ticker ticker, JsonNode message) {
        JsonNode feed = feed(message);
        Level1Quote quote = new Level1Quote(ticker, timestamp(feed));
        boolean hasValues = false;
        BigDecimal lastPrice = decimal(feed, "last_price");
        if (lastPrice != null) {
            quote.addQuote(QuoteType.LAST, lastPrice);
            hasValues = true;
        }
        BigDecimal lastSize = decimal(feed, "last_size");
        if (lastSize != null) {
            quote.addQuote(QuoteType.LAST_SIZE, lastSize);
            hasValues = true;
        }
        BigDecimal markPrice = decimal(feed, "mark_price");
        if (markPrice != null) {
            quote.addQuote(QuoteType.MARK_PRICE, markPrice);
            hasValues = true;
        }
        BigDecimal fundingRatePercent = decimal(feed, "funding_rate");
        int fundingIntervalHours = ticker.getFundingRateInterval();
        if (fundingRatePercent != null && fundingIntervalHours > 0) {
            BigDecimal intervalHours = BigDecimal.valueOf(fundingIntervalHours);
            // GRVT expresses funding_rate in percentage points for the active funding
            // interval. Chaiwala consumes both annualized percent and hourly bps.
            quote.addQuote(QuoteType.FUNDING_RATE_HOURLY_BPS,
                    fundingRatePercent.multiply(BPS_PER_PERCENT).divide(intervalHours, MathContext.DECIMAL64));
            quote.addQuote(QuoteType.FUNDING_RATE_APR,
                    fundingRatePercent.multiply(HOURS_PER_YEAR).divide(intervalHours, MathContext.DECIMAL64));
            hasValues = true;
        }
        BigDecimal buyVolumeBase = decimal(feed, "buy_volume_24h_b");
        BigDecimal sellVolumeBase = decimal(feed, "sell_volume_24h_b");
        BigDecimal volumeBase = sumIfPresent(buyVolumeBase, sellVolumeBase);
        if (volumeBase != null) {
            quote.addQuote(QuoteType.VOLUME, volumeBase);
            hasValues = true;
        }
        BigDecimal buyVolumeQuote = decimal(feed, "buy_volume_24h_q");
        BigDecimal sellVolumeQuote = decimal(feed, "sell_volume_24h_q");
        BigDecimal volumeNotional = sumIfPresent(buyVolumeQuote, sellVolumeQuote);
        if (volumeNotional != null) {
            quote.addQuote(QuoteType.VOLUME_NOTIONAL, volumeNotional);
            hasValues = true;
        }
        if (hasValues) {
            fireLevel1Quote(quote);
        }
    }

    protected void onOrderBook(Ticker ticker, JsonNode message) {
        JsonNode feed = feed(message);
        JsonNode bids = feed.path("bids");
        JsonNode asks = feed.path("asks");
        if (!bids.isArray() && !asks.isArray()) {
            return;
        }

        BookState state = bookStates.computeIfAbsent(ticker, ignored -> new BookState());
        ZonedDateTime timestamp = timestamp(feed);
        long sequence = sequence(message, "sequence_number");
        long previousSequence = sequence(message, "prev_sequence_number");
        long eventTimeNanos = eventTimeNanos(feed);
        OrderBook orderBook;

        synchronized (state) {
            if (isSnapshotAnchor(message)) {
                replaceBook(state, bids, asks);
                state.initialized = hasTwoSidedBook(state);
                state.awaitingResync = false;
                state.lastSequence = NO_SEQUENCE;
                if (!state.initialized) {
                    logger.warn("Ignoring incomplete GRVT snapshot anchor for {}", ticker.getSymbol());
                    return;
                }
            } else if (!state.initialized && !state.awaitingResync) {
                replaceBook(state, bids, asks);
                state.initialized = hasTwoSidedBook(state);
                if (!state.initialized) {
                    logger.warn("Ignoring incomplete initial GRVT book for {}", ticker.getSymbol());
                    return;
                }
            } else {
                boolean sequenceReset = sequence != NO_SEQUENCE && state.lastSequence != NO_SEQUENCE
                        && sequence < state.lastSequence && previousSequence != state.lastSequence;
                if (sequenceReset) {
                    state.awaitingResync = true;
                    logger.warn("GRVT book sequence reset for {} (lastSequence={}, previousSequence={}, sequence={}); resynchronizing",
                            ticker.getSymbol(), state.lastSequence, previousSequence, sequence);
                }
                if (sequence != NO_SEQUENCE && state.lastSequence != NO_SEQUENCE
                        && sequence <= state.lastSequence && !sequenceReset) {
                    logger.debug("Skipping stale GRVT book frame for {} (sequence={}, lastSequence={})",
                            ticker.getSymbol(), sequence, state.lastSequence);
                    return;
                }

                if (!state.awaitingResync && previousSequence != NO_SEQUENCE && state.lastSequence != NO_SEQUENCE
                        && previousSequence != state.lastSequence) {
                    state.awaitingResync = true;
                    logger.warn("GRVT book sequence gap for {} (lastSequence={}, previousSequence={}, sequence={}); resynchronizing",
                            ticker.getSymbol(), state.lastSequence, previousSequence, sequence);
                }

                if (state.awaitingResync) {
                    long snapshotEventTimeNanos = resynchronizeBook(ticker, state);
                    if (snapshotEventTimeNanos < 0L) {
                        return;
                    }
                    state.awaitingResync = false;
                    // GRVT deltas contain absolute sizes. If the REST snapshot predates this frame,
                    // apply the frame; otherwise the snapshot already includes it (or something newer).
                    if (eventTimeNanos <= 0L || snapshotEventTimeNanos < eventTimeNanos) {
                        applyBookDelta(state, bids, asks);
                    }
                } else {
                    applyBookDelta(state, bids, asks);
                }
            }

            if (!hasTwoSidedBook(state)) {
                state.awaitingResync = true;
                logger.warn("GRVT delta left {} without a two-sided book; waiting for REST resynchronization",
                        ticker.getSymbol());
                return;
            }
            if (sequence != NO_SEQUENCE) {
                state.lastSequence = sequence;
            }
            orderBook = buildOrderBook(ticker, state, timestamp);
        }

        fireMarketDepthQuote(new Level2Quote(ticker, orderBook, timestamp));
    }

    protected void onTrade(Ticker ticker, JsonNode message) {
        for (JsonNode trade : feedArray(message)) {
            BigDecimal price = decimal(trade, "price");
            BigDecimal size = decimal(trade, "size");
            if (price == null || size == null) {
                continue;
            }
            OrderFlow.Side side = trade.path("is_taker_buyer").asBoolean(false) ? OrderFlow.Side.BUY
                    : OrderFlow.Side.SELL;
            fireOrderFlow(new OrderFlow(ticker, price, size, side, timestamp(trade)));
        }
    }

    // ------------------------------------------------------------- helpers

    protected boolean applyTopOfBook(Level1Quote quote, JsonNode feed) {
        boolean hasValues = false;
        BigDecimal bid = decimal(feed, "best_bid_price");
        if (bid != null) {
            quote.addQuote(QuoteType.BID, bid);
            hasValues = true;
        }
        BigDecimal bidSize = decimal(feed, "best_bid_size");
        if (bidSize != null) {
            quote.addQuote(QuoteType.BID_SIZE, bidSize);
            hasValues = true;
        }
        BigDecimal ask = decimal(feed, "best_ask_price");
        if (ask != null) {
            quote.addQuote(QuoteType.ASK, ask);
            hasValues = true;
        }
        BigDecimal askSize = decimal(feed, "best_ask_size");
        if (askSize != null) {
            quote.addQuote(QuoteType.ASK_SIZE, askSize);
            hasValues = true;
        }
        return hasValues;
    }

    protected JsonNode feed(JsonNode message) {
        if (message == null) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        JsonNode feed = message.has("feed") ? message.path("feed") : message;
        if (feed.isArray() && feed.size() > 0) {
            return feed.get(feed.size() - 1);
        }
        return feed;
    }

    protected List<JsonNode> feedArray(JsonNode message) {
        List<JsonNode> items = new ArrayList<>();
        if (message == null) {
            return items;
        }
        JsonNode feed = message.has("feed") ? message.path("feed") : message;
        if (feed.isArray()) {
            feed.forEach(items::add);
        } else if (!feed.isMissingNode() && !feed.isNull()) {
            items.add(feed);
        }
        return items;
    }

    protected BigDecimal decimal(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected BigDecimal sumIfPresent(BigDecimal first, BigDecimal second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.add(second);
    }

    protected long sequence(JsonNode message, String field) {
        if (message == null || field == null) {
            return NO_SEQUENCE;
        }
        long value = message.path(field).asLong(NO_SEQUENCE);
        // GRVT labels the initial full book.d snapshot with sequence 0, then the first
        // incremental frame starts at the gateway's live sequence and links to its own
        // predecessor. Sequence 0 is therefore an unsequenced snapshot anchor, not a delta.
        return value > 0L ? value : NO_SEQUENCE;
    }

    protected boolean isSnapshotAnchor(JsonNode message) {
        return message != null && message.path("sequence_number").asLong(NO_SEQUENCE) == 0L;
    }

    protected long eventTimeNanos(JsonNode feed) {
        if (feed == null) {
            return 0L;
        }
        return feed.path("event_time").asLong(0L);
    }

    protected void replaceBook(BookState state, JsonNode bids, JsonNode asks) {
        state.bids.clear();
        state.asks.clear();
        applyLevels(state.bids, bids);
        applyLevels(state.asks, asks);
    }

    protected void applyBookDelta(BookState state, JsonNode bids, JsonNode asks) {
        applyLevels(state.bids, bids);
        applyLevels(state.asks, asks);
    }

    protected void applyLevels(NavigableMap<BigDecimal, BigDecimal> side, JsonNode levels) {
        if (side == null || levels == null || !levels.isArray()) {
            return;
        }
        for (JsonNode level : levels) {
            BigDecimal price = decimal(level, "price");
            BigDecimal size = decimal(level, "size");
            if (price == null || price.signum() <= 0 || size == null) {
                continue;
            }
            if (size.signum() <= 0) {
                side.remove(price);
            } else {
                side.put(price, size);
            }
        }
    }

    protected long resynchronizeBook(Ticker ticker, BookState state) {
        try {
            JsonNode snapshot = restApi.getOrderBook(ticker.getSymbol(), RESYNC_DEPTH);
            if (snapshot != null && snapshot.has("result")) {
                snapshot = snapshot.path("result");
            }
            snapshot = feed(snapshot);
            if (snapshot == null || !snapshot.path("bids").isArray() || !snapshot.path("asks").isArray()) {
                logger.warn("GRVT REST book resynchronization returned no usable book for {}", ticker.getSymbol());
                return -1L;
            }
            replaceBook(state, snapshot.path("bids"), snapshot.path("asks"));
            state.initialized = hasTwoSidedBook(state);
            if (!state.initialized) {
                logger.warn("GRVT REST book resynchronization returned an incomplete book for {}", ticker.getSymbol());
                return -1L;
            }
            return eventTimeNanos(snapshot);
        } catch (RuntimeException e) {
            logger.warn("Failed to resynchronize GRVT book for {}: {}", ticker.getSymbol(), e.toString());
            return -1L;
        }
    }

    protected boolean hasTwoSidedBook(BookState state) {
        return state != null && !state.bids.isEmpty() && !state.asks.isEmpty();
    }

    protected OrderBook buildOrderBook(Ticker ticker, BookState state, ZonedDateTime timestamp) {
        OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());
        orderBook.updateFromSnapshot(toPriceLevels(state.bids, PUBLISHED_DEPTH),
                toPriceLevels(state.asks, PUBLISHED_DEPTH), timestamp);
        return orderBook;
    }

    protected List<OrderBook.PriceLevel> toPriceLevels(NavigableMap<BigDecimal, BigDecimal> side, int depth) {
        int limit = Math.max(0, depth);
        List<OrderBook.PriceLevel> levels = new ArrayList<>(Math.min(side.size(), limit));
        for (Map.Entry<BigDecimal, BigDecimal> entry : side.entrySet()) {
            if (levels.size() >= limit) {
                break;
            }
            levels.add(new OrderBook.PriceLevel(entry.getKey(), entry.getValue().doubleValue()));
        }
        return levels;
    }

    protected ZonedDateTime timestamp(JsonNode node) {
        long nanos = node == null ? 0L : node.path("event_time").asLong(0L);
        if (nanos <= 0L) {
            return ZonedDateTime.now(ZoneId.of("UTC"));
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(nanos / 1_000_000L), ZoneId.of("UTC"));
    }

    protected void safeRun(Runnable action, String streamType, Ticker ticker) {
        try {
            action.run();
        } catch (Exception e) {
            logger.warn("Failed to process GRVT {} update for {}", streamType, ticker.getSymbol(), e);
        }
    }

    protected void requireTicker(Ticker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
    }

    protected static final class BookState {
        private final NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
        private final NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
        private boolean initialized;
        private boolean awaitingResync;
        private long lastSequence = NO_SEQUENCE;
    }
}
