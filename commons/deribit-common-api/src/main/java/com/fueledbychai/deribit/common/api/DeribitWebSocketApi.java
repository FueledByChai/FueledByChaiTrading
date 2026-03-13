package com.fueledbychai.deribit.common.api;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fueledbychai.deribit.common.api.ws.listener.IDeribitOrderBookListener;
import com.fueledbychai.deribit.common.api.ws.listener.IDeribitTickerListener;
import com.fueledbychai.deribit.common.api.ws.listener.IDeribitTradeListener;
import com.fueledbychai.deribit.common.api.ws.model.DeribitBookLevel;
import com.fueledbychai.deribit.common.api.ws.model.DeribitBookUpdate;
import com.fueledbychai.deribit.common.api.ws.model.DeribitTickerUpdate;
import com.fueledbychai.deribit.common.api.ws.model.DeribitTrade;
import com.fueledbychai.websocket.ProxyAwareWebSocketClient;

/**
 * Shared public websocket API for Deribit market data streams.
 */
public class DeribitWebSocketApi implements IDeribitWebSocketApi {

    protected static final Logger logger = LoggerFactory.getLogger(DeribitWebSocketApi.class);
    protected static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10L);
    protected static final long RECONNECT_BASE_DELAY_MILLIS = 1_000L;
    protected static final long RECONNECT_MAX_DELAY_MILLIS = 30_000L;
    protected static final int DEFAULT_SUBSCRIBE_BATCH_SIZE = 100;
    protected static final long DEFAULT_SUBSCRIBE_SEND_INTERVAL_MILLIS = 350L;
    protected static final int DEFAULT_MAX_SUBSCRIBE_RETRIES = 3;

    protected final String webSocketUrl;
    protected final URI webSocketUri;
    protected final AtomicLong requestId = new AtomicLong(1L);
    protected final AtomicInteger reconnectAttempts = new AtomicInteger();
    protected final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "deribit-ws-reconnect");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    protected final ScheduledExecutorService subscribeExecutor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "deribit-ws-subscribe");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    protected final Map<String, CopyOnWriteArrayList<IDeribitTickerListener>> tickerListeners = new ConcurrentHashMap<>();
    protected final Map<String, CopyOnWriteArrayList<IDeribitOrderBookListener>> orderBookListeners = new ConcurrentHashMap<>();
    protected final Map<String, CopyOnWriteArrayList<IDeribitTradeListener>> tradeListeners = new ConcurrentHashMap<>();
    protected final Set<String> requestedChannels = ConcurrentHashMap.newKeySet();
    protected final Set<String> activeChannels = ConcurrentHashMap.newKeySet();
    protected final Set<String> inFlightChannels = ConcurrentHashMap.newKeySet();
    protected final Set<String> queuedChannels = ConcurrentHashMap.newKeySet();
    protected final ConcurrentLinkedQueue<String> subscribeQueue = new ConcurrentLinkedQueue<>();
    protected final Map<Long, List<String>> pendingSubscribeRequests = new ConcurrentHashMap<>();
    protected final Map<String, Integer> subscribeRetryCounts = new ConcurrentHashMap<>();
    protected final Object connectLock = new Object();

    protected volatile DeribitSocketClient webSocket;
    protected volatile CompletableFuture<DeribitSocketClient> connectFuture;
    protected volatile ScheduledFuture<?> reconnectFuture;
    protected volatile ScheduledFuture<?> subscribeFuture;
    protected volatile boolean connected;
    protected volatile boolean manualDisconnect = true;

    public DeribitWebSocketApi(String webSocketUrl) {
        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            throw new IllegalArgumentException("webSocketUrl is required");
        }
        this.webSocketUrl = webSocketUrl.trim();
        this.webSocketUri = URI.create(this.webSocketUrl);
    }

    @Override
    public void connect() {
        manualDisconnect = false;
        ensureConnected();
    }

    @Override
    public void subscribeTicker(String instrumentName, IDeribitTickerListener listener) {
        String normalized = validateInstrumentName(instrumentName);
        subscribe(listener, tickerListeners, "ticker." + normalized + ".100ms");
    }

    @Override
    public void subscribeOrderBook(String instrumentName, IDeribitOrderBookListener listener) {
        String normalized = validateInstrumentName(instrumentName);
        subscribe(listener, orderBookListeners, "book." + normalized + ".100ms");
    }

    @Override
    public void subscribeTrades(String instrumentName, IDeribitTradeListener listener) {
        String normalized = validateInstrumentName(instrumentName);
        subscribe(listener, tradeListeners, "trades." + normalized + ".100ms");
    }

    @Override
    public void disconnectAll() {
        manualDisconnect = true;
        connected = false;
        activeChannels.clear();
        requestedChannels.clear();
        tickerListeners.clear();
        orderBookListeners.clear();
        tradeListeners.clear();
        clearSubscribeState();
        cancelReconnect();
        cancelSubscribeDrain();

        CompletableFuture<DeribitSocketClient> pendingConnect = connectFuture;
        connectFuture = null;
        if (pendingConnect != null && !pendingConnect.isDone()) {
            pendingConnect.cancel(true);
        }

        DeribitSocketClient current = webSocket;
        webSocket = null;
        if (current != null) {
            try {
                current.close(1000, "shutdown");
            } catch (Exception e) {
                logger.debug("Ignoring Deribit websocket close failure", e);
            }
        }
    }

    protected <T> void subscribe(T listener, Map<String, CopyOnWriteArrayList<T>> listenerMap, String channel) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        listenerMap.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(listener);
        requestedChannels.add(channel);
        manualDisconnect = false;
        ensureConnected();
        queueSubscribeChannel(channel);
    }

    protected String validateInstrumentName(String instrumentName) {
        if (instrumentName == null || instrumentName.isBlank()) {
            throw new IllegalArgumentException("instrumentName is required");
        }
        return instrumentName.trim().toUpperCase();
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

            connectFuture = new CompletableFuture<>();
            try {
                webSocket = new DeribitSocketClient();
                webSocket.connect();
            } catch (RuntimeException e) {
                logger.warn("Failed to start Deribit websocket connection", e);
                webSocket = null;
                connectFuture = null;
                scheduleReconnect();
            }
        }
    }

    protected void queueSubscribeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        if (activeChannels.contains(channel) || inFlightChannels.contains(channel)) {
            return;
        }
        if (!queuedChannels.add(channel)) {
            return;
        }
        subscribeQueue.offer(channel);
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

    protected void cancelSubscribeDrain() {
        synchronized (connectLock) {
            if (subscribeFuture != null) {
                subscribeFuture.cancel(false);
                subscribeFuture = null;
            }
        }
    }

    protected void clearSubscribeState() {
        activeChannels.clear();
        inFlightChannels.clear();
        queuedChannels.clear();
        subscribeQueue.clear();
        pendingSubscribeRequests.clear();
        subscribeRetryCounts.clear();
    }

    protected void drainSubscribeQueue() {
        if (!canSend()) {
            return;
        }

        List<String> channels = new ArrayList<>();
        int maxChannels = getSubscribeBatchSize();
        while (channels.size() < maxChannels) {
            String channel = subscribeQueue.poll();
            if (channel == null) {
                break;
            }
            queuedChannels.remove(channel);
            if (activeChannels.contains(channel) || inFlightChannels.contains(channel) || !requestedChannels.contains(channel)) {
                continue;
            }
            channels.add(channel);
        }

        if (channels.isEmpty()) {
            if (subscribeQueue.isEmpty() && inFlightChannels.isEmpty()) {
                cancelSubscribeDrain();
            }
            return;
        }
        sendSubscribeBatch(channels);
    }

    protected void sendSubscribeBatch(List<String> channels) {
        if (!canSend() || channels == null || channels.isEmpty()) {
            return;
        }
        List<String> sanitized = new ArrayList<>();
        for (String channel : channels) {
            if (channel == null || channel.isBlank() || activeChannels.contains(channel) || inFlightChannels.contains(channel)) {
                continue;
            }
            sanitized.add(channel);
        }
        if (sanitized.isEmpty()) {
            return;
        }
        inFlightChannels.addAll(sanitized);

        long id = requestId.getAndIncrement();
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", id);
        request.addProperty("method", "public/subscribe");

        JsonObject params = new JsonObject();
        JsonArray channelArray = new JsonArray();
        for (String channel : sanitized) {
            channelArray.add(channel);
        }
        params.add("channels", channelArray);
        request.add("params", params);

        pendingSubscribeRequests.put(id, sanitized);
        try {
            sendText(request.toString());
        } catch (RuntimeException e) {
            pendingSubscribeRequests.remove(id);
            for (String channel : sanitized) {
                inFlightChannels.remove(channel);
                queueSubscribeWithRetry(channel, "send failure", e);
            }
        }
    }

    protected void sendText(String payload) {
        webSocket.send(payload);
    }

    protected boolean canSend() {
        return connected && webSocket != null;
    }

    protected int getSubscribeBatchSize() {
        int configured = Integer.getInteger("deribit.ws.subscribe.batch.size", DEFAULT_SUBSCRIBE_BATCH_SIZE);
        return configured > 0 ? configured : DEFAULT_SUBSCRIBE_BATCH_SIZE;
    }

    protected long getSubscribeSendIntervalMillis() {
        long configured = Long.getLong("deribit.ws.subscribe.interval.ms", DEFAULT_SUBSCRIBE_SEND_INTERVAL_MILLIS);
        return configured > 0 ? configured : DEFAULT_SUBSCRIBE_SEND_INTERVAL_MILLIS;
    }

    protected int getMaxSubscribeRetries() {
        int configured = Integer.getInteger("deribit.ws.subscribe.max.retries", DEFAULT_MAX_SUBSCRIBE_RETRIES);
        return configured >= 0 ? configured : DEFAULT_MAX_SUBSCRIBE_RETRIES;
    }

    protected void resubscribeAll() {
        clearSubscribeState();
        for (String channel : requestedChannels) {
            queueSubscribeChannel(channel);
        }
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

    protected void handleMessage(String message) {
        JsonObject payload;
        try {
            payload = JsonParser.parseString(message).getAsJsonObject();
        } catch (RuntimeException e) {
            logger.warn("Ignoring malformed Deribit websocket message: {}", message, e);
            return;
        }

        if (isRpcResponse(payload)) {
            handleRpcResponse(payload);
            return;
        }

        if (!payload.has("method") || payload.get("method").isJsonNull()) {
            return;
        }
        if (!"subscription".equals(payload.get("method").getAsString())) {
            return;
        }

        JsonObject params = payload.getAsJsonObject("params");
        if (params == null) {
            return;
        }

        String channel = getString(params, "channel");
        JsonElement data = params.get("data");
        if (channel == null || data == null || data.isJsonNull()) {
            return;
        }

        if (channel.startsWith("ticker.")) {
            dispatchTicker(channel, data);
            return;
        }
        if (channel.startsWith("book.")) {
            dispatchOrderBook(channel, data);
            return;
        }
        if (channel.startsWith("trades.")) {
            dispatchTrades(channel, data);
        }
    }

    protected boolean isRpcResponse(JsonObject payload) {
        return payload.has("id") && (payload.has("result") || payload.has("error"));
    }

    protected void handleRpcResponse(JsonObject payload) {
        Long id = getLong(payload, "id");
        if (id == null) {
            return;
        }

        List<String> channels = pendingSubscribeRequests.remove(id);
        if (channels == null || channels.isEmpty()) {
            return;
        }

        if (payload.has("error") && payload.get("error").isJsonObject()) {
            JsonObject error = payload.getAsJsonObject("error");
            String code = getString(error, "code");
            String message = getString(error, "message");
            logger.warn("Deribit subscribe failed id={} code={} message={} channels={} sample={}", id, code, message,
                    channels.size(), abbreviateChannels(channels));

            for (String channel : channels) {
                inFlightChannels.remove(channel);
                activeChannels.remove(channel);
                queueSubscribeWithRetry(channel, "rpc_error " + code + " " + message, null);
            }
            return;
        }

        for (String channel : channels) {
            inFlightChannels.remove(channel);
            activeChannels.add(channel);
            subscribeRetryCounts.remove(channel);
        }
    }

    protected String abbreviateChannels(Collection<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return "[]";
        }
        int maxSamples = 6;
        List<String> sample = new ArrayList<>(maxSamples);
        int count = 0;
        for (String channel : channels) {
            if (count++ >= maxSamples) {
                break;
            }
            sample.add(channel);
        }
        if (channels.size() > maxSamples) {
            return sample + " ...";
        }
        return sample.toString();
    }

    protected void queueSubscribeWithRetry(String channel, String reason, Throwable error) {
        if (channel == null || channel.isBlank() || !requestedChannels.contains(channel)) {
            return;
        }
        int attempts = subscribeRetryCounts.merge(channel, 1, Integer::sum);
        if (attempts > getMaxSubscribeRetries()) {
            logger.error("Giving up Deribit subscribe for {} after {} attempts. reason={}", channel, attempts, reason,
                    error);
            return;
        }
        logger.warn("Retrying Deribit subscribe for {} attempt {} reason={}", channel, attempts, reason, error);
        queueSubscribeChannel(channel);
    }

    protected void dispatchTicker(String channel, JsonElement data) {
        if (!data.isJsonObject()) {
            return;
        }
        DeribitTickerUpdate update = parseTickerUpdate(data.getAsJsonObject());
        if (update == null) {
            return;
        }
        List<IDeribitTickerListener> listeners = tickerListeners.get(channel);
        if (listeners == null) {
            return;
        }
        for (IDeribitTickerListener listener : listeners) {
            try {
                listener.onTicker(update);
            } catch (RuntimeException e) {
                logger.warn("Deribit ticker listener failed for {}", channel, e);
            }
        }
    }

    protected void dispatchOrderBook(String channel, JsonElement data) {
        if (!data.isJsonObject()) {
            return;
        }
        DeribitBookUpdate update = parseBookUpdate(data.getAsJsonObject());
        if (update == null) {
            return;
        }
        List<IDeribitOrderBookListener> listeners = orderBookListeners.get(channel);
        if (listeners == null) {
            return;
        }
        for (IDeribitOrderBookListener listener : listeners) {
            try {
                listener.onOrderBook(update);
            } catch (RuntimeException e) {
                logger.warn("Deribit order book listener failed for {}", channel, e);
            }
        }
    }

    protected void dispatchTrades(String channel, JsonElement data) {
        List<DeribitTrade> trades = parseTrades(data);
        if (trades.isEmpty()) {
            return;
        }
        String instrumentName = trades.get(0).getInstrumentName();
        List<IDeribitTradeListener> listeners = tradeListeners.get(channel);
        if (listeners == null) {
            return;
        }
        for (IDeribitTradeListener listener : listeners) {
            try {
                listener.onTrades(instrumentName, trades);
            } catch (RuntimeException e) {
                logger.warn("Deribit trade listener failed for {}", channel, e);
            }
        }
    }

    protected DeribitTickerUpdate parseTickerUpdate(JsonObject data) {
        if (data == null) {
            return null;
        }

        JsonObject stats = data.has("stats") && data.get("stats").isJsonObject() ? data.getAsJsonObject("stats") : null;
        JsonObject greeks = data.has("greeks") && data.get("greeks").isJsonObject() ? data.getAsJsonObject("greeks")
                : null;

        return new DeribitTickerUpdate(getString(data, "instrument_name"), getLong(data, "timestamp"),
                getBigDecimal(data, "best_bid_price"), getBigDecimal(data, "best_bid_amount"),
                getBigDecimal(data, "best_ask_price"), getBigDecimal(data, "best_ask_amount"),
                getBigDecimal(data, "last_price"), getBigDecimal(data, "mark_price"),
                getBigDecimal(data, "open_interest"), getBigDecimal(stats, "volume"),
                getBigDecimal(stats, "volume_usd"), getBigDecimal(data, "underlying_price"),
                getBigDecimal(data, "current_funding"), getBigDecimal(data, "bid_iv"), getBigDecimal(data, "ask_iv"),
                getBigDecimal(data, "mark_iv"), getBigDecimal(greeks, "delta"), getBigDecimal(greeks, "gamma"),
                getBigDecimal(greeks, "theta"), getBigDecimal(greeks, "vega"), getBigDecimal(greeks, "rho"),
                getBigDecimal(data, "interest_rate"));
    }

    protected DeribitBookUpdate parseBookUpdate(JsonObject data) {
        if (data == null) {
            return null;
        }
        List<DeribitBookLevel> bids = parseBookLevels(data.get("bids"));
        List<DeribitBookLevel> asks = parseBookLevels(data.get("asks"));
        return new DeribitBookUpdate(getString(data, "instrument_name"), getString(data, "type"),
                getLong(data, "change_id"), getLong(data, "timestamp"), bids, asks);
    }

    protected List<DeribitBookLevel> parseBookLevels(JsonElement levelsElement) {
        if (levelsElement == null || levelsElement.isJsonNull() || !levelsElement.isJsonArray()) {
            return Collections.emptyList();
        }
        List<DeribitBookLevel> levels = new ArrayList<>();
        for (JsonElement levelElement : levelsElement.getAsJsonArray()) {
            if (!levelElement.isJsonArray()) {
                continue;
            }
            JsonArray levelArray = levelElement.getAsJsonArray();
            if (levelArray.size() < 2) {
                continue;
            }

            String action = "new";
            int priceIndex = 0;
            int amountIndex = 1;
            if (levelArray.get(0).isJsonPrimitive() && !levelArray.get(0).getAsJsonPrimitive().isNumber()) {
                action = levelArray.get(0).getAsString();
                priceIndex = 1;
                amountIndex = 2;
            }
            if (levelArray.size() <= amountIndex) {
                continue;
            }

            BigDecimal price = asBigDecimal(levelArray.get(priceIndex));
            BigDecimal amount = asBigDecimal(levelArray.get(amountIndex));
            if (price == null) {
                continue;
            }
            levels.add(new DeribitBookLevel(action, price, amount));
        }
        return levels;
    }

    protected List<DeribitTrade> parseTrades(JsonElement data) {
        if (data == null || data.isJsonNull() || !data.isJsonArray()) {
            return Collections.emptyList();
        }

        List<DeribitTrade> trades = new ArrayList<>();
        for (JsonElement tradeElement : data.getAsJsonArray()) {
            if (!tradeElement.isJsonObject()) {
                continue;
            }
            JsonObject trade = tradeElement.getAsJsonObject();
            trades.add(new DeribitTrade(getString(trade, "instrument_name"), getString(trade, "direction"),
                    getBigDecimal(trade, "price"), getBigDecimal(trade, "amount"), getLong(trade, "timestamp")));
        }
        return trades;
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
        return asBigDecimal(object.get(key));
    }

    protected Long getLong(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsLong();
    }

    protected BigDecimal asBigDecimal(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return new BigDecimal(element.getAsString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    protected class DeribitSocketClient extends ProxyAwareWebSocketClient {

        protected DeribitSocketClient() {
            super(webSocketUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            DeribitWebSocketApi.this.webSocket = this;
            connected = true;
            synchronized (connectLock) {
                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.complete(this);
                }
                connectFuture = null;
            }
            cancelReconnect();
            resubscribeAll();
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int statusCode, String reason, boolean remote) {
            connected = false;
            clearSubscribeState();
            DeribitWebSocketApi.this.webSocket = null;
            synchronized (connectLock) {
                connectFuture = null;
            }
            if (!manualDisconnect) {
                scheduleReconnect();
            }
        }

        @Override
        public void onError(Exception ex) {
            connected = false;
            clearSubscribeState();
            DeribitWebSocketApi.this.webSocket = null;
            synchronized (connectLock) {
                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.completeExceptionally(ex);
                }
                connectFuture = null;
            }
            logger.warn("Deribit websocket error", ex);
            if (!manualDisconnect) {
                scheduleReconnect();
            }
        }
    }
}
