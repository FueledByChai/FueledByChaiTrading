package com.fueledbychai.marketdata.hibachi;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.hibachi.common.api.HibachiConfiguration;
import com.fueledbychai.hibachi.common.api.IHibachiRestApi;
import com.fueledbychai.hibachi.common.api.ws.HibachiJsonProcessor;
import com.fueledbychai.hibachi.common.api.ws.HibachiMarketSubscribeMessage;
import com.fueledbychai.hibachi.common.api.ws.HibachiTopicRouter;
import com.fueledbychai.hibachi.common.api.ws.HibachiWebSocketClient;
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
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

/**
 * Hibachi market-data quote engine.
 *
 * <p>Maintains a single shared market WebSocket connection (Hibachi multiplexes topics on
 * one connection). Subscriptions map to:
 * <ul>
 *   <li><b>L1</b> = {@code ask_bid_price} + {@code mark_price}</li>
 *   <li><b>L2</b> = {@code orderbook}</li>
 *   <li><b>OrderFlow</b> = {@code trades}</li>
 * </ul>
 */
public class HibachiQuoteEngine extends QuoteEngine {

    private static final Logger logger = LoggerFactory.getLogger(HibachiQuoteEngine.class);
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final java.math.MathContext MC = java.math.MathContext.DECIMAL64;
    private static final BigDecimal HOURS_PER_YEAR = BigDecimal.valueOf(24L * 365L);
    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);
    private static final BigDecimal BPS_MULTIPLIER = BigDecimal.valueOf(10000);

    protected final IHibachiRestApi restApi;
    protected final ITickerRegistry tickerRegistry;
    protected final HibachiConfiguration config;
    protected final Set<SubKey> activeSubscriptions = ConcurrentHashMap.newKeySet();

    protected volatile HibachiWebSocketClient marketClient;
    protected volatile HibachiJsonProcessor marketProcessor;
    protected volatile boolean started = false;

    protected final Set<String> volumePollingSymbols = ConcurrentHashMap.newKeySet();
    protected volatile ScheduledExecutorService volumeScheduler;

    // Per-ticker accumulated order book state. Hibachi sends an initial
    // {messageType:"Snapshot"} containing the full visible depth, then
    // {messageType:"Update"} frames carrying ONLY changed levels (qty=0
    // means "remove this level"). Treating every frame as a snapshot the way
    // the previous implementation did caused fresh OrderBooks to contain
    // only the deltas — best ask would flicker from 84 → 86 when the only
    // levels echoed back were a couple of mid-book changes around 86.
    protected static final class BookState {
        final java.util.TreeMap<BigDecimal, BigDecimal> bids =
                new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        final java.util.TreeMap<BigDecimal, BigDecimal> asks = new java.util.TreeMap<>();
        // Last top-of-book emitted as a book-derived L1 quote (live_book mode),
        // so we only fire L1 when the touch actually changes — not on every
        // 5ms book frame.
        BigDecimal lastEmitBid;
        BigDecimal lastEmitBidSize;
        BigDecimal lastEmitAsk;
        BigDecimal lastEmitAskSize;
    }

    protected final java.util.Map<String, BookState> bookBySymbol = new ConcurrentHashMap<>();

    // Trade-vs-book mismatch telemetry (kept after the L1 sanity checker was
    // removed 2026-05-27). Hibachi's WS pulses L1 and trades on the same
    // 250ms heartbeat but snapshots them at different points within the
    // window, so trade-inside-book is structural and not evidence of a
    // stale L1. We still count the rate of these events for the support
    // tickets we send to Hibachi. Per-symbol last-known top-of-book is
    // recorded as L1 fires; emitTrade compares against it.
    private static final class FeedConsistencyState {
        volatile BigDecimal lastBid;
        volatile BigDecimal lastAsk;
        final java.util.concurrent.atomic.AtomicLong mismatchCount =
                new java.util.concurrent.atomic.AtomicLong();
        volatile long lastLogMs = 0L;
    }
    private static final long MISMATCH_LOG_THROTTLE_MS = 60_000L;
    private final java.util.Map<String, FeedConsistencyState> feedConsistency = new ConcurrentHashMap<>();

    protected volatile ScheduledFuture<?> volumeTask;

    // Auto-reconnect state. First retry is ~500ms for fast recovery from
    // transient blips (cloudflare hiccup, brief network reset); subsequent
    // retries back off exponentially up to 30s so we don't hammer the exchange
    // during a prolonged outage. Counter resets on successful reconnect.
    // Progression: 500ms → 1s → 2s → 4s → 8s → 16s → 30s (capped).
    private static final long RECONNECT_INITIAL_DELAY_MS = 500L;
    private static final long RECONNECT_MAX_DELAY_MS = 30_000L;
    protected volatile ScheduledExecutorService reconnectScheduler;
    protected final java.util.concurrent.atomic.AtomicInteger reconnectAttempt =
            new java.util.concurrent.atomic.AtomicInteger(0);
    // Pending reconnect task — used to dedup. If non-null and not done, a
    // reconnect is already scheduled and additional scheduleReconnect() calls
    // are no-ops. Without this, the 2026-05-20 V4 cascade fired ~163
    // overlapping reconnects within seconds: each close event scheduled a new
    // task without checking whether one was already pending, and the
    // tearDownPartialClient() call inside attemptReconnect() itself triggers
    // onMarketWsClosed for the old client which would schedule yet another.
    protected volatile java.util.concurrent.ScheduledFuture<?> reconnectTask;
    // Generation counter incremented every time a new market WS client is
    // created. The close-listener captures the generation at creation time;
    // when a close event fires for a stale (already-replaced) client it's
    // ignored. Prevents tearDownPartialClient()'s close from triggering a
    // reconnect cascade as the old client is being intentionally retired.
    protected final java.util.concurrent.atomic.AtomicLong clientGeneration =
            new java.util.concurrent.atomic.AtomicLong(0L);

    public HibachiQuoteEngine() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.HIBACHI, IHibachiRestApi.class),
                TickerRegistryFactory.getInstance(Exchange.HIBACHI),
                HibachiConfiguration.getInstance());
        logger.info("Hibachi market WS URL: {}", config.getMarketWsUrl());
    }

    protected HibachiQuoteEngine(IHibachiRestApi restApi, ITickerRegistry tickerRegistry,
                                 HibachiConfiguration config) {
        if (restApi == null) throw new IllegalArgumentException("restApi is required");
        if (tickerRegistry == null) throw new IllegalArgumentException("tickerRegistry is required");
        if (config == null) throw new IllegalArgumentException("config is required");
        this.restApi = restApi;
        this.tickerRegistry = tickerRegistry;
        this.config = config;
    }

    @Override
    public String getDataProviderName() {
        return "Hibachi";
    }

    @Override
    public Date getServerTime() {
        return restApi.getServerTime();
    }

    @Override
    public boolean isConnected() {
        return started && marketClient != null && marketClient.isOpen();
    }

    @Override
    public void startEngine() {
        if (started) {
            return;
        }
        started = true;
        ensureMarketClient();
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
        activeSubscriptions.clear();
        volumePollingSymbols.clear();
        if (volumeTask != null) {
            volumeTask.cancel(false);
            volumeTask = null;
        }
        if (volumeScheduler != null) {
            volumeScheduler.shutdownNow();
            volumeScheduler = null;
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            reconnectScheduler = null;
        }
        reconnectAttempt.set(0);
        HibachiWebSocketClient client = marketClient;
        marketClient = null;
        HibachiJsonProcessor processor = marketProcessor;
        marketProcessor = null;
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
        if (processor != null) {
            processor.shutdown();
        }
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        requireTicker(ticker);
        super.subscribeLevel1(ticker, listener);
        ensureMarketClient();
        for (String topic : HibachiTopicRouter.LEVEL1_TOPICS) {
            subscribeTopic(ticker, topic);
        }
        startVolumePolling(ticker);
    }

    @Override
    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        requireTicker(ticker);
        super.subscribeMarketDepth(ticker, listener);
        ensureMarketClient();
        // MM-partner live_book channel (~5ms, 10 levels) when enabled, else the
        // standard ~250-300ms orderbook. Same message schema → same handler.
        String depthTopic = config.isMarketDataLiveBook()
                ? HibachiTopicRouter.TOPIC_LIVE_BOOK
                : HibachiTopicRouter.LEVEL2_TOPIC;
        logger.info("Hibachi L2 depth feed for {}: topic={} ({})", ticker.getSymbol(), depthTopic,
                config.isMarketDataLiveBook() ? "live_book ~5ms" : "orderbook ~250-300ms");
        subscribeTopic(ticker, depthTopic);
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        requireTicker(ticker);
        super.subscribeOrderFlow(ticker, listener);
        ensureMarketClient();
        subscribeTopic(ticker, HibachiTopicRouter.ORDER_FLOW_TOPIC);
    }

    @Override
    public ILevel1Quote requestLevel1Snapshot(Ticker ticker) {
        requireTicker(ticker);
        JsonNode response = restApi.getOrderBookSnapshot(ticker.getSymbol());
        if (response == null || response.isMissingNode()) {
            throw new IllegalStateException("No order-book snapshot for " + ticker.getSymbol());
        }
        Level1Quote quote = new Level1Quote(ticker, ZonedDateTime.now(UTC));
        BigDecimal bid = topPrice(response, "bids");
        BigDecimal ask = topPrice(response, "asks");
        if (bid != null) quote.addQuote(QuoteType.BID, bid);
        if (ask != null) quote.addQuote(QuoteType.ASK, ask);
        return quote;
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        logger.debug("useDelayedData() not supported for Hibachi");
    }

    // ---------- WS plumbing ----------

    /**
     * Idempotently bring the market WS client to an open state. Returns
     * {@code true} if the client is open on return, {@code false} otherwise.
     *
     * <p><b>Non-throwing</b> (as of 2026-05-20). If the connect attempt fails
     * for any reason (timeout, network down, venue rejecting connections),
     * this method logs the failure at WARN, schedules an async reconnect via
     * the standard backoff path, and returns {@code false}. Callers that
     * need to know the current state should check {@link #isConnected()} or
     * the return value here.
     *
     * <p><b>Why non-throwing</b>: previously a venue-down at app startup
     * threw out of {@code startEngine()} → propagated through
     * {@code initStrategy()} → crashed the Spring boot. The 2026-05-20
     * Hibachi recurrence showed this in production. Market makers running
     * 24/7 on venues prone to transient outages need the app to start,
     * park itself, and recover when the venue comes back — not refuse to
     * boot until someone manually intervenes. Existing feed-staleness
     * checks downstream prevent any quoting attempts while the WS is dead.
     */
    protected synchronized boolean ensureMarketClient() {
        if (marketClient != null && marketClient.isOpen()) {
            return true;
        }
        // Capture this client's generation BEFORE constructing the processor —
        // the close-listener uses it to ignore close events for already-
        // superseded clients (see clientGeneration field docs).
        long gen = clientGeneration.incrementAndGet();
        try {
            marketProcessor = new HibachiJsonProcessor(() -> onMarketWsClosed(gen));
            marketProcessor.addEventListener(this::onMarketMessage);
            marketClient = HibachiWebSocketClient.createMarket(
                    config.getMarketWsUrl(), marketProcessor, config.getClient(), null);
            if (!marketClient.connectBlocking(10, TimeUnit.SECONDS)) {
                logger.warn("Timed out connecting to Hibachi market WS at {}; scheduling reconnect",
                        config.getMarketWsUrl());
                tearDownPartialClient();
                if (started) scheduleReconnect();
                return false;
            }
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted connecting to Hibachi market WS; scheduling reconnect");
            tearDownPartialClient();
            if (started) scheduleReconnect();
            return false;
        } catch (Exception e) {
            logger.warn("Failed to create Hibachi market WS client: {}; scheduling reconnect", e.toString());
            tearDownPartialClient();
            if (started) scheduleReconnect();
            return false;
        }
    }

    /**
     * Drop any partially-initialized client/processor state from a failed
     * connect attempt so the next try starts clean. Called by
     * {@link #ensureMarketClient} on every failure path.
     */
    private void tearDownPartialClient() {
        HibachiWebSocketClient stale = marketClient;
        HibachiJsonProcessor staleProc = marketProcessor;
        marketClient = null;
        marketProcessor = null;
        if (stale != null) {
            try { stale.close(); } catch (Exception ignored) {}
        }
        if (staleProc != null) {
            try { staleProc.shutdown(); } catch (Exception ignored) {}
        }
    }

    protected synchronized void subscribeTopic(Ticker ticker, String topic) {
        SubKey key = new SubKey(ticker.getSymbol(), topic);
        if (!activeSubscriptions.add(key)) {
            return;
        }
        try {
            String msg = HibachiMarketSubscribeMessage.subscribe(ticker.getSymbol(), topic);
            if (marketClient != null && marketClient.isOpen()) {
                logger.info("Hibachi WS send: {}", msg);
                marketClient.send(msg);
            } else {
                logger.warn("Hibachi WS not open; dropping subscribe for {} {}", ticker.getSymbol(), topic);
            }
        } catch (Exception e) {
            logger.warn("Failed to send subscribe for {} {}", ticker.getSymbol(), topic, e);
        }
    }

    /**
     * Close-event handler for a specific market WS client. The {@code gen}
     * argument is the {@link #clientGeneration} value at the time the
     * processor was constructed in {@link #ensureMarketClient}. If a later
     * client has already superseded this one (e.g. because we called
     * {@link #tearDownPartialClient} as part of an in-flight reconnect),
     * the stale-client close fires here AFTER the new client is live —
     * ignoring those events is the only way to avoid the 2026-05-20
     * reconnect cascade where every tearDown triggered a fresh
     * scheduleReconnect that ran concurrently with the in-flight attempt.
     */
    protected void onMarketWsClosed(long gen) {
        if (!started) {
            return;
        }
        long current = clientGeneration.get();
        if (gen != current) {
            // Stale close event — already replaced. Don't schedule.
            return;
        }
        int attempts = reconnectAttempt.get();
        logger.warn("Hibachi market WS closed; scheduling reconnect (attempt will be #{} )",
                attempts + 1);
        scheduleReconnect();
    }

    /**
     * Exponential backoff reconnect, capped at {@link #RECONNECT_MAX_DELAY_MS}.
     * The attempt counter reads while scheduling (so the very first retry after
     * a healthy session has no delay growth) and resets to 0 once a reconnect
     * succeeds in {@link #attemptReconnect()}.
     *
     * <p>Deduplicates pending reconnects via {@link #reconnectTask}: if a
     * task is already scheduled and not yet done, additional calls are
     * no-ops. Without this, every close event + every internal failure
     * path queued a new task, producing the 2026-05-20 V4 fan-out.
     */
    protected synchronized void scheduleReconnect() {
        if (!started) {
            return;
        }
        if (reconnectTask != null && !reconnectTask.isDone()) {
            // Already pending — don't pile on.
            return;
        }
        if (reconnectScheduler == null || reconnectScheduler.isShutdown()) {
            reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "hibachi-ws-reconnect");
                t.setDaemon(true);
                return t;
            });
        }
        int attempt = reconnectAttempt.incrementAndGet();
        // Backoff: 500ms, 1s, 2s, 4s, 8s, 16s, 30s (capped), 30s, ...
        long delayMs = Math.min(RECONNECT_MAX_DELAY_MS,
                RECONNECT_INITIAL_DELAY_MS * (1L << Math.min(attempt - 1, 6)));
        reconnectTask = reconnectScheduler.schedule(this::attemptReconnect, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Tear down the stale WS state, rebuild a fresh connection, and replay
     * every topic that was subscribed before the disconnect. If the rebuild
     * itself fails, logs and re-schedules another attempt on the same backoff
     * curve.
     *
     * <p><b>Subscription preservation</b>: the prior implementation snapshotted
     * {@code activeSubscriptions}, cleared the set, then called
     * {@code ensureMarketClient()}. If that call threw (e.g., "Failed to
     * create Hibachi market WS client"), the snapshot was lost on the
     * exception-return and the cleared set stayed empty. The next reconnect
     * attempt then snapshotted an empty set, and on indefinitely — so when
     * a reconnect FINALLY succeeded, "replayed 0 subscription(s)" was the
     * result and the algo got a silently-healthy WS with zero data flow.
     * Caused the 2026-05-19 Hibachi outage to look like a successful
     * recovery from the broker side while quoting silently went dark.
     *
     * <p>Fix: don't touch {@code activeSubscriptions} until the new client
     * is confirmed open. If {@code ensureMarketClient} throws, the set is
     * untouched and the next attempt has the full subscription list to
     * replay. The clear happens AFTER the new client exists and IMMEDIATELY
     * before the replay loop, preserving the original "subscribeTopic is
     * a no-op if already in set" guard.
     */
    protected synchronized void attemptReconnect() {
        if (!started) {
            return;
        }
        // Drop the dead client & processor. ensureMarketClient() only
        // rebuilds if marketClient is null or closed, so we null them first.
        tearDownPartialClient();

        if (!ensureMarketClient()) {
            // ensureMarketClient already logged + scheduled the next attempt.
            // Subscriptions stay intact because we haven't touched the set yet —
            // see the 2026-05-19 outage note in this file.
            return;
        }

        // Client is open. NOW it's safe to clear-then-replay; subscribeTopic
        // is a no-op when the SubKey is still present, so we have to clear
        // the set before replaying or nothing gets sent on the new wire.
        List<SubKey> toReplay = new ArrayList<>(activeSubscriptions);
        activeSubscriptions.clear();

        int replayed = 0;
        for (SubKey key : toReplay) {
            try {
                Ticker ticker = lookupTicker(key.symbol);
                if (ticker == null) {
                    logger.warn("Cannot replay Hibachi subscription: ticker not found for symbol={} topic={}",
                            key.symbol, key.topic);
                    continue;
                }
                subscribeTopic(ticker, key.topic);
                replayed++;
            } catch (Exception e) {
                logger.warn("Failed to replay Hibachi subscription symbol={} topic={}: {}",
                        key.symbol, key.topic, e.toString());
            }
        }
        reconnectAttempt.set(0);
        if (toReplay.isEmpty()) {
            // Empty toReplay AFTER the new client is open means we never had
            // subscriptions in the first place — bootstrap from scratch path,
            // or a startup race. Log louder so a "successful" reconnect with
            // no data flow doesn't look healthy.
            logger.warn("Hibachi market WS reconnected with NO subscriptions to replay — feed will be silent until a fresh subscribe() call");
        } else {
            logger.info("Hibachi market WS reconnected; replayed {} of {} subscription(s)",
                    replayed, toReplay.size());
        }
    }

    protected void onMarketMessage(JsonNode message) {
        if (message == null) {
            return;
        }
        String topic = message.path("topic").asText("");
        String symbol = message.path("symbol").asText("");
        if (topic.isEmpty()) {
            logger.info("Hibachi WS recv (no topic): {}", message);
            return;
        }
        logger.info("Hibachi WS dispatch topic={} symbol={}", topic, symbol);
        Ticker ticker = lookupTicker(symbol);
        if (ticker == null) {
            logger.warn("Hibachi WS no ticker for symbol={} topic={}", symbol, topic);
            return;
        }
        try {
            switch (topic) {
                case HibachiTopicRouter.TOPIC_ASK_BID_PRICE -> onAskBidUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_MARK_PRICE -> onMarkPriceUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_ORDERBOOK -> onOrderBookUpdate(ticker, message);
                // live_book shares the orderbook schema (snapshot/delta levels) → same handler.
                case HibachiTopicRouter.TOPIC_LIVE_BOOK -> onOrderBookUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_TRADES -> onTradesUpdate(ticker, message);
                case HibachiTopicRouter.TOPIC_FUNDING_RATE_ESTIMATION -> onFundingRateUpdate(ticker, message);
                default -> { /* unhandled topic */ }
            }
        } catch (Exception e) {
            logger.warn("Failed to dispatch Hibachi market message for {} {}", symbol, topic, e);
        }
    }

    protected void onAskBidUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "timestamp_ms"));
        boolean any = false;
        BigDecimal bid = decimal(data, "bidPrice");
        if (bid != null) { quote.addQuote(QuoteType.BID, bid); any = true; }
        BigDecimal bidSize = decimal(data, "bidSize");
        if (bidSize != null) { quote.addQuote(QuoteType.BID_SIZE, bidSize); any = true; }
        BigDecimal ask = decimal(data, "askPrice");
        if (ask != null) { quote.addQuote(QuoteType.ASK, ask); any = true; }
        BigDecimal askSize = decimal(data, "askSize");
        if (askSize != null) { quote.addQuote(QuoteType.ASK_SIZE, askSize); any = true; }
        if (!any) return;
        // Cache the latest top-of-book per symbol so trade events can
        // detect inside-spread prints for telemetry. The earlier
        // suppression behavior (HibachiL1SanityChecker) was removed
        // 2026-05-27: per Hibachi, both L1 and trades pulse on the same
        // 250ms heartbeat but snapshot at different points within the
        // window, so trade-inside-spread is structural and rejecting L1
        // updates on it was froze the cache without value.
        if (bid != null && ask != null) {
            FeedConsistencyState state = feedConsistency.computeIfAbsent(
                    ticker.getSymbol(), k -> new FeedConsistencyState());
            state.lastBid = bid;
            state.lastAsk = ask;
        }
        fireLevel1Quote(quote);
    }

    protected void onMarkPriceUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "timestamp_ms"));
        boolean any = false;
        BigDecimal mark = firstDecimal(data, "markPrice", "price");
        if (mark != null) { quote.addQuote(QuoteType.MARK_PRICE, mark); any = true; }
        if (any) {
            fireLevel1Quote(quote);
        }
    }

    protected void onOrderBookUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        // Hibachi orderbook frames carry messageType=Snapshot for full state
        // and messageType=Update for incremental level changes. On Update
        // frames a level with quantity=0 means "remove". Without honoring
        // this distinction the cached book gets reset to whatever 1-2
        // levels were in the latest delta, producing the 84→86 best-ask
        // flicker and OBI imbalance jumping to ±100.
        String messageType = message.path("messageType").asText("");
        boolean isSnapshot = "Snapshot".equalsIgnoreCase(messageType);
        BookState state = bookBySymbol.computeIfAbsent(ticker.getSymbol(), k -> new BookState());
        synchronized (state) {
            if (isSnapshot) {
                state.bids.clear();
                state.asks.clear();
            }
            applyLevelDeltas(state.bids, data.path("bid").path("levels"));
            applyLevelDeltas(state.asks, data.path("ask").path("levels"));
            // live_book is a BOUNDED 10-level window per side: Update frames
            // only carry changes WITHIN [startPrice,endPrice] and never send a
            // qty=0 removal for levels that scroll out of the window. Without
            // trimming, those stale out-of-window levels persist in the
            // TreeMap and, once price moves, become the "best" on their side —
            // crossing the book by 100+ bps. Prune each side to the window the
            // frame reports so only live levels remain. (Standard orderbook
            // feed is unaffected — gated on live_book.)
            if (config.isMarketDataLiveBook()) {
                pruneToWindow(state.bids, data.path("bid"));
                pruneToWindow(state.asks, data.path("ask"));
            }
            // Snapshots without any levels (rare but seen on reconnect) leave
            // both sides empty — skip the publish so we don't push a zero book
            // downstream that would crater OBI / midpoint.
            if (state.bids.isEmpty() && state.asks.isEmpty()) {
                return;
            }
            OrderBook orderBook = new OrderBook(ticker, ticker.getMinimumTickSize());
            ZonedDateTime timestamp = toTimestamp(message, "timestamp_ms");
            orderBook.updateFromSnapshot(toLevels(state.bids), toLevels(state.asks), timestamp);
            fireMarketDepthQuote(new Level2Quote(ticker, orderBook, timestamp));
            // live_book mode: the book IS the fast feed (~5ms), so derive
            // top-of-book L1 from it rather than lagging on the ~300ms
            // ask_bid_price topic. No-op when live_book is off (standard
            // orderbook feed) → L1 keeps coming solely from ask_bid_price.
            if (config.isMarketDataLiveBook()) {
                emitBookDerivedL1(ticker, state, timestamp);
            }
        }
    }

    /**
     * Derive a top-of-book L1 quote from the current order book (live_book
     * mode). Fires only when the touch (price or size, either side) actually
     * changes, so a 5ms book that mostly republishes the same top doesn't
     * spam L1 at 200Hz. Caller holds the {@link BookState} lock.
     */
    private void emitBookDerivedL1(Ticker ticker, BookState state, ZonedDateTime timestamp) {
        BigDecimal bid = state.bids.isEmpty() ? null : state.bids.firstKey();
        BigDecimal ask = state.asks.isEmpty() ? null : state.asks.firstKey();
        if (bid == null && ask == null) {
            return;
        }
        // Safety net: never publish a crossed/locked top-of-book as L1. The
        // window-prune above should keep the book uncrossed, but if a stale
        // level ever slips through, holding the last good L1 beats pushing a
        // bid >= ask downstream (which craters fair value / OBI / quoting).
        if (bid != null && ask != null && bid.compareTo(ask) >= 0) {
            return;
        }
        BigDecimal bidSize = bid != null ? state.bids.get(bid) : null;
        BigDecimal askSize = ask != null ? state.asks.get(ask) : null;
        if (java.util.Objects.equals(bid, state.lastEmitBid)
                && java.util.Objects.equals(ask, state.lastEmitAsk)
                && java.util.Objects.equals(bidSize, state.lastEmitBidSize)
                && java.util.Objects.equals(askSize, state.lastEmitAskSize)) {
            return;
        }
        state.lastEmitBid = bid;
        state.lastEmitBidSize = bidSize;
        state.lastEmitAsk = ask;
        state.lastEmitAskSize = askSize;
        Level1Quote quote = new Level1Quote(ticker, timestamp);
        if (bid != null) { quote.addQuote(QuoteType.BID, bid); }
        if (bidSize != null) { quote.addQuote(QuoteType.BID_SIZE, bidSize); }
        if (ask != null) { quote.addQuote(QuoteType.ASK, ask); }
        if (askSize != null) { quote.addQuote(QuoteType.ASK_SIZE, askSize); }
        // Keep the trade-inside-book telemetry anchored on the fresh top too.
        if (bid != null && ask != null) {
            FeedConsistencyState fc = feedConsistency.computeIfAbsent(
                    ticker.getSymbol(), k -> new FeedConsistencyState());
            fc.lastBid = bid;
            fc.lastAsk = ask;
        }
        fireLevel1Quote(quote);
    }

    /**
     * Trim a side to the [startPrice, endPrice] window the live_book frame
     * reports. live_book keeps only {@code depth} levels per side and does NOT
     * emit qty=0 removals for levels that scroll out of that window, so the
     * accumulated TreeMap must be pruned to the live window each frame —
     * otherwise stale far-side levels eventually cross the book. No-op if the
     * frame doesn't carry usable window bounds (e.g. empty side on reconnect).
     */
    private static void pruneToWindow(java.util.TreeMap<BigDecimal, BigDecimal> side, JsonNode sideNode) {
        pruneOutsideWindow(side, parseDecimal(sideNode.path("startPrice")),
                parseDecimal(sideNode.path("endPrice")));
    }

    /** Remove levels outside [min(start,end), max(start,end)]; no-op on null bounds. Package-private for test. */
    static void pruneOutsideWindow(java.util.TreeMap<BigDecimal, BigDecimal> side, BigDecimal start, BigDecimal end) {
        if (side == null || side.isEmpty() || start == null || end == null) {
            return;
        }
        BigDecimal lo = start.min(end);
        BigDecimal hi = start.max(end);
        side.keySet().removeIf(price -> price.compareTo(lo) < 0 || price.compareTo(hi) > 0);
    }

    private static void applyLevelDeltas(java.util.Map<BigDecimal, BigDecimal> side, JsonNode levels) {
        if (levels == null || !levels.isArray()) {
            return;
        }
        for (JsonNode lvl : levels) {
            BigDecimal price = parseDecimal(lvl.path("price"));
            BigDecimal qty = parseDecimal(lvl.path("quantity"));
            if (price == null || qty == null) {
                continue;
            }
            if (qty.signum() <= 0) {
                side.remove(price);
            } else {
                side.put(price, qty);
            }
        }
    }

    private static List<OrderBook.PriceLevel> toLevels(java.util.Map<BigDecimal, BigDecimal> side) {
        List<OrderBook.PriceLevel> out = new ArrayList<>(side.size());
        for (java.util.Map.Entry<BigDecimal, BigDecimal> e : side.entrySet()) {
            out.add(new OrderBook.PriceLevel(e.getKey(), e.getValue().doubleValue()));
        }
        return out;
    }

    private static BigDecimal parseDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected void onTradesUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        // Hibachi may wrap a single trade as {"trade":{...}} or batch as {"trades":[...]}
        JsonNode singleTrade = data.path("trade");
        if (singleTrade.isObject()) {
            emitTrade(ticker, singleTrade);
            return;
        }
        JsonNode trades = data.path("trades");
        if (trades.isArray()) {
            for (JsonNode trade : trades) {
                emitTrade(ticker, trade);
            }
            return;
        }
        emitTrade(ticker, data);
    }

    protected void emitTrade(Ticker ticker, JsonNode trade) {
        BigDecimal price = decimal(trade, "price");
        BigDecimal size = firstDecimal(trade, "quantity", "size");
        if (price == null || size == null) {
            logger.info("Hibachi trade missing fields: price={} size={} raw={}", price, size, trade);
            return;
        }
        String sideStr = firstString(trade, "takerSide", "side").toUpperCase();
        OrderFlow.Side side = "ASK".equals(sideStr) || "SELL".equals(sideStr)
                ? OrderFlow.Side.SELL : OrderFlow.Side.BUY;
        ZonedDateTime ts = toTimestamp(trade, "timestamp_ms");
        OrderFlow flow = new OrderFlow(ticker, price, size, side, ts);
        fireOrderFlow(flow);

        // Trade-vs-book mismatch telemetry. Count trades whose price lands
        // strictly inside the most recent cached top-of-book on the same WS
        // connection. Under Hibachi's 250ms pulsed architecture these are
        // structural (trade and book streams snapshot at different points
        // in the same window), not a stale-feed bug — but we still log the
        // rate so we can keep the evidence current for Hibachi support and
        // monitor for sudden changes.
        FeedConsistencyState fcState = feedConsistency.get(ticker.getSymbol());
        if (fcState != null) {
            BigDecimal lastBid = fcState.lastBid;
            BigDecimal lastAsk = fcState.lastAsk;
            if (lastBid != null && lastAsk != null
                    && price.compareTo(lastBid) > 0
                    && price.compareTo(lastAsk) < 0) {
                long count = fcState.mismatchCount.incrementAndGet();
                long now = System.currentTimeMillis();
                if (now - fcState.lastLogMs >= MISMATCH_LOG_THROTTLE_MS) {
                    fcState.lastLogMs = now;
                    logger.info(
                            "Hibachi trade-inside-book for {}: trade {} inside cached [{}, {}] (cumulative_mismatches={})",
                            ticker.getSymbol(), price.toPlainString(),
                            lastBid.toPlainString(), lastAsk.toPlainString(), count);
                }
            }
        }

        Level1Quote lastQuote = new Level1Quote(ticker, ts);
        lastQuote.addQuote(QuoteType.LAST, price);
        lastQuote.addQuote(QuoteType.LAST_SIZE, size);
        fireLevel1Quote(lastQuote);
    }

    protected void onFundingRateUpdate(Ticker ticker, JsonNode message) {
        JsonNode data = message.path("data");
        BigDecimal rate = decimal(data, "estimatedFundingRate");
        if (rate == null) {
            rate = decimal(data.path("fundingRateEstimation"), "estimatedFundingRate");
        }
        if (rate == null) {
            logger.info("Hibachi funding rate missing estimatedFundingRate: {}", data);
            return;
        }
        int fundingInterval = ticker.getFundingRateInterval();
        if (fundingInterval <= 0) {
            fundingInterval = 8;
        }
        BigDecimal hourlyRate = rate.divide(BigDecimal.valueOf(fundingInterval), MC);
        BigDecimal annualizedPercent = hourlyRate.multiply(HOURS_PER_YEAR).multiply(PERCENT_MULTIPLIER, MC);
        BigDecimal hourlyBps = hourlyRate.multiply(BPS_MULTIPLIER, MC);
        Level1Quote quote = new Level1Quote(ticker, toTimestamp(message, "timestamp_ms"));
        quote.addQuote(QuoteType.FUNDING_RATE_APR, annualizedPercent);
        quote.addQuote(QuoteType.FUNDING_RATE_HOURLY_BPS, hourlyBps);
        fireLevel1Quote(quote);
    }

    protected synchronized void startVolumePolling(Ticker ticker) {
        String symbol = ticker.getSymbol();
        if (!volumePollingSymbols.add(symbol)) {
            return;
        }
        if (volumeScheduler == null) {
            volumeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "hibachi-volume-poll");
                t.setDaemon(true);
                return t;
            });
            volumeTask = volumeScheduler.scheduleAtFixedRate(
                    this::pollVolume, 0, 1, TimeUnit.SECONDS);
        }
    }

    protected void pollVolume() {
        for (String symbol : volumePollingSymbols) {
            try {
                JsonNode stats = restApi.getMarketStats(symbol);
                if (stats == null || stats.isMissingNode()) {
                    continue;
                }
                Ticker ticker = lookupTicker(symbol);
                if (ticker == null) {
                    continue;
                }
                Level1Quote quote = new Level1Quote(ticker, ZonedDateTime.now(UTC));
                boolean any = false;
                BigDecimal volume = decimal(stats, "volume24h");
                if (volume != null) { quote.addQuote(QuoteType.VOLUME, volume); any = true; }
                BigDecimal volumeNotional = decimal(stats, "volumeNotional24h");
                if (volumeNotional != null) { quote.addQuote(QuoteType.VOLUME_NOTIONAL, volumeNotional); any = true; }
                if (any) {
                    fireLevel1Quote(quote);
                }
            } catch (Exception e) {
                logger.debug("Failed to poll volume for {}", symbol, e);
            }
        }
    }

    protected Ticker lookupTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        Ticker t = tickerRegistry.lookupByBrokerSymbol(com.fueledbychai.data.InstrumentType.PERPETUAL_FUTURES, symbol);
        if (t == null) {
            t = tickerRegistry.lookupByCommonSymbol(com.fueledbychai.data.InstrumentType.PERPETUAL_FUTURES, symbol);
        }
        return t;
    }

    protected BigDecimal topPrice(JsonNode root, String side) {
        JsonNode levels = root.path(side);
        if (!levels.isArray() || levels.size() == 0) {
            return null;
        }
        JsonNode first = levels.get(0);
        if (!first.isArray() || first.size() == 0) {
            return null;
        }
        try {
            return new BigDecimal(first.get(0).asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected List<OrderBook.PriceLevel> parseLevels(JsonNode arr) {
        List<OrderBook.PriceLevel> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode lvl : arr) {
            try {
                BigDecimal price;
                double size;
                if (lvl.isArray() && lvl.size() >= 2) {
                    price = new BigDecimal(lvl.get(0).asText());
                    size = Double.parseDouble(lvl.get(1).asText());
                } else if (lvl.isObject()) {
                    price = new BigDecimal(lvl.path("price").asText());
                    size = Double.parseDouble(lvl.path("quantity").asText());
                } else {
                    continue;
                }
                out.add(new OrderBook.PriceLevel(price, size));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    protected ZonedDateTime toTimestamp(JsonNode message, String field) {
        if (message == null) {
            return ZonedDateTime.now(UTC);
        }
        long epoch = message.path(field).asLong(0L);
        if (epoch <= 0L) {
            return ZonedDateTime.now(UTC);
        }
        // Heuristic: values < 1e12 are seconds, else millis.
        long millis = epoch < 1_000_000_000_000L ? epoch * 1000L : epoch;
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), UTC);
    }

    protected BigDecimal decimal(JsonNode message, String field) {
        if (message == null) return null;
        String raw = message.path(field).asText("");
        if (raw.isBlank()) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected BigDecimal firstDecimal(JsonNode message, String... fields) {
        for (String f : fields) {
            BigDecimal v = decimal(message, f);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    protected String firstString(JsonNode message, String... fields) {
        if (message == null) return "";
        for (String f : fields) {
            String v = message.path(f).asText("");
            if (!v.isBlank()) return v;
        }
        return "";
    }

    protected void requireTicker(Ticker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
    }

    private static final class SubKey {
        final String symbol;
        final String topic;

        SubKey(String symbol, String topic) {
            this.symbol = symbol;
            this.topic = topic;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SubKey)) return false;
            SubKey k = (SubKey) o;
            return symbol.equals(k.symbol) && topic.equals(k.topic);
        }

        @Override
        public int hashCode() {
            return 31 * symbol.hashCode() + topic.hashCode();
        }
    }

    // unused but kept for spi clarity
    @SuppressWarnings("unused")
    private static final Set<String> SUPPORTED_TOPICS = Set.copyOf(new HashSet<>(List.of(
            HibachiTopicRouter.TOPIC_ASK_BID_PRICE,
            HibachiTopicRouter.TOPIC_MARK_PRICE,
            HibachiTopicRouter.TOPIC_ORDERBOOK,
            HibachiTopicRouter.TOPIC_TRADES)));
}
