package com.fueledbychai.okx.common.api;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fueledbychai.okx.common.api.ws.listener.IOkxFundingRateListener;
import com.fueledbychai.okx.common.api.ws.listener.IOkxOrderBookListener;
import com.fueledbychai.okx.common.api.ws.listener.IOkxTickerListener;
import com.fueledbychai.okx.common.api.ws.listener.IOkxTradeListener;
import com.fueledbychai.okx.common.api.ws.model.OkxFundingRateUpdate;
import com.fueledbychai.okx.common.api.ws.model.OkxOrderBookLevel;
import com.fueledbychai.okx.common.api.ws.model.OkxOrderBookUpdate;
import com.fueledbychai.okx.common.api.ws.model.OkxTickerUpdate;
import com.fueledbychai.okx.common.api.ws.model.OkxTrade;

/**
 * Shared public websocket API for OKX market data streams.
 */
public class OkxWebSocketApi implements IOkxWebSocketApi {

    protected static final Logger logger = LoggerFactory.getLogger(OkxWebSocketApi.class);
    protected static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10L);
    protected static final long RECONNECT_BASE_DELAY_MILLIS = 1_000L;
    protected static final long RECONNECT_MAX_DELAY_MILLIS = 30_000L;
    protected static final int DEFAULT_SUBSCRIBE_BATCH_SIZE = 20;
    protected static final long DEFAULT_SUBSCRIBE_SEND_INTERVAL_MILLIS = 200L;
    protected static final int DEFAULT_MAX_SUBSCRIBE_RETRIES = 3;
    protected static final long DEFAULT_PING_INTERVAL_SECONDS = 20L;

    protected final String webSocketUrl;
    protected final HttpClient httpClient;
    protected final URI webSocketUri;
    protected final AtomicInteger reconnectAttempts = new AtomicInteger();
    protected final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(
            daemonThreadFactory("okx-ws-reconnect"));
    protected final ScheduledExecutorService subscribeExecutor = Executors.newSingleThreadScheduledExecutor(
            daemonThreadFactory("okx-ws-subscribe"));
    protected final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
            daemonThreadFactory("okx-ws-heartbeat"));

    protected final Map<String, CopyOnWriteArrayList<IOkxTickerListener>> tickerListeners = new ConcurrentHashMap<>();
    protected final Map<String, CopyOnWriteArrayList<IOkxFundingRateListener>> fundingRateListeners = new ConcurrentHashMap<>();
    protected final Map<String, CopyOnWriteArrayList<IOkxOrderBookListener>> orderBookListeners = new ConcurrentHashMap<>();
    protected final Map<String, CopyOnWriteArrayList<IOkxTradeListener>> tradeListeners = new ConcurrentHashMap<>();

    protected final Set<SubscriptionArg> requestedSubscriptions = ConcurrentHashMap.newKeySet();
    protected final Set<SubscriptionArg> activeSubscriptions = ConcurrentHashMap.newKeySet();
    protected final Set<SubscriptionArg> queuedSubscriptions = ConcurrentHashMap.newKeySet();
    protected final ConcurrentLinkedQueue<SubscriptionArg> subscribeQueue = new ConcurrentLinkedQueue<>();
    protected final Map<SubscriptionArg, Integer> subscribeRetryCounts = new ConcurrentHashMap<>();

    protected final Object connectLock = new Object();

    protected volatile WebSocket webSocket;
    protected volatile CompletableFuture<WebSocket> connectFuture;
    protected volatile ScheduledFuture<?> reconnectFuture;
    protected volatile ScheduledFuture<?> subscribeFuture;
    protected volatile ScheduledFuture<?> heartbeatFuture;
    protected volatile boolean connected;
    protected volatile boolean manualDisconnect = true;

    public OkxWebSocketApi(String webSocketUrl) {
        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            throw new IllegalArgumentException("webSocketUrl is required");
        }
        this.webSocketUrl = webSocketUrl.trim();
        this.httpClient = createHttpClient();
        this.webSocketUri = URI.create(this.webSocketUrl);
    }

    @Override
    public void connect() {
        manualDisconnect = false;
        ensureConnected();
    }

    @Override
    public void subscribeTicker(String instrumentId, IOkxTickerListener listener) {
        String normalizedInstrumentId = normalizeInstrumentId(instrumentId);
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        tickerListeners.computeIfAbsent(normalizedInstrumentId, key -> new CopyOnWriteArrayList<>()).add(listener);
        subscribe("tickers", normalizedInstrumentId);
    }

    @Override
    public void subscribeFundingRate(String instrumentId, IOkxFundingRateListener listener) {
        String normalizedInstrumentId = normalizeInstrumentId(instrumentId);
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        fundingRateListeners.computeIfAbsent(normalizedInstrumentId, key -> new CopyOnWriteArrayList<>()).add(listener);
        subscribe("funding-rate", normalizedInstrumentId);
    }

    @Override
    public void subscribeOrderBook(String instrumentId, IOkxOrderBookListener listener) {
        String normalizedInstrumentId = normalizeInstrumentId(instrumentId);
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        orderBookListeners.computeIfAbsent(normalizedInstrumentId, key -> new CopyOnWriteArrayList<>()).add(listener);
        subscribe("books5", normalizedInstrumentId);
    }

    @Override
    public void subscribeTrades(String instrumentId, IOkxTradeListener listener) {
        String normalizedInstrumentId = normalizeInstrumentId(instrumentId);
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        tradeListeners.computeIfAbsent(normalizedInstrumentId, key -> new CopyOnWriteArrayList<>()).add(listener);
        subscribe("trades", normalizedInstrumentId);
    }

    @Override
    public void disconnectAll() {
        manualDisconnect = true;
        connected = false;
        tickerListeners.clear();
        fundingRateListeners.clear();
        orderBookListeners.clear();
        tradeListeners.clear();
        requestedSubscriptions.clear();
        clearSubscriptionState();
        cancelReconnect();
        cancelSubscribeDrain();
        cancelHeartbeat();

        CompletableFuture<WebSocket> pendingConnect = connectFuture;
        connectFuture = null;
        if (pendingConnect != null && !pendingConnect.isDone()) {
            pendingConnect.cancel(true);
        }

        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception e) {
                logger.debug("Ignoring OKX websocket close failure", e);
            }
        }
    }

    protected HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    protected static ThreadFactory daemonThreadFactory(String threadName) {
        return runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
    }

    protected String normalizeInstrumentId(String instrumentId) {
        if (instrumentId == null || instrumentId.isBlank()) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        return instrumentId.trim().toUpperCase(Locale.US);
    }

    protected void subscribe(String channel, String instrumentId) {
        SubscriptionArg arg = new SubscriptionArg(channel, instrumentId);
        requestedSubscriptions.add(arg);
        manualDisconnect = false;
        ensureConnected();
        queueSubscribe(arg);
    }

    protected void ensureConnected() {
        if (connected) {
            return;
        }
        synchronized (connectLock) {
            if (connected) {
                return;
            }
            if (connectFuture != null && !connectFuture.isDone()) {
                return;
            }

            connectFuture = httpClient.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(webSocketUri, new OkxListener());
            connectFuture.whenComplete((socket, error) -> {
                if (error != null) {
                    logger.warn("Failed to connect OKX websocket", error);
                    synchronized (connectLock) {
                        connectFuture = null;
                    }
                    scheduleReconnect();
                }
            });
        }
    }

    protected void queueSubscribe(SubscriptionArg arg) {
        if (arg == null || activeSubscriptions.contains(arg)) {
            return;
        }
        if (!queuedSubscriptions.add(arg)) {
            return;
        }
        subscribeQueue.offer(arg);
        ensureSubscribeDrainScheduled();
    }

    protected void ensureSubscribeDrainScheduled() {
        synchronized (connectLock) {
            if (subscribeFuture != null && !subscribeFuture.isDone()) {
                return;
            }
            subscribeFuture = subscribeExecutor.scheduleWithFixedDelay(this::drainSubscribeQueue, 0L,
                    getSubscribeSendIntervalMillis(), TimeUnit.MILLISECONDS);
        }
    }

    protected void drainSubscribeQueue() {
        if (!canSend()) {
            return;
        }

        List<SubscriptionArg> batch = new ArrayList<>();
        int batchSize = getSubscribeBatchSize();
        while (batch.size() < batchSize) {
            SubscriptionArg next = subscribeQueue.poll();
            if (next == null) {
                break;
            }
            queuedSubscriptions.remove(next);
            if (!requestedSubscriptions.contains(next) || activeSubscriptions.contains(next)) {
                continue;
            }
            batch.add(next);
        }

        if (batch.isEmpty()) {
            if (subscribeQueue.isEmpty()) {
                cancelSubscribeDrain();
            }
            return;
        }

        sendSubscribeBatch(batch);
    }

    protected void sendSubscribeBatch(List<SubscriptionArg> batch) {
        if (!canSend() || batch == null || batch.isEmpty()) {
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("op", "subscribe");
        JsonArray args = new JsonArray();
        for (SubscriptionArg arg : batch) {
            if (arg == null || activeSubscriptions.contains(arg)) {
                continue;
            }
            JsonObject argJson = new JsonObject();
            argJson.addProperty("channel", arg.channel);
            argJson.addProperty("instId", arg.instrumentId);
            args.add(argJson);
        }

        if (args.isEmpty()) {
            return;
        }
        request.add("args", args);

        try {
            sendText(request.toString());
            for (JsonElement element : args) {
                JsonObject argJson = element.getAsJsonObject();
                SubscriptionArg sentArg = new SubscriptionArg(getString(argJson, "channel"),
                        getString(argJson, "instId"));
                activeSubscriptions.add(sentArg);
                subscribeRetryCounts.remove(sentArg);
            }
        } catch (RuntimeException e) {
            for (JsonElement element : args) {
                JsonObject argJson = element.getAsJsonObject();
                SubscriptionArg failedArg = new SubscriptionArg(getString(argJson, "channel"),
                        getString(argJson, "instId"));
                queueSubscribeWithRetry(failedArg, "send failure", e);
            }
        }
    }

    protected void sendText(String payload) {
        WebSocket socket = webSocket;
        if (socket == null) {
            throw new IllegalStateException("websocket is not connected");
        }
        socket.sendText(payload, true);
    }

    protected boolean canSend() {
        return connected && webSocket != null;
    }

    protected int getSubscribeBatchSize() {
        int configured = Integer.getInteger("okx.ws.subscribe.batch.size", DEFAULT_SUBSCRIBE_BATCH_SIZE);
        return configured > 0 ? configured : DEFAULT_SUBSCRIBE_BATCH_SIZE;
    }

    protected long getSubscribeSendIntervalMillis() {
        long configured = Long.getLong("okx.ws.subscribe.interval.ms", DEFAULT_SUBSCRIBE_SEND_INTERVAL_MILLIS);
        return configured > 0 ? configured : DEFAULT_SUBSCRIBE_SEND_INTERVAL_MILLIS;
    }

    protected int getMaxSubscribeRetries() {
        int configured = Integer.getInteger("okx.ws.subscribe.max.retries", DEFAULT_MAX_SUBSCRIBE_RETRIES);
        return configured >= 0 ? configured : DEFAULT_MAX_SUBSCRIBE_RETRIES;
    }

    protected long getPingIntervalSeconds() {
        long configured = Long.getLong("okx.ws.ping.interval.seconds", DEFAULT_PING_INTERVAL_SECONDS);
        return configured > 0 ? configured : DEFAULT_PING_INTERVAL_SECONDS;
    }

    protected void clearSubscriptionState() {
        activeSubscriptions.clear();
        queuedSubscriptions.clear();
        subscribeQueue.clear();
        subscribeRetryCounts.clear();
    }

    protected void scheduleReconnect() {
        if (manualDisconnect) {
            return;
        }
        synchronized (connectLock) {
            if (reconnectFuture != null && !reconnectFuture.isDone()) {
                return;
            }

            int attempts = reconnectAttempts.getAndIncrement();
            long multiplier = 1L << Math.min(attempts, 5);
            long delayMillis = Math.min(RECONNECT_BASE_DELAY_MILLIS * multiplier, RECONNECT_MAX_DELAY_MILLIS);

            reconnectFuture = reconnectExecutor.schedule(() -> {
                synchronized (connectLock) {
                    reconnectFuture = null;
                    connectFuture = null;
                }
                if (!manualDisconnect) {
                    ensureConnected();
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    protected void cancelReconnect() {
        ScheduledFuture<?> current = reconnectFuture;
        if (current != null) {
            current.cancel(false);
        }
        reconnectFuture = null;
        reconnectAttempts.set(0);
    }

    protected void cancelSubscribeDrain() {
        synchronized (connectLock) {
            if (subscribeFuture != null) {
                subscribeFuture.cancel(false);
                subscribeFuture = null;
            }
        }
    }

    protected void startHeartbeat() {
        synchronized (connectLock) {
            cancelHeartbeat();
            long intervalSeconds = getPingIntervalSeconds();
            heartbeatFuture = heartbeatExecutor.scheduleWithFixedDelay(() -> {
                if (!canSend()) {
                    return;
                }
                try {
                    sendText("ping");
                } catch (Exception e) {
                    logger.debug("Failed to send OKX websocket ping", e);
                }
            }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        }
    }

    protected void cancelHeartbeat() {
        synchronized (connectLock) {
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
                heartbeatFuture = null;
            }
        }
    }

    protected void queueSubscribeWithRetry(SubscriptionArg arg, String reason, Throwable error) {
        if (arg == null || !requestedSubscriptions.contains(arg)) {
            return;
        }

        int attempts = subscribeRetryCounts.merge(arg, 1, Integer::sum);
        if (attempts > getMaxSubscribeRetries()) {
            logger.error("Giving up OKX subscribe for {} after {} attempts reason={}", arg, attempts, reason, error);
            return;
        }
        logger.warn("Retrying OKX subscribe for {} attempt {} reason={}", arg, attempts, reason, error);
        queueSubscribe(arg);
    }

    protected void handleMessage(String message) {
        if (message == null || message.isBlank() || "pong".equalsIgnoreCase(message.trim())) {
            return;
        }

        JsonObject payload;
        try {
            payload = JsonParser.parseString(message).getAsJsonObject();
        } catch (RuntimeException e) {
            logger.warn("Ignoring malformed OKX websocket message: {}", message, e);
            return;
        }

        String event = getString(payload, "event");
        if (event != null) {
            handleEventMessage(event, payload);
            return;
        }

        JsonObject arg = payload.getAsJsonObject("arg");
        JsonArray data = payload.getAsJsonArray("data");
        if (arg == null || data == null) {
            return;
        }

        String channel = getString(arg, "channel");
        String instrumentId = normalizeInstrumentIdOrNull(getString(arg, "instId"));
        if (channel == null || instrumentId == null) {
            return;
        }

        if ("tickers".equalsIgnoreCase(channel)) {
            dispatchTicker(instrumentId, data);
            return;
        }
        if ("funding-rate".equalsIgnoreCase(channel)) {
            dispatchFundingRate(instrumentId, data);
            return;
        }
        if ("books5".equalsIgnoreCase(channel) || "books".equalsIgnoreCase(channel)
                || "books-l2-tbt".equalsIgnoreCase(channel) || "bbo-tbt".equalsIgnoreCase(channel)) {
            dispatchOrderBook(instrumentId, data);
            return;
        }
        if ("trades".equalsIgnoreCase(channel)) {
            dispatchTrades(instrumentId, data);
        }
    }

    protected void handleEventMessage(String event, JsonObject payload) {
        if ("subscribe".equalsIgnoreCase(event)) {
            JsonObject arg = payload.getAsJsonObject("arg");
            SubscriptionArg subscriptionArg = toSubscriptionArg(arg);
            if (subscriptionArg != null) {
                activeSubscriptions.add(subscriptionArg);
                subscribeRetryCounts.remove(subscriptionArg);
            }
            return;
        }

        if (!"error".equalsIgnoreCase(event)) {
            return;
        }

        JsonObject arg = payload.getAsJsonObject("arg");
        SubscriptionArg failed = toSubscriptionArg(arg);
        String code = getString(payload, "code");
        String message = getString(payload, "msg");

        if (failed != null) {
            activeSubscriptions.remove(failed);
            queueSubscribeWithRetry(failed, "event error code=" + code + " msg=" + message, null);
        }

        logger.warn("OKX websocket error event code={} msg={} payload={}", code, message, payload);
    }

    protected SubscriptionArg toSubscriptionArg(JsonObject argJson) {
        if (argJson == null) {
            return null;
        }
        String channel = getString(argJson, "channel");
        String instrumentId = normalizeInstrumentIdOrNull(getString(argJson, "instId"));
        if (channel == null || instrumentId == null) {
            return null;
        }
        return new SubscriptionArg(channel, instrumentId);
    }

    protected void dispatchTicker(String instrumentId, JsonArray data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        List<IOkxTickerListener> listeners = tickerListeners.get(instrumentId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        for (JsonElement element : data) {
            if (!element.isJsonObject()) {
                continue;
            }
            OkxTickerUpdate update = parseTickerUpdate(instrumentId, element.getAsJsonObject());
            if (update == null) {
                continue;
            }
            for (IOkxTickerListener listener : listeners) {
                try {
                    listener.onTicker(update);
                } catch (RuntimeException e) {
                    logger.warn("OKX ticker listener failed for {}", instrumentId, e);
                }
            }
        }
    }

    protected void dispatchFundingRate(String instrumentId, JsonArray data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        List<IOkxFundingRateListener> listeners = fundingRateListeners.get(instrumentId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        for (JsonElement element : data) {
            if (!element.isJsonObject()) {
                continue;
            }
            OkxFundingRateUpdate update = parseFundingRateUpdate(instrumentId, element.getAsJsonObject());
            if (update == null) {
                continue;
            }
            for (IOkxFundingRateListener listener : listeners) {
                try {
                    listener.onFundingRate(update);
                } catch (RuntimeException e) {
                    logger.warn("OKX funding-rate listener failed for {}", instrumentId, e);
                }
            }
        }
    }

    protected void dispatchOrderBook(String instrumentId, JsonArray data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        List<IOkxOrderBookListener> listeners = orderBookListeners.get(instrumentId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        for (JsonElement element : data) {
            if (!element.isJsonObject()) {
                continue;
            }
            OkxOrderBookUpdate update = parseOrderBookUpdate(instrumentId, element.getAsJsonObject());
            if (update == null) {
                continue;
            }
            for (IOkxOrderBookListener listener : listeners) {
                try {
                    listener.onOrderBook(update);
                } catch (RuntimeException e) {
                    logger.warn("OKX order-book listener failed for {}", instrumentId, e);
                }
            }
        }
    }

    protected void dispatchTrades(String instrumentId, JsonArray data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        List<IOkxTradeListener> listeners = tradeListeners.get(instrumentId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        List<OkxTrade> trades = new ArrayList<>();
        for (JsonElement element : data) {
            if (!element.isJsonObject()) {
                continue;
            }
            OkxTrade trade = parseTrade(instrumentId, element.getAsJsonObject());
            if (trade != null) {
                trades.add(trade);
            }
        }
        if (trades.isEmpty()) {
            return;
        }

        for (IOkxTradeListener listener : listeners) {
            try {
                listener.onTrades(instrumentId, trades);
            } catch (RuntimeException e) {
                logger.warn("OKX trade listener failed for {}", instrumentId, e);
            }
        }
    }

    protected OkxTickerUpdate parseTickerUpdate(String fallbackInstrumentId, JsonObject data) {
        if (data == null) {
            return null;
        }

        String instrumentId = normalizeInstrumentIdOrNull(getString(data, "instId"));
        if (instrumentId == null) {
            instrumentId = fallbackInstrumentId;
        }

        return new OkxTickerUpdate(instrumentId, getString(data, "instType"), getLong(data, "ts"),
                getBigDecimal(data, "bidPx"), getBigDecimal(data, "bidSz"), getBigDecimal(data, "askPx"),
                getBigDecimal(data, "askSz"), getBigDecimal(data, "last"), getBigDecimal(data, "lastSz"),
                getBigDecimal(data, "markPx"), firstNonNull(getBigDecimal(data, "oi"), getBigDecimal(data, "openInterest")),
                getBigDecimal(data, "vol24h"), getBigDecimal(data, "volCcy24h"),
                firstNonNull(getBigDecimal(data, "fundingRate"), getBigDecimal(data, "funding_rate")),
                getBigDecimal(data, "delta"), getBigDecimal(data, "gamma"), getBigDecimal(data, "theta"),
                getBigDecimal(data, "vega"));
    }

    protected OkxFundingRateUpdate parseFundingRateUpdate(String fallbackInstrumentId, JsonObject data) {
        if (data == null) {
            return null;
        }

        String instrumentId = normalizeInstrumentIdOrNull(getString(data, "instId"));
        if (instrumentId == null) {
            instrumentId = fallbackInstrumentId;
        }

        BigDecimal fundingRate = firstNonNull(getBigDecimal(data, "fundingRate"), getBigDecimal(data, "funding_rate"));
        BigDecimal nextFundingRate = firstNonNull(getBigDecimal(data, "nextFundingRate"),
                getBigDecimal(data, "next_funding_rate"));
        Long timestamp = firstNonNull(getLong(data, "ts"), getLong(data, "fundingTime"), getLong(data, "funding_time"));
        Long fundingTime = firstNonNull(getLong(data, "fundingTime"), getLong(data, "funding_time"));
        Long nextFundingTime = firstNonNull(getLong(data, "nextFundingTime"), getLong(data, "next_funding_time"));

        if (instrumentId == null || fundingRate == null) {
            return null;
        }

        return new OkxFundingRateUpdate(instrumentId, getString(data, "instType"), timestamp, fundingRate,
                nextFundingRate, fundingTime, nextFundingTime);
    }

    protected OkxOrderBookUpdate parseOrderBookUpdate(String fallbackInstrumentId, JsonObject data) {
        if (data == null) {
            return null;
        }

        String instrumentId = normalizeInstrumentIdOrNull(getString(data, "instId"));
        if (instrumentId == null) {
            instrumentId = fallbackInstrumentId;
        }

        JsonArray bidsJson = data.getAsJsonArray("bids");
        JsonArray asksJson = data.getAsJsonArray("asks");

        List<OkxOrderBookLevel> bids = parseBookLevels(bidsJson);
        List<OkxOrderBookLevel> asks = parseBookLevels(asksJson);
        Long timestamp = getLong(data, "ts");

        return new OkxOrderBookUpdate(instrumentId, timestamp, bids, asks);
    }

    protected List<OkxOrderBookLevel> parseBookLevels(JsonArray levelsJson) {
        List<OkxOrderBookLevel> levels = new ArrayList<>();
        if (levelsJson == null || levelsJson.isEmpty()) {
            return levels;
        }

        for (JsonElement levelElement : levelsJson) {
            if (!levelElement.isJsonArray()) {
                continue;
            }
            JsonArray row = levelElement.getAsJsonArray();
            if (row.size() < 2) {
                continue;
            }

            BigDecimal price = asBigDecimal(row, 0);
            BigDecimal size = asBigDecimal(row, 1);
            Integer orderCount = asInteger(row, 3);
            if (price == null || size == null) {
                continue;
            }
            levels.add(new OkxOrderBookLevel(price, size, orderCount));
        }

        return levels;
    }

    protected OkxTrade parseTrade(String fallbackInstrumentId, JsonObject data) {
        if (data == null) {
            return null;
        }

        String instrumentId = normalizeInstrumentIdOrNull(getString(data, "instId"));
        if (instrumentId == null) {
            instrumentId = fallbackInstrumentId;
        }

        BigDecimal price = getBigDecimal(data, "px");
        BigDecimal size = getBigDecimal(data, "sz");
        if (instrumentId == null || price == null || size == null) {
            return null;
        }

        return new OkxTrade(instrumentId, getString(data, "tradeId"), getString(data, "side"), price, size,
                getLong(data, "ts"));
    }

    protected String normalizeInstrumentIdOrNull(String instrumentId) {
        if (instrumentId == null || instrumentId.isBlank()) {
            return null;
        }
        return instrumentId.trim().toUpperCase(Locale.US);
    }

    protected BigDecimal asBigDecimal(JsonArray array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonElement element = array.get(index);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected Integer asInteger(JsonArray array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonElement element = array.get(index);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    protected BigDecimal getBigDecimal(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }

        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }

        String value = element.getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected Long getLong(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }

        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }

        String value = element.getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    protected Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    protected Long firstNonNull(Long first, Long second, Long third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    protected void handleConnected(WebSocket socket) {
        this.webSocket = socket;
        this.connected = true;
        this.connectFuture = null;
        cancelReconnect();
        startHeartbeat();
        clearSubscriptionState();
        for (SubscriptionArg arg : requestedSubscriptions) {
            queueSubscribe(arg);
        }
    }

    protected void handleDisconnected() {
        this.connected = false;
        this.webSocket = null;
        cancelHeartbeat();

        clearSubscriptionState();
        if (!manualDisconnect) {
            for (SubscriptionArg arg : requestedSubscriptions) {
                queueSubscribe(arg);
            }
            scheduleReconnect();
        }
    }

    protected final class OkxListener implements WebSocket.Listener {

        private final StringBuilder messageBuilder = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            handleConnected(webSocket);
            webSocket.request(1L);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuilder.append(data);
            if (last) {
                String message = messageBuilder.toString();
                messageBuilder.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1L);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.info("OKX websocket closed code={} reason={}", statusCode, reason);
            handleDisconnected();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.warn("OKX websocket error", error);
            handleDisconnected();
        }
    }

    protected static final class SubscriptionArg {
        protected final String channel;
        protected final String instrumentId;

        protected SubscriptionArg(String channel, String instrumentId) {
            this.channel = channel == null ? null : channel.trim();
            this.instrumentId = instrumentId == null ? null : instrumentId.trim().toUpperCase(Locale.US);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel, instrumentId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            SubscriptionArg other = (SubscriptionArg) obj;
            return Objects.equals(channel, other.channel) && Objects.equals(instrumentId, other.instrumentId);
        }

        @Override
        public String toString() {
            return channel + ":" + instrumentId;
        }
    }
}
