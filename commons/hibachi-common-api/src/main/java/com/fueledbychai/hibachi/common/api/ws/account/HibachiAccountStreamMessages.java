package com.fueledbychai.hibachi.common.api.ws.account;

import java.time.Instant;

import org.json.JSONObject;

/**
 * Builders for Hibachi account-stream WebSocket messages.
 *
 * <p>See {@code hibachi_xyz/api_ws_account.py} in the Python SDK.
 */
public final class HibachiAccountStreamMessages {

    private HibachiAccountStreamMessages() {
    }

    /** Builds the {@code stream.start} request. */
    public static String buildStreamStart(long id, long accountId) {
        JSONObject p = new JSONObject();
        p.put("accountId", accountId);
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("method", "stream.start");
        root.put("params", p);
        root.put("timestamp", Instant.now().getEpochSecond());
        return root.toString();
    }

    /** Builds the {@code stream.ping} keepalive (recommended every ~14s). */
    public static String buildStreamPing(long id, long accountId, String listenKey) {
        JSONObject p = new JSONObject();
        p.put("accountId", accountId);
        p.put("listenKey", listenKey);
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("method", "stream.ping");
        root.put("params", p);
        root.put("timestamp", Instant.now().getEpochSecond());
        return root.toString();
    }
}
