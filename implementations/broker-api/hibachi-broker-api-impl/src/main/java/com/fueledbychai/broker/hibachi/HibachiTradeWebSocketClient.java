package com.fueledbychai.broker.hibachi;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.hibachi.common.api.HibachiConfiguration;
import com.fueledbychai.time.Span;
import com.fueledbychai.hibachi.common.api.ws.HibachiJsonProcessor;
import com.fueledbychai.hibachi.common.api.ws.HibachiWebSocketClient;
import com.fueledbychai.hibachi.common.api.ws.trade.HibachiOrderStatusListener;
import com.fueledbychai.hibachi.common.api.ws.trade.HibachiOrderStatusUpdate;
import com.fueledbychai.hibachi.common.api.ws.trade.HibachiTradeEnvelope;

/**
 * Trade WebSocket client for Hibachi.
 *
 * <p>Owns:
 * <ul>
 *   <li>The persistent trade WS connection (signed-payload order ops)</li>
 *   <li>A correlation map ({@code id} → {@link CompletableFuture}) for request/response</li>
 *   <li>A small fanout for unsolicited order-status frames</li>
 *   <li>A heartbeat that sends a WS-level ping every
 *       {@link HibachiConfiguration#getTradeWsPingSeconds()} seconds (Hibachi closes idle
 *       trade WS connections at ~60s)</li>
 *   <li>Auto-reconnect with exponential backoff on unexpected close</li>
 * </ul>
 *
 * <p>Order placement / modification / cancellation use the trade WS from day one (per the
 * project decision; see {@code memory/project_hibachi.md}). REST is used only for read-only
 * lookups in the broker layer.
 */
public class HibachiTradeWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(HibachiTradeWebSocketClient.class);
    private static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 10_000L;
    private static final long SEND_RECONNECT_WAIT_MILLIS = 5_000L;
    protected static final String LATENCY_LOGGER = "latency.hibachi";

    protected final HibachiConfiguration config;
    protected final long accountId;
    protected final String apiKey;
    protected final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    protected volatile HibachiWebSocketClient client;
    protected volatile HibachiJsonProcessor processor;
    protected volatile HibachiOrderStatusListener orderStatusListener;
    protected volatile Consumer<JsonNode> rawListener;
    protected volatile Consumer<Boolean> connectionStateListener;

    protected volatile ScheduledExecutorService pingScheduler;
    protected volatile ScheduledFuture<?> pingTask;
    protected volatile ScheduledExecutorService reconnectScheduler;
    protected volatile ScheduledFuture<?> reconnectTask;
    protected volatile long reconnectBackoffMs;
    protected volatile boolean shutdown;

    public HibachiTradeWebSocketClient(HibachiConfiguration config, long accountId, String apiKey) {
        this.config = config;
        this.accountId = accountId;
        this.apiKey = apiKey;
        this.reconnectBackoffMs = Math.max(100L, config.getWsReconnectInitialBackoffMs());
    }

    public synchronized void connect() {
        if (client != null && client.isOpen()) {
            return;
        }
        shutdown = false;
        try {
            doConnect();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open Hibachi trade WS", e);
        }
    }

    public synchronized void disconnect() {
        shutdown = true;
        cancelReconnect();
        stopPing();
        cleanupClient();
        failPending(new IllegalStateException("trade WS disconnected"));
        notifyState(false);
    }

    public boolean isConnected() {
        HibachiWebSocketClient c = client;
        return c != null && c.isOpen();
    }

    public void setOrderStatusListener(HibachiOrderStatusListener listener) {
        this.orderStatusListener = listener;
    }

    public void setRawMessageListener(Consumer<JsonNode> listener) {
        this.rawListener = listener;
    }

    /** Notified with {@code true} when the trade WS opens, {@code false} when it closes. */
    public void setConnectionStateListener(Consumer<Boolean> listener) {
        this.connectionStateListener = listener;
    }

    /**
     * Sends an {@code order.place} request and awaits the response.
     */
    public JsonNode placeOrder(Map<String, Object> params, String signature, String traceId) throws Exception {
        long id = HibachiTradeEnvelope.nextCorrelationId();
        return await(id, HibachiTradeEnvelope.buildPlace(id, params, signature), "HB_PLACE_ORDER_WS", traceId);
    }

    /** Sends an {@code order.modify} request and awaits the response. */
    public JsonNode modifyOrder(Map<String, Object> params, String signature, String traceId) throws Exception {
        long id = HibachiTradeEnvelope.nextCorrelationId();
        return await(id, HibachiTradeEnvelope.buildModify(id, params, signature), "HB_MODIFY_ORDER_WS", traceId);
    }

    /** Sends an {@code order.cancel} request and awaits the response. */
    public JsonNode cancelOrder(Map<String, Object> params, String signature, String traceId) throws Exception {
        long id = HibachiTradeEnvelope.nextCorrelationId();
        return await(id, HibachiTradeEnvelope.buildCancel(id, params, signature), "HB_CANCEL_ORDER_WS", traceId);
    }

    /** Sends an {@code orders.cancel} (cancel-all) request and awaits the response. */
    public JsonNode cancelAll(long nonce, String signature) throws Exception {
        long id = HibachiTradeEnvelope.nextCorrelationId();
        return await(id, HibachiTradeEnvelope.buildCancelAll(id, accountId, nonce, signature),
                "HB_CANCEL_ALL_WS", String.valueOf(nonce));
    }

    /** Queries the status of a single order; no signature. */
    public JsonNode orderStatus(String orderId) throws Exception {
        long id = HibachiTradeEnvelope.nextCorrelationId();
        return await(id, HibachiTradeEnvelope.buildOrderStatus(id, accountId, orderId),
                "HB_ORDER_STATUS_WS", orderId);
    }

    /** Queries the status of all open orders; no signature. */
    public JsonNode ordersStatus() throws Exception {
        long id = HibachiTradeEnvelope.nextCorrelationId();
        return await(id, HibachiTradeEnvelope.buildOrdersStatus(id, accountId),
                "HB_ORDERS_STATUS_WS", String.valueOf(accountId));
    }

    /** Sends {@code orders.enableCancelOnDisconnect}. */
    public void enableCancelOnDisconnect(long nonce) {
        long id = HibachiTradeEnvelope.nextCorrelationId();
        send(HibachiTradeEnvelope.buildEnableCancelOnDisconnect(id, nonce));
    }

    protected JsonNode await(long id, String message, String spanPhase, String traceId) throws Exception {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        try (var s = Span.start(spanPhase, traceId == null ? String.valueOf(id) : traceId, LATENCY_LOGGER)) {
            send(message);
            return future.get(DEFAULT_REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            pendingRequests.remove(id);
            throw new IllegalStateException("Timed out waiting for Hibachi trade WS response id=" + id, te);
        }
    }

    protected void send(String message) {
        if (!ensureConnected()) {
            throw new IllegalStateException("Hibachi trade WS is not connected");
        }
        HibachiWebSocketClient c = client;
        if (c == null || !c.isOpen()) {
            throw new IllegalStateException("Hibachi trade WS is not connected");
        }
        c.send(message);
    }

    /**
     * Ensures the WS is open before a send, attempting a synchronous reconnect (with a short
     * deadline) if it is not. Returns {@code true} if the WS is open at the end.
     */
    protected boolean ensureConnected() {
        if (isConnected() || shutdown) {
            return isConnected();
        }
        long deadline = System.currentTimeMillis() + SEND_RECONNECT_WAIT_MILLIS;
        synchronized (this) {
            while (!isConnected() && !shutdown && System.currentTimeMillis() < deadline) {
                try {
                    doConnect();
                    return true;
                } catch (Exception e) {
                    logger.debug("Hibachi trade WS reconnect attempt failed; will retry", e);
                    try {
                        wait(Math.min(500L, Math.max(50L, deadline - System.currentTimeMillis())));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return isConnected();
    }

    protected synchronized void doConnect() throws Exception {
        cleanupClient();
        processor = new HibachiJsonProcessor(this::onClosed);
        processor.addEventListener(this::onMessage);
        client = HibachiWebSocketClient.createPrivate(
                config.getTradeWsUrl(), String.valueOf(accountId), processor, apiKey, config.getClient());
        if (!client.connectBlocking(15, TimeUnit.SECONDS)) {
            cleanupClient();
            throw new IllegalStateException("Hibachi trade WS handshake timed out");
        }
        cancelReconnect();
        reconnectBackoffMs = Math.max(100L, config.getWsReconnectInitialBackoffMs());
        startPing();
        notifyState(true);
    }

    protected synchronized void cleanupClient() {
        HibachiWebSocketClient c = client;
        client = null;
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
        if (processor != null) {
            try { processor.shutdown(); } catch (Exception ignored) {}
            processor = null;
        }
    }

    protected void startPing() {
        stopPing();
        long intervalSeconds = Math.max(1L, config.getTradeWsPingSeconds());
        pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hibachi-trade-ping");
            t.setDaemon(true);
            return t;
        });
        pingTask = pingScheduler.scheduleAtFixedRate(this::sendHeartbeat,
                intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    protected void stopPing() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        if (pingScheduler != null) {
            pingScheduler.shutdownNow();
            pingScheduler = null;
        }
    }

    protected void sendHeartbeat() {
        HibachiWebSocketClient c = client;
        if (c == null || !c.isOpen()) {
            return;
        }
        try {
            c.sendPing();
        } catch (Exception e) {
            logger.warn("Hibachi trade WS ping failed; will rely on close handler to reconnect", e);
        }
    }

    protected void scheduleReconnect() {
        if (shutdown) {
            return;
        }
        synchronized (this) {
            if (reconnectTask != null && !reconnectTask.isDone()) {
                return;
            }
            if (reconnectScheduler == null) {
                reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "hibachi-trade-reconnect");
                    t.setDaemon(true);
                    return t;
                });
            }
            long delay = reconnectBackoffMs;
            long max = Math.max(delay, config.getWsReconnectMaxBackoffMs());
            reconnectBackoffMs = Math.min(max, Math.max(delay * 2L, delay + 250L));
            logger.info("Scheduling Hibachi trade WS reconnect in {}ms", delay);
            reconnectTask = reconnectScheduler.schedule(this::tryReconnect, delay, TimeUnit.MILLISECONDS);
        }
    }

    protected void tryReconnect() {
        if (shutdown) {
            return;
        }
        try {
            synchronized (this) {
                if (isConnected()) {
                    return;
                }
                doConnect();
                logger.info("Hibachi trade WS reconnected");
                notifyAll();
            }
        } catch (Exception e) {
            logger.warn("Hibachi trade WS reconnect failed; rescheduling", e);
            scheduleReconnect();
        }
    }

    protected synchronized void cancelReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            reconnectScheduler = null;
        }
    }

    protected void onMessage(JsonNode message) {
        if (message == null) {
            return;
        }
        if (message.has("id")) {
            long id = message.path("id").asLong();
            CompletableFuture<JsonNode> pending = pendingRequests.remove(id);
            if (pending != null) {
                pending.complete(message);
            }
        }
        Consumer<JsonNode> raw = rawListener;
        if (raw != null) {
            try { raw.accept(message); } catch (Exception e) { logger.warn("trade WS raw listener failed", e); }
        }
        HibachiOrderStatusListener listener = orderStatusListener;
        if (listener != null) {
            HibachiOrderStatusUpdate update = parseOrderStatusUpdate(message);
            if (update != null) {
                try { listener.onOrderStatus(update); } catch (Exception e) {
                    logger.warn("order status listener failed", e);
                }
            }
        }
    }

    protected HibachiOrderStatusUpdate parseOrderStatusUpdate(JsonNode message) {
        // Best-effort parse of unsolicited order-status frames. Exact shape is
        // not fully specified in the docs — extend this when first observed
        // against testnet.
        if (message == null || !message.has("orderId")) {
            return null;
        }
        return null;
    }

    protected void onClosed() {
        stopPing();
        failPending(new IllegalStateException("trade WS closed"));
        notifyState(false);
        if (!shutdown) {
            scheduleReconnect();
        }
    }

    protected void failPending(Throwable cause) {
        for (CompletableFuture<JsonNode> pending : pendingRequests.values()) {
            pending.completeExceptionally(cause);
        }
        pendingRequests.clear();
    }

    protected void notifyState(boolean connected) {
        Consumer<Boolean> listener = connectionStateListener;
        if (listener == null) {
            return;
        }
        try {
            listener.accept(connected);
        } catch (Exception e) {
            logger.warn("Hibachi trade WS connection-state listener failed", e);
        }
    }
}
