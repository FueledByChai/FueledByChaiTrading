package com.fueledbychai.hibachi.common.api.ws.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class HibachiTradeEnvelopeTest {

    @Test
    void buildPlace_putsSignatureBothInsideParamsAndAtTopLevel() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", "BTC/USDT-P");
        params.put("accountId", 12345);
        String env = HibachiTradeEnvelope.buildPlace(99L, params, "abcdef");
        JSONObject root = new JSONObject(env);
        assertEquals(99L, root.getLong("id"));
        assertEquals("order.place", root.getString("method"));
        assertEquals("abcdef", root.getString("signature"));
        assertEquals("abcdef", root.getJSONObject("params").getString("signature"));
    }

    @Test
    void buildModify_putsSignatureOnlyAtTopLevel() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orderId", "9999");
        params.put("accountId", 12345);
        params.put("signature", "leakedFromCaller"); // ensure we strip it
        String env = HibachiTradeEnvelope.buildModify(1L, params, "realsig");
        JSONObject root = new JSONObject(env);
        assertEquals("realsig", root.getString("signature"));
        assertFalse(root.getJSONObject("params").has("signature"));
    }

    @Test
    void buildCancel_envelopeHasTopLevelSignature() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orderId", "9999");
        params.put("accountId", 12345);
        String env = HibachiTradeEnvelope.buildCancel(2L, params, "sig");
        JSONObject root = new JSONObject(env);
        assertEquals("order.cancel", root.getString("method"));
        assertEquals("sig", root.getString("signature"));
        assertEquals("9999", root.getJSONObject("params").getString("orderId"));
    }

    @Test
    void buildOrderStatus_hasNoSignature() {
        String env = HibachiTradeEnvelope.buildOrderStatus(7L, 12345, "abc");
        JSONObject root = new JSONObject(env);
        assertEquals("order.status", root.getString("method"));
        assertFalse(root.has("signature"));
        assertEquals("abc", root.getJSONObject("params").getString("orderId"));
    }

    @Test
    void buildCancelAll_paramsCarryAccountIdAndNonce() {
        String env = HibachiTradeEnvelope.buildCancelAll(11L, 12345, 1700000000000L, "sig");
        JSONObject root = new JSONObject(env);
        assertEquals("orders.cancel", root.getString("method"));
        assertEquals(12345, root.getJSONObject("params").getInt("accountId"));
        assertEquals(1700000000000L, root.getJSONObject("params").getLong("nonce"));
    }

    @Test
    void nextCorrelationId_isMonotonic() {
        long a = HibachiTradeEnvelope.nextCorrelationId();
        long b = HibachiTradeEnvelope.nextCorrelationId();
        assertTrue(b > a);
    }
}
