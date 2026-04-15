package com.fueledbychai.hibachi.common.api.ws.trade;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONObject;

/**
 * Builds the JSON envelope sent over the Hibachi trade WebSocket.
 *
 * <p>Base shape: {@code {"id": <int>, "method": "<name>", "params": {...}, "signature": "<hex>"}}.
 *
 * <p>Quirks mirrored from {@code hibachi_xyz/api_ws_trade.py}:
 * <ul>
 *   <li>{@link HibachiTradeMethod#ORDER_PLACE}: signature appears BOTH inside {@code params}
 *       and at top level (api_ws_trade.py:198-203).</li>
 *   <li>{@link HibachiTradeMethod#ORDER_MODIFY}: signature is pulled OUT of {@code params}
 *       and only placed at the top level (api_ws_trade.py:324-325).</li>
 *   <li>{@link HibachiTradeMethod#ORDER_CANCEL} / {@link HibachiTradeMethod#ORDERS_CANCEL}:
 *       signature top-level only.</li>
 *   <li>{@link HibachiTradeMethod#ORDERS_BATCH}: no top-level signature — each child carries
 *       its own.</li>
 *   <li>{@link HibachiTradeMethod#ORDER_STATUS} / {@link HibachiTradeMethod#ORDERS_STATUS} /
 *       {@link HibachiTradeMethod#ORDERS_ENABLE_CANCEL_ON_DISCONNECT}: no signature.</li>
 * </ul>
 */
public final class HibachiTradeEnvelope {

    private static final AtomicLong CORRELATION_ID = new AtomicLong(ThreadLocalRandom.current().nextLong(1, 1_000_000));

    private HibachiTradeEnvelope() {
    }

    /** Allocates a fresh monotonic correlation id. */
    public static long nextCorrelationId() {
        return CORRELATION_ID.incrementAndGet();
    }

    /** Builds an {@link HibachiTradeMethod#ORDER_PLACE} envelope. */
    public static String buildPlace(long id, Map<String, Object> params, String signature) {
        JSONObject p = new JSONObject(params);
        p.put("signature", signature);
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("method", HibachiTradeMethod.ORDER_PLACE);
        root.put("params", p);
        root.put("signature", signature);
        return root.toString();
    }

    /** Builds an {@link HibachiTradeMethod#ORDER_MODIFY} envelope. */
    public static String buildModify(long id, Map<String, Object> params, String signature) {
        JSONObject p = new JSONObject(params);
        // SDK pops signature out of params; mirror exactly.
        p.remove("signature");
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("method", HibachiTradeMethod.ORDER_MODIFY);
        root.put("params", p);
        root.put("signature", signature);
        return root.toString();
    }

    /** Builds an {@link HibachiTradeMethod#ORDER_CANCEL} envelope. */
    public static String buildCancel(long id, Map<String, Object> params, String signature) {
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("method", HibachiTradeMethod.ORDER_CANCEL);
        root.put("params", new JSONObject(params));
        root.put("signature", signature);
        return root.toString();
    }

    /** Builds an {@link HibachiTradeMethod#ORDERS_CANCEL} envelope. */
    public static String buildCancelAll(long id, long accountId, long nonce, String signature) {
        JSONObject p = new JSONObject();
        p.put("accountId", accountId);
        p.put("nonce", nonce);
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("method", HibachiTradeMethod.ORDERS_CANCEL);
        root.put("params", p);
        root.put("signature", signature);
        return root.toString();
    }

    /** Builds an {@link HibachiTradeMethod#ORDER_STATUS} envelope (no signature). */
    public static String buildOrderStatus(long id, long accountId, String orderId) {
        JSONObject p = new JSONObject();
        p.put("orderId", orderId);
        p.put("accountId", accountId);
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("method", HibachiTradeMethod.ORDER_STATUS);
        root.put("params", p);
        return root.toString();
    }

    /** Builds an {@link HibachiTradeMethod#ORDERS_STATUS} envelope (no signature). */
    public static String buildOrdersStatus(long id, long accountId) {
        JSONObject p = new JSONObject();
        p.put("accountId", accountId);
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("method", HibachiTradeMethod.ORDERS_STATUS);
        root.put("params", p);
        return root.toString();
    }

    /** Builds an {@link HibachiTradeMethod#ORDERS_ENABLE_CANCEL_ON_DISCONNECT} envelope. */
    public static String buildEnableCancelOnDisconnect(long id, long nonce) {
        JSONObject p = new JSONObject();
        p.put("nonce", nonce);
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("method", HibachiTradeMethod.ORDERS_ENABLE_CANCEL_ON_DISCONNECT);
        root.put("params", p);
        return root.toString();
    }
}
