package com.fueledbychai.deribit.common.api;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

/**
 * Shared public websocket API for Deribit market data streams.
 */
public class DeribitWebSocketApi implements IDeribitWebSocketApi {

    protected static final Logger logger = LoggerFactory.getLogger(DeribitWebSocketApi.class);
    protected static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10L);
    protected static final long RECONNECT_BASE_DELAY_MILLIS = 1_000L;
    protected static final long RECONNECT_MAX_DELAY_MILLIS = 30_000L;

    protected final String webSocketUrl;
    protected final HttpClient httpClient;
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
    protected final Map<String, CopyOnWriteArrayList<IDeribitTickerListener>> tickerListeners = new ConcurrentHashMap<>();
    protected final Map<String, CopyOnWriteArrayList<IDeribitOrderBookListener>> orderBookListeners = new ConcurrentHashMap<>();
    protected final Map<String, CopyOnWriteArrayList<IDeribitTradeListener>> tradeListeners = new ConcurrentHashMap<>();
    protected final Set<String> requestedChannels = ConcurrentHashMap.newKeySet();
    protected final Set<String> activeChannels = ConcurrentHashMap.newKeySet();
    protected final Object connectLock = new Object();

    protected volatile WebSocket webSocket;
    protected volatile CompletableFuture<WebSocket> connectFuture;
    protected volatile ScheduledFuture<?> reconnectFuture;
    protected volatile boolean connected;
    protected volatile boolean manualDisconnect = true;

    public DeribitWebSocketApi(String webSocketUrl) {
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
        cancelReconnect();

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
                logger.debug("Ignoring Deribit websocket close failure", e);
            }
        }
    }

    protected HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    protected <T> void subscribe(T listener, Map<String, CopyOnWriteArrayList<T>> listenerMap, String channel) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        listenerMap.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(listener);
        requestedChannels.add(channel);
        manualDisconnect = false;
        ensureConnected();
        if (connected) {
            sendSubscribe(channel);
        }
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

            connectFuture = httpClient.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(webSocketUri, new DeribitListener());
            connectFuture.whenComplete((socket, error) -> {
                if (error != null) {
                    logger.warn("Failed to connect Deribit websocket", error);
                    synchronized (connectLock) {
                        connectFuture = null;
                    }
                    scheduleReconnect();
                }
            });
        }
    }

    protected void sendSubscribe(String channel) {
        if (!connected || webSocket == null || !activeChannels.add(channel)) {
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", requestId.getAndIncrement());
        request.addProperty("method", "public/subscribe");

        JsonObject params = new JsonObject();
        JsonArray channels = new JsonArray();
        channels.add(channel);
        params.add("channels", channels);
        request.add("params", params);

        webSocket.sendText(request.toString(), true);
    }

    protected void resubscribeAll() {
        activeChannels.clear();
        for (String channel : requestedChannels) {
            sendSubscribe(channel);
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

    protected class DeribitListener implements WebSocket.Listener {

        protected final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            DeribitWebSocketApi.this.webSocket = webSocket;
            connected = true;
            synchronized (connectLock) {
                connectFuture = null;
            }
            cancelReconnect();
            resubscribeAll();
            webSocket.request(1L);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1L);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            activeChannels.clear();
            DeribitWebSocketApi.this.webSocket = null;
            synchronized (connectLock) {
                connectFuture = null;
            }
            if (!manualDisconnect) {
                scheduleReconnect();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected = false;
            activeChannels.clear();
            DeribitWebSocketApi.this.webSocket = null;
            synchronized (connectLock) {
                connectFuture = null;
            }
            logger.warn("Deribit websocket error", error);
            if (!manualDisconnect) {
                scheduleReconnect();
            }
        }
    }
}
