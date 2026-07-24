package com.fueledbychai.grvt.common.api;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;
import com.fueledbychai.grvt.common.api.ws.GrvtWebSocketClient;

/**
 * Manages the GRVT market-data and trade-data WebSocket connections, including authentication
 * headers for the trade socket, automatic reconnect with subscription replay, and JSON-RPC order
 * entry. Backs both {@code GrvtBroker} (trade data + order RPC) and {@code GrvtQuoteEngine}
 * (market data).
 */
public class GrvtWebSocketApi implements IGrvtWebSocketApi {

    private static final Logger logger = LoggerFactory.getLogger(GrvtWebSocketApi.class);
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final GrvtEnvironment environment;
    private final IGrvtRestApi restApi;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "grvt-ws-reconnect");
        t.setDaemon(true);
        return t;
    });

    private final List<Subscription> marketSubscriptions = new CopyOnWriteArrayList<>();
    private final List<Subscription> tradeSubscriptions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean marketReconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean tradeReconnectScheduled = new AtomicBoolean(false);

    private volatile GrvtWebSocketClient marketClient;
    private volatile GrvtWebSocketClient tradeClient;
    // Channel-local intentional-disconnect flags. The API is cached for the life
    // of the JVM, so neither flag is a terminal object state: connect* starts a
    // fresh session after an in-process application restart.
    private volatile boolean marketDisconnecting = false;
    private volatile boolean tradeDisconnecting = false;

    public GrvtWebSocketApi(GrvtEnvironment environment, IGrvtRestApi restApi) {
        if (environment == null) {
            throw new IllegalArgumentException("environment is required");
        }
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        this.environment = environment;
        this.restApi = restApi;
    }

    @Override
    public synchronized void connectMarketData() {
        marketDisconnecting = false;
        if (isMarketDataConnected()) {
            return;
        }
        GrvtWebSocketClient client = createClient(environment.getMarketWsUrl(), Map.of());
        client.setCloseListener((code, reason) -> onMarketClosed(client, code, reason));
        // Publish the candidate before connectBlocking. If GRVT accepts and then
        // immediately closes the socket, onClose will observe the correct client
        // generation and schedule recovery after this synchronized method exits.
        marketClient = client;
        try {
            connectClient(client, environment.getMarketWsUrl());
            for (Subscription subscription : marketSubscriptions) {
                client.subscribe(subscription.stream, subscription.selector, subscription.listener);
            }
        } catch (RuntimeException e) {
            if (marketClient == client) {
                marketClient = null;
            }
            closeQuietly(client);
            throw e;
        }
    }

    @Override
    public synchronized void connectTradeData() {
        tradeDisconnecting = false;
        if (isTradeDataConnected()) {
            return;
        }
        restApi.refreshCookieIfNeeded();
        GrvtWebSocketClient client = createClient(environment.getTradeWsUrl(), authHeaders());
        client.setCloseListener((code, reason) -> onTradeClosed(client, code, reason));
        tradeClient = client;
        try {
            connectClient(client, environment.getTradeWsUrl());
            for (Subscription subscription : tradeSubscriptions) {
                client.subscribe(subscription.stream, subscription.selector, subscription.listener);
            }
        } catch (RuntimeException e) {
            if (tradeClient == client) {
                tradeClient = null;
            }
            closeQuietly(client);
            throw e;
        }
    }

    @Override
    public boolean isMarketDataConnected() {
        return marketClient != null && marketClient.isOpen();
    }

    @Override
    public boolean isTradeDataConnected() {
        return tradeClient != null && tradeClient.isOpen();
    }

    @Override
    public void forceReconnectMarketData() {
        GrvtWebSocketClient staleClient;
        synchronized (this) {
            if (marketDisconnecting) {
                throw new IllegalStateException("GRVT market-data session is disconnected");
            }
            staleClient = marketClient;
            marketClient = null;
        }

        // Clear the published generation before closing it. Its onClose callback
        // will then recognize this as an intentional replacement and will not
        // schedule a second reconnect. Desired subscriptions live on this API,
        // not on the discarded client, and are replayed by connectMarketData().
        closeQuietly(staleClient);
        logger.warn("Forcing GRVT market-data websocket reconnect; preserving {} subscription(s)",
                marketSubscriptions.size());
        scheduleMarketReconnect(0L);
    }

    @Override
    public synchronized void subscribeMarketData(String stream, String selector, Consumer<JsonNode> listener) {
        Subscription subscription = new Subscription(stream, selector, listener);
        marketSubscriptions.add(subscription);
        if (isMarketDataConnected()) {
            marketClient.subscribe(stream, selector, listener);
        } else {
            // connectMarketData replays the complete desired-subscription list,
            // including the entry added above. Do not subscribe a second time.
            connectMarketData();
        }
    }

    @Override
    public synchronized void subscribeTradeData(String stream, String selector, Consumer<JsonNode> listener) {
        Subscription subscription = new Subscription(stream, selector, listener);
        tradeSubscriptions.add(subscription);
        if (isTradeDataConnected()) {
            tradeClient.subscribe(stream, selector, listener);
        } else {
            connectTradeData();
        }
    }

    @Override
    public CompletableFuture<JsonNode> rpcCreateOrder(GrvtOrder order) {
        connectTradeData();
        JsonNode params = restApi.buildSignedOrderRequest(order);
        return tradeClient.rpc("v1/create_order", params);
    }

    @Override
    public CompletableFuture<JsonNode> rpcCancelOrder(String orderId, String clientOrderId) {
        connectTradeData();
        ObjectNode params = mapper.createObjectNode();
        params.put("sub_account_id", subAccountId());
        if (orderId != null && !orderId.isBlank()) {
            params.put("order_id", orderId);
        } else if (clientOrderId != null && !clientOrderId.isBlank()) {
            params.put("client_order_id", clientOrderId);
        } else {
            throw new IllegalArgumentException("orderId or clientOrderId is required");
        }
        return tradeClient.rpc("v1/cancel_order", params);
    }

    @Override
    public CompletableFuture<JsonNode> rpcCancelAllOrders() {
        connectTradeData();
        ObjectNode params = mapper.createObjectNode();
        params.put("sub_account_id", subAccountId());
        return tradeClient.rpc("v1/cancel_all_orders", params);
    }

    @Override
    public void disconnectMarketData() {
        GrvtWebSocketClient client;
        synchronized (this) {
            marketDisconnecting = true;
            client = marketClient;
            marketClient = null;
            marketSubscriptions.clear();
            marketReconnectScheduled.set(false);
        }
        closeQuietly(client);
    }

    @Override
    public void disconnectTradeData() {
        GrvtWebSocketClient client;
        synchronized (this) {
            tradeDisconnecting = true;
            client = tradeClient;
            tradeClient = null;
            tradeSubscriptions.clear();
            tradeReconnectScheduled.set(false);
        }
        closeQuietly(client);
    }

    /**
     * Ends both current sessions without destroying this factory-cached API.
     * A later connect/subscribe call starts a clean session on the same object.
     */
    @Override
    public void disconnectAll() {
        disconnectMarketData();
        disconnectTradeData();
    }

    // ------------------------------------------------------------- internals

    /** Package-private seam used by reconnect tests; production always creates the real client. */
    GrvtWebSocketClient createClient(String url, Map<String, String> headers) {
        try {
            return new GrvtWebSocketClient(new URI(url), headers);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create GRVT websocket " + url, e);
        }
    }

    private void connectClient(GrvtWebSocketClient client, String url) {
        try {
            boolean connected = client.connectBlocking(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // Java-WebSocket can return false on timeout without invoking
            // onClose. Treating that dead client as a successful reconnect was
            // the failure that left market data permanently stopped after a 1006.
            if (!connected || !client.isOpen()) {
                throw new IllegalStateException("GRVT websocket connect timed out or closed before ready: " + url);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open GRVT websocket " + url, e);
        }
    }

    private Map<String, String> authHeaders() {
        Map<String, String> headers = new HashMap<>();
        String cookie = restApi.getSessionCookie();
        if (cookie != null) {
            headers.put("Cookie", "gravity=" + cookie);
        }
        String account = restApi.getAccountId();
        if (account != null) {
            headers.put("X-Grvt-Account-Id", account);
        }
        return headers;
    }

    private void onMarketClosed(GrvtWebSocketClient closedClient, int code, String reason) {
        synchronized (this) {
            if (marketDisconnecting || marketClient != closedClient) {
                return;
            }
            marketClient = null;
        }
        logger.warn("GRVT market-data socket lost code={} reason={}; scheduling reconnect", code, reason);
        scheduleMarketReconnect();
    }

    private void onTradeClosed(GrvtWebSocketClient closedClient, int code, String reason) {
        synchronized (this) {
            if (tradeDisconnecting || tradeClient != closedClient) {
                return;
            }
            tradeClient = null;
        }
        logger.warn("GRVT trade-data socket lost code={} reason={}; scheduling reconnect", code, reason);
        scheduleTradeReconnect();
    }

    private void scheduleMarketReconnect() {
        scheduleMarketReconnect(reconnectDelaySeconds());
    }

    private void scheduleMarketReconnect(long delaySeconds) {
        if (marketDisconnecting || !marketReconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        scheduleReconnect(this::runMarketReconnect, "market-data", delaySeconds);
    }

    private void runMarketReconnect() {
        if (marketDisconnecting) {
            marketReconnectScheduled.set(false);
            return;
        }
        try {
            connectMarketData();
            marketReconnectScheduled.set(false);
            // If the replacement closed while the reconnect flag was still
            // held, its onClose correctly avoided scheduling a duplicate. Recheck
            // after releasing the flag so that narrow race cannot strand us.
            if (isMarketDataConnected()) {
                logger.info("GRVT market-data websocket reconnected; replayed {} subscription(s)",
                        marketSubscriptions.size());
            } else {
                scheduleMarketReconnect();
            }
        } catch (Exception e) {
            logger.warn("GRVT market-data reconnect failed; retrying in {}s", reconnectDelaySeconds(), e);
            scheduleReconnect(this::runMarketReconnect, "market-data", reconnectDelaySeconds());
        }
    }

    private void scheduleTradeReconnect() {
        if (tradeDisconnecting || !tradeReconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        scheduleReconnect(this::runTradeReconnect, "trade-data", reconnectDelaySeconds());
    }

    private void runTradeReconnect() {
        if (tradeDisconnecting) {
            tradeReconnectScheduled.set(false);
            return;
        }
        try {
            connectTradeData();
            tradeReconnectScheduled.set(false);
            if (isTradeDataConnected()) {
                logger.info("GRVT trade-data websocket reconnected; replayed {} subscription(s)",
                        tradeSubscriptions.size());
            } else {
                scheduleTradeReconnect();
            }
        } catch (Exception e) {
            logger.warn("GRVT trade-data reconnect failed; retrying in {}s", reconnectDelaySeconds(), e);
            scheduleReconnect(this::runTradeReconnect, "trade-data", reconnectDelaySeconds());
        }
    }

    private void scheduleReconnect(Runnable task, String channel, long delaySeconds) {
        try {
            scheduler.schedule(task, Math.max(0L, delaySeconds), TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            boolean disconnecting = "market-data".equals(channel)
                    ? marketDisconnecting
                    : tradeDisconnecting;
            if (!disconnecting) {
                if ("market-data".equals(channel)) {
                    marketReconnectScheduled.set(false);
                } else if ("trade-data".equals(channel)) {
                    tradeReconnectScheduled.set(false);
                }
                logger.error("Unable to schedule GRVT {} websocket reconnect", channel, e);
            }
        }
    }

    /** Package-private so reconnect behavior can be tested without sleeping five seconds. */
    long reconnectDelaySeconds() {
        return RECONNECT_DELAY_SECONDS;
    }

    private String subAccountId() {
        return GrvtConfiguration.getInstance().getSubAccountId();
    }

    private void closeQuietly(GrvtWebSocketClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.debug("Ignoring GRVT websocket close failure", e);
            }
        }
    }

    private static final class Subscription {
        private final String stream;
        private final String selector;
        private final Consumer<JsonNode> listener;

        private Subscription(String stream, String selector, Consumer<JsonNode> listener) {
            this.stream = stream;
            this.selector = selector;
            this.listener = listener;
        }
    }
}
