package com.fueledbychai.bybit.common.api;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fueledbychai.bybit.common.api.ws.listener.IBybitOrderBookListener;
import com.fueledbychai.bybit.common.api.ws.listener.IBybitTickerListener;
import com.fueledbychai.bybit.common.api.ws.listener.IBybitTradeListener;
import com.fueledbychai.bybit.common.api.ws.model.BybitOrderBookLevel;
import com.fueledbychai.bybit.common.api.ws.model.BybitOrderBookUpdate;
import com.fueledbychai.bybit.common.api.ws.model.BybitTickerUpdate;
import com.fueledbychai.bybit.common.api.ws.model.BybitTrade;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.websocket.ProxyAwareWebSocketClient;

/**
 * Shared public websocket API for Bybit market data streams.
 */
public class BybitWebSocketApi implements IBybitWebSocketApi {

    protected static final Logger logger = LoggerFactory.getLogger(BybitWebSocketApi.class);

    protected static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10L);
    protected static final long RECONNECT_BASE_DELAY_MILLIS = 1_000L;
    protected static final long RECONNECT_MAX_DELAY_MILLIS = 30_000L;
    protected static final long DEFAULT_PING_INTERVAL_SECONDS = 20L;
    protected static final int MAX_BOOK_LEVELS = 200;
    protected static final Pattern OPTION_EXCHANGE_SYMBOL_PATTERN = Pattern
            .compile("^[A-Z0-9]+-\\d{1,2}[A-Z]{3}\\d{2}-[0-9.]+-[CP](?:-[A-Z0-9]+)?$");
    protected static final Pattern OPTION_COMMON_SYMBOL_PATTERN = Pattern
            .compile("^[A-Z0-9]+/[A-Z0-9]+-\\d{8}-[0-9.]+-[CP]$");

    protected final BybitConfiguration configuration;

    protected final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(
            daemonThreadFactory("bybit-ws-reconnect"));
    protected final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
            daemonThreadFactory("bybit-ws-heartbeat"));

    protected final Map<BybitWsCategory, ConnectionState> connections = new EnumMap<>(BybitWsCategory.class);

    protected final Map<String, CopyOnWriteArrayList<IBybitTickerListener>> tickerListeners = new ConcurrentHashMap<>();
    protected final Map<String, CopyOnWriteArrayList<IBybitOrderBookListener>> orderBookListeners = new ConcurrentHashMap<>();
    protected final Map<String, CopyOnWriteArrayList<IBybitTradeListener>> tradeListeners = new ConcurrentHashMap<>();

    protected final Map<String, LocalOrderBookState> orderBookStates = new ConcurrentHashMap<>();

    protected volatile ScheduledFuture<?> heartbeatFuture;
    protected volatile boolean manualDisconnect = true;

    public BybitWebSocketApi(BybitConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration is required");
        }
        this.configuration = configuration;

        for (BybitWsCategory category : BybitWsCategory.values()) {
            String url = configuration.getWebSocketUrl(category);
            connections.put(category, new ConnectionState(category, URI.create(url)));
        }
    }

    @Override
    public void connect() {
        manualDisconnect = false;
        for (ConnectionState state : connections.values()) {
            ensureConnected(state);
        }
    }

    @Override
    public void subscribeTicker(String instrumentId, InstrumentType instrumentType, IBybitTickerListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        String symbol = normalizeInstrumentId(instrumentId);
        tickerListeners.computeIfAbsent(symbol, key -> new CopyOnWriteArrayList<>()).add(listener);

        BybitWsCategory category = resolveCategory(symbol, instrumentType);
        requestTopic(category, "tickers." + symbol);
    }

    @Override
    public void subscribeOrderBook(String instrumentId, InstrumentType instrumentType, IBybitOrderBookListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        String symbol = normalizeInstrumentId(instrumentId);
        orderBookListeners.computeIfAbsent(symbol, key -> new CopyOnWriteArrayList<>()).add(listener);

        BybitWsCategory category = resolveCategory(symbol, instrumentType);
        int depth = category == BybitWsCategory.OPTION ? 25 : 50;
        requestTopic(category, "orderbook." + depth + "." + symbol);
    }

    @Override
    public void subscribeTrades(String instrumentId, InstrumentType instrumentType, IBybitTradeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        String symbol = normalizeInstrumentId(instrumentId);
        tradeListeners.computeIfAbsent(symbol, key -> new CopyOnWriteArrayList<>()).add(listener);

        BybitWsCategory category = resolveCategory(symbol, instrumentType);
        String tradeTopicSymbol = category == BybitWsCategory.OPTION ? extractBaseAsset(symbol) : symbol;
        requestTopic(category, "publicTrade." + tradeTopicSymbol);
    }

    protected void requestTopic(BybitWsCategory category, String topic) {
        ConnectionState state = connections.get(category);
        if (state == null || topic == null || topic.isBlank()) {
            return;
        }

        state.requestedTopics.add(topic);
        manualDisconnect = false;
        ensureConnected(state);
        sendSubscribe(state, List.of(topic));
    }

    @Override
    public void disconnectAll() {
        manualDisconnect = true;
        tickerListeners.clear();
        orderBookListeners.clear();
        tradeListeners.clear();
        orderBookStates.clear();
        cancelHeartbeat();

        for (ConnectionState state : connections.values()) {
            synchronized (state.connectLock) {
                state.connected = false;
                state.activeTopics.clear();
                state.requestedTopics.clear();

                if (state.reconnectFuture != null) {
                    state.reconnectFuture.cancel(false);
                    state.reconnectFuture = null;
                }

                CompletableFuture<BybitSocketClient> pendingConnect = state.connectFuture;
                state.connectFuture = null;
                if (pendingConnect != null && !pendingConnect.isDone()) {
                    pendingConnect.cancel(true);
                }

                BybitSocketClient socket = state.webSocket;
                state.webSocket = null;
                if (socket != null) {
                    try {
                        socket.close(1000, "shutdown");
                    } catch (Exception e) {
                        logger.debug("Ignoring Bybit websocket close failure category={}", state.category, e);
                    }
                }
            }
        }
    }

    protected String normalizeInstrumentId(String instrumentId) {
        if (instrumentId == null || instrumentId.isBlank()) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        return instrumentId.trim().toUpperCase(Locale.US);
    }

    protected BybitWsCategory resolveCategory(String symbol, InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.CRYPTO_SPOT) {
            return BybitWsCategory.SPOT;
        }
        if (instrumentType == InstrumentType.OPTION) {
            return BybitWsCategory.OPTION;
        }

        String normalized = symbol == null ? "" : symbol.toUpperCase(Locale.US);
        if (looksLikeOptionSymbol(normalized)) {
            return BybitWsCategory.OPTION;
        }
        if (normalized.contains("USDT") || normalized.contains("USDC")) {
            return BybitWsCategory.LINEAR;
        }
        if (normalized.matches("^[A-Z0-9]+USD[A-Z0-9]*$")) {
            return BybitWsCategory.INVERSE;
        }
        if (normalized.contains("-")) {
            return BybitWsCategory.LINEAR;
        }
        return BybitWsCategory.LINEAR;
    }

    protected boolean looksLikeOptionSymbol(String symbol) {
        if (!isPresent(symbol)) {
            return false;
        }
        return OPTION_EXCHANGE_SYMBOL_PATTERN.matcher(symbol).matches()
                || OPTION_COMMON_SYMBOL_PATTERN.matcher(symbol).matches();
    }

    protected String extractBaseAsset(String symbol) {
        if (symbol == null) {
            return "";
        }

        String normalized = symbol.trim().toUpperCase(Locale.US);
        if (normalized.contains("-")) {
            return normalized.split("-")[0];
        }

        for (String suffix : List.of("USDT", "USDC", "USD", "BTC", "ETH")) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }

        return normalized;
    }

    protected void ensureConnected(ConnectionState state) {
        if (state == null || manualDisconnect) {
            return;
        }

        synchronized (state.connectLock) {
            if (state.connected) {
                return;
            }
            if (state.connectFuture != null && !state.connectFuture.isDone()) {
                return;
            }

            state.connectFuture = new CompletableFuture<>();
            try {
                BybitSocketClient socket = new BybitSocketClient(state);
                state.webSocket = socket;
                socket.connect();
            } catch (RuntimeException e) {
                logger.warn("Failed to start Bybit websocket connect category={}", state.category, e);
                state.connectFuture = null;
                state.connected = false;
                state.webSocket = null;
                scheduleReconnect(state);
            }
        }
    }

    protected void scheduleReconnect(ConnectionState state) {
        if (state == null || manualDisconnect) {
            return;
        }

        synchronized (state.connectLock) {
            if (state.reconnectFuture != null && !state.reconnectFuture.isDone()) {
                return;
            }

            int attempts = state.reconnectAttempts.getAndIncrement();
            long multiplier = 1L << Math.min(attempts, 5);
            long delayMillis = Math.min(RECONNECT_BASE_DELAY_MILLIS * multiplier, RECONNECT_MAX_DELAY_MILLIS);

            state.reconnectFuture = reconnectExecutor.schedule(() -> {
                synchronized (state.connectLock) {
                    state.reconnectFuture = null;
                    state.connectFuture = null;
                }
                if (!manualDisconnect) {
                    ensureConnected(state);
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    protected void sendSubscribe(ConnectionState state, List<String> requestedTopics) {
        if (state == null || requestedTopics == null || requestedTopics.isEmpty() || !state.connected
                || state.webSocket == null) {
            return;
        }

        JsonArray args = new JsonArray();
        for (String topic : requestedTopics) {
            if (!isPresent(topic) || state.activeTopics.contains(topic)) {
                continue;
            }
            if (!state.requestedTopics.contains(topic)) {
                continue;
            }
            args.add(topic);
        }

        if (args.isEmpty()) {
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("op", "subscribe");
        request.add("args", args);

        try {
            state.webSocket.send(request.toString());
            for (JsonElement element : args) {
                state.activeTopics.add(element.getAsString());
            }
        } catch (RuntimeException e) {
            logger.warn("Bybit subscribe failed category={} payload={}", state.category, request, e);
            state.activeTopics.removeIf(topic -> args.contains(new com.google.gson.JsonPrimitive(topic)));
        }
    }

    protected void onConnected(ConnectionState state, BybitSocketClient socket) {
        state.webSocket = socket;
        state.connected = true;
        state.reconnectAttempts.set(0);

        synchronized (state.connectLock) {
            if (state.reconnectFuture != null) {
                state.reconnectFuture.cancel(false);
                state.reconnectFuture = null;
            }
            if (state.connectFuture != null && !state.connectFuture.isDone()) {
                state.connectFuture.complete(socket);
            }
        }

        startHeartbeat();
        sendSubscribe(state, new ArrayList<>(state.requestedTopics));
    }

    protected void onDisconnected(ConnectionState state, Throwable error) {
        if (state == null) {
            return;
        }

        synchronized (state.connectLock) {
            state.connected = false;
            state.webSocket = null;
            if (state.connectFuture != null && !state.connectFuture.isDone() && error != null) {
                state.connectFuture.completeExceptionally(error);
            }
            state.connectFuture = null;
            state.activeTopics.clear();
        }

        if (error != null) {
            logger.debug("Bybit websocket disconnected category={}", state.category, error);
        }

        if (!manualDisconnect) {
            scheduleReconnect(state);
        }
    }

    protected void startHeartbeat() {
        synchronized (connections) {
            if (heartbeatFuture != null && !heartbeatFuture.isDone()) {
                return;
            }

            long intervalSeconds = Long.getLong("bybit.ws.ping.interval.seconds", DEFAULT_PING_INTERVAL_SECONDS);
            if (intervalSeconds <= 0) {
                intervalSeconds = DEFAULT_PING_INTERVAL_SECONDS;
            }

            heartbeatFuture = heartbeatExecutor.scheduleWithFixedDelay(this::sendHeartbeats, intervalSeconds,
                    intervalSeconds, TimeUnit.SECONDS);
        }
    }

    protected void sendHeartbeats() {
        for (ConnectionState state : connections.values()) {
            if (!state.connected || state.webSocket == null) {
                continue;
            }
            try {
                JsonObject ping = new JsonObject();
                ping.addProperty("op", "ping");
                state.webSocket.send(ping.toString());
            } catch (Exception e) {
                logger.debug("Failed to send Bybit ping category={}", state.category, e);
            }
        }
    }

    protected void cancelHeartbeat() {
        synchronized (connections) {
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
                heartbeatFuture = null;
            }
        }
    }

    protected void handleMessage(ConnectionState state, String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        JsonObject payload;
        try {
            payload = JsonParser.parseString(message).getAsJsonObject();
        } catch (RuntimeException e) {
            logger.warn("Ignoring malformed Bybit websocket message category={} payload={}", state.category, message,
                    e);
            return;
        }

        String topic = getString(payload, "topic");
        if (!isPresent(topic)) {
            return;
        }

        JsonElement dataElement = payload.get("data");
        Long rootTimestamp = getLong(payload, "ts");
        String updateType = getString(payload, "type");

        if (topic.startsWith("tickers.")) {
            dispatchTicker(state.category, topic, dataElement, rootTimestamp);
            return;
        }
        if (topic.startsWith("orderbook.")) {
            dispatchOrderBook(topic, updateType, dataElement, rootTimestamp);
            return;
        }
        if (topic.startsWith("publicTrade.")) {
            dispatchTrades(topic, dataElement);
        }
    }

    protected void dispatchTicker(BybitWsCategory category, String topic, JsonElement dataElement, Long rootTimestamp) {
        if (dataElement == null || !dataElement.isJsonObject()) {
            return;
        }

        JsonObject data = dataElement.getAsJsonObject();
        String symbol = normalizeInstrumentIdOrNull(firstNonBlank(getString(data, "symbol"), getString(data, "s"),
                extractSymbolFromTopic(topic, "tickers.")));
        if (!isPresent(symbol)) {
            return;
        }

        List<IBybitTickerListener> listeners = tickerListeners.get(symbol);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        BybitTickerUpdate update = parseTickerUpdate(category, symbol, data, rootTimestamp);
        if (update == null) {
            return;
        }

        for (IBybitTickerListener listener : listeners) {
            try {
                listener.onTicker(update);
            } catch (RuntimeException e) {
                logger.warn("Bybit ticker listener failed for {}", symbol, e);
            }
        }
    }

    protected void dispatchOrderBook(String topic, String updateType, JsonElement dataElement, Long rootTimestamp) {
        if (dataElement == null || !dataElement.isJsonObject()) {
            return;
        }

        JsonObject data = dataElement.getAsJsonObject();
        String symbol = normalizeInstrumentIdOrNull(firstNonBlank(getString(data, "s"),
                extractSymbolFromTopic(topic, "orderbook.")));
        if (!isPresent(symbol)) {
            return;
        }

        List<IBybitOrderBookListener> listeners = orderBookListeners.get(symbol);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        JsonArray bidsJson = getJsonArray(data, "b");
        JsonArray asksJson = getJsonArray(data, "a");

        List<BybitOrderBookLevel> bidLevels = parseBookLevels(bidsJson);
        List<BybitOrderBookLevel> askLevels = parseBookLevels(asksJson);

        boolean snapshot = "snapshot".equalsIgnoreCase(updateType) || !orderBookStates.containsKey(symbol);
        LocalOrderBookState localState = orderBookStates.computeIfAbsent(symbol, key -> new LocalOrderBookState());
        localState.apply(snapshot, bidLevels, askLevels);

        Long updateTs = firstNonNull(getLong(data, "ts"), getLong(data, "cts"), rootTimestamp);
        BybitOrderBookUpdate update = localState.toUpdate(symbol, updateTs, MAX_BOOK_LEVELS);

        for (IBybitOrderBookListener listener : listeners) {
            try {
                listener.onOrderBook(update);
            } catch (RuntimeException e) {
                logger.warn("Bybit order-book listener failed for {}", symbol, e);
            }
        }
    }

    protected void dispatchTrades(String topic, JsonElement dataElement) {
        if (dataElement == null || !dataElement.isJsonArray()) {
            return;
        }

        JsonArray data = dataElement.getAsJsonArray();
        if (data.isEmpty()) {
            return;
        }

        String topicSymbol = normalizeInstrumentIdOrNull(extractSymbolFromTopic(topic, "publicTrade."));
        Map<String, List<BybitTrade>> tradesBySymbol = new ConcurrentHashMap<>();

        for (JsonElement element : data) {
            if (!element.isJsonObject()) {
                continue;
            }

            BybitTrade trade = parseTrade(topicSymbol, element.getAsJsonObject());
            if (trade == null || !isPresent(trade.getInstrumentId())) {
                continue;
            }

            tradesBySymbol.computeIfAbsent(trade.getInstrumentId(), key -> new ArrayList<>()).add(trade);
        }

        for (Map.Entry<String, List<BybitTrade>> entry : tradesBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<IBybitTradeListener> listeners = tradeListeners.get(symbol);
            if (listeners == null || listeners.isEmpty()) {
                continue;
            }

            for (IBybitTradeListener listener : listeners) {
                try {
                    listener.onTrades(symbol, entry.getValue());
                } catch (RuntimeException e) {
                    logger.warn("Bybit trade listener failed for {}", symbol, e);
                }
            }
        }
    }

    protected BybitTickerUpdate parseTickerUpdate(BybitWsCategory category, String symbol, JsonObject data,
            Long rootTimestamp) {
        if (data == null) {
            return null;
        }

        return new BybitTickerUpdate(symbol, category == null ? null : category.name().toLowerCase(Locale.US),
                rootTimestamp, firstBigDecimal(data, "bid1Price", "bidPrice", "bestBidPrice"),
                firstBigDecimal(data, "bid1Size", "bidSize", "bestBidSize"),
                firstBigDecimal(data, "ask1Price", "askPrice", "bestAskPrice"),
                firstBigDecimal(data, "ask1Size", "askSize", "bestAskSize"), getBigDecimal(data, "lastPrice"),
                firstBigDecimal(data, "lastQty", "lastSize"), getBigDecimal(data, "markPrice"),
                getBigDecimal(data, "indexPrice"), getBigDecimal(data, "underlyingPrice"),
                getBigDecimal(data, "openInterest"), getBigDecimal(data, "volume24h"),
                getBigDecimal(data, "turnover24h"), getBigDecimal(data, "fundingRate"),
                firstBigDecimal(data, "bid1Iv", "bidIv"), firstBigDecimal(data, "ask1Iv", "askIv"),
                getBigDecimal(data, "markIv"), getBigDecimal(data, "delta"), getBigDecimal(data, "gamma"),
                getBigDecimal(data, "theta"), getBigDecimal(data, "vega"));
    }

    protected BigDecimal firstBigDecimal(JsonObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            BigDecimal value = getBigDecimal(object, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected List<BybitOrderBookLevel> parseBookLevels(JsonArray levelsJson) {
        List<BybitOrderBookLevel> levels = new ArrayList<>();
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
            if (price == null || size == null) {
                continue;
            }
            levels.add(new BybitOrderBookLevel(price, size));
        }

        return levels;
    }

    protected BybitTrade parseTrade(String topicSymbol, JsonObject data) {
        if (data == null) {
            return null;
        }

        String symbol = normalizeInstrumentIdOrNull(firstNonBlank(getString(data, "s"), topicSymbol));
        BigDecimal price = getBigDecimal(data, "p");
        BigDecimal size = getBigDecimal(data, "v");
        if (!isPresent(symbol) || price == null || size == null) {
            return null;
        }

        return new BybitTrade(symbol, getString(data, "i"), getString(data, "S"), price, size, getLong(data, "T"),
                getBigDecimal(data, "mP"), getBigDecimal(data, "iP"), getBigDecimal(data, "mIv"),
                getBigDecimal(data, "iv"));
    }

    protected String extractSymbolFromTopic(String topic, String prefix) {
        if (!isPresent(topic) || !isPresent(prefix) || !topic.startsWith(prefix)) {
            return null;
        }
        if ("orderbook.".equals(prefix)) {
            String[] parts = topic.split("\\.");
            if (parts.length >= 3) {
                return parts[2];
            }
            return null;
        }
        return topic.substring(prefix.length());
    }

    protected String normalizeInstrumentIdOrNull(String instrumentId) {
        if (!isPresent(instrumentId)) {
            return null;
        }
        return instrumentId.trim().toUpperCase(Locale.US);
    }

    protected BigDecimal asBigDecimal(JsonArray array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonElement element = array.get(index);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        if (!isPresent(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected JsonArray getJsonArray(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(key);
        return element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    protected String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
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
        if (!isPresent(value)) {
            return null;
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
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
        if (!isPresent(value)) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (isPresent(value)) {
                return value;
            }
        }
        return null;
    }

    protected Long firstNonNull(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    protected static ThreadFactory daemonThreadFactory(String threadName) {
        return runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
    }

    protected class BybitSocketClient extends ProxyAwareWebSocketClient {
        protected final ConnectionState state;

        protected BybitSocketClient(ConnectionState state) {
            super(state.uri);
            this.state = state;
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            onConnected(state, this);
        }

        @Override
        public void onMessage(String message) {
            handleMessage(state, message);
        }

        @Override
        public void onClose(int statusCode, String reason, boolean remote) {
            onDisconnected(state, null);
        }

        @Override
        public void onError(Exception ex) {
            onDisconnected(state, ex);
        }
    }

    protected static class ConnectionState {
        protected final BybitWsCategory category;
        protected final URI uri;
        protected final Object connectLock = new Object();

        protected volatile BybitSocketClient webSocket;
        protected volatile CompletableFuture<BybitSocketClient> connectFuture;
        protected volatile ScheduledFuture<?> reconnectFuture;
        protected volatile boolean connected;

        protected final AtomicInteger reconnectAttempts = new AtomicInteger();
        protected final Set<String> requestedTopics = ConcurrentHashMap.newKeySet();
        protected final Set<String> activeTopics = ConcurrentHashMap.newKeySet();

        protected ConnectionState(BybitWsCategory category, URI uri) {
            this.category = category;
            this.uri = uri;
        }
    }

    protected static class LocalOrderBookState {
        protected final Map<BigDecimal, BigDecimal> bids = new ConcurrentHashMap<>();
        protected final Map<BigDecimal, BigDecimal> asks = new ConcurrentHashMap<>();

        protected synchronized void apply(boolean snapshot, List<BybitOrderBookLevel> newBids,
                List<BybitOrderBookLevel> newAsks) {
            if (snapshot) {
                bids.clear();
                asks.clear();
            }

            applySide(bids, newBids);
            applySide(asks, newAsks);
        }

        protected void applySide(Map<BigDecimal, BigDecimal> side, List<BybitOrderBookLevel> updates) {
            if (updates == null) {
                return;
            }
            for (BybitOrderBookLevel level : updates) {
                if (level == null || level.getPrice() == null || level.getSize() == null) {
                    continue;
                }
                if (level.getSize().signum() <= 0) {
                    side.remove(level.getPrice());
                    continue;
                }
                side.put(level.getPrice(), level.getSize());
            }
        }

        protected synchronized BybitOrderBookUpdate toUpdate(String symbol, Long timestamp, int maxDepth) {
            List<BybitOrderBookLevel> bidLevels = bids.entrySet().stream()
                    .sorted(Map.Entry.<BigDecimal, BigDecimal>comparingByKey(Comparator.reverseOrder()))
                    .limit(maxDepth)
                    .map(entry -> new BybitOrderBookLevel(entry.getKey(), entry.getValue()))
                    .toList();

            List<BybitOrderBookLevel> askLevels = asks.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .limit(maxDepth)
                    .map(entry -> new BybitOrderBookLevel(entry.getKey(), entry.getValue()))
                    .toList();

            return new BybitOrderBookUpdate(symbol, timestamp, bidLevels, askLevels);
        }
    }
}
