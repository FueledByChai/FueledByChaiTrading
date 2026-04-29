package com.fueledbychai.broker.hibachi;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
 * </ul>
 *
 * <p>Order placement / modification / cancellation use the trade WS from day one (per the
 * project decision; see {@code memory/project_hibachi.md}). REST is used only for read-only
 * lookups in the broker layer.
 */
public class HibachiTradeWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(HibachiTradeWebSocketClient.class);
    private static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 10_000L;
    protected static final String LATENCY_LOGGER = "latency.hibachi";

    protected final HibachiConfiguration config;
    protected final long accountId;
    protected final String apiKey;
    protected final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    protected volatile HibachiWebSocketClient client;
    protected volatile HibachiJsonProcessor processor;
    protected volatile HibachiOrderStatusListener orderStatusListener;
    protected volatile Consumer<JsonNode> rawListener;

    public HibachiTradeWebSocketClient(HibachiConfiguration config, long accountId, String apiKey) {
        this.config = config;
        this.accountId = accountId;
        this.apiKey = apiKey;
    }

    public synchronized void connect() {
        if (client != null && client.isOpen()) {
            return;
        }
        try {
            processor = new HibachiJsonProcessor(this::onClosed);
            processor.addEventListener(this::onMessage);
            client = HibachiWebSocketClient.createPrivate(
                    config.getTradeWsUrl(), String.valueOf(accountId), processor, apiKey, config.getClient());
            if (!client.connectBlocking(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Hibachi trade WS handshake timed out");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open Hibachi trade WS", e);
        }
    }

    public synchronized void disconnect() {
        HibachiWebSocketClient c = client;
        client = null;
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
        if (processor != null) {
            processor.shutdown();
            processor = null;
        }
        for (CompletableFuture<JsonNode> pending : pendingRequests.values()) {
            pending.completeExceptionally(new IllegalStateException("trade WS disconnected"));
        }
        pendingRequests.clear();
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public void setOrderStatusListener(HibachiOrderStatusListener listener) {
        this.orderStatusListener = listener;
    }

    public void setRawMessageListener(Consumer<JsonNode> listener) {
        this.rawListener = listener;
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
        HibachiWebSocketClient c = client;
        if (c == null || !c.isOpen()) {
            throw new IllegalStateException("Hibachi trade WS is not connected");
        }
        c.send(message);
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
        for (CompletableFuture<JsonNode> pending : pendingRequests.values()) {
            pending.completeExceptionally(new IllegalStateException("trade WS closed"));
        }
        pendingRequests.clear();
    }
}
