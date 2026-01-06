package com.fueledbychai.paradex.common.api;

import com.swmansion.starknet.crypto.StarknetCurveSignature;
import com.swmansion.starknet.data.TypedData;
import com.swmansion.starknet.data.types.Felt;

import java.util.List;
import java.util.Map;

/**
 * Fast Paradex TypedData signer for: - Order - ModifyOrder - Request (used for
 * /v1/auth JWT)
 *
 * Key ideas: - Cache domain JSON once - Cache TypedData "types" maps as static
 * finals (no reparsing) - Build only the tiny "message" JSON per call - Compute
 * message hash via TypedData.getMessageHash(accountAddress) - Sign hash via
 * BouncyCastle (BcStarknetCurveSigner) port of Paradex Groovy
 */
public final class ParadexTypedDataSigner {

    // ---------- Static type definitions (built once) ----------

    private static final List<TypedData.Type> TYPES_DOMAIN = List.of(new TypedData.StandardType("name", "felt"),
            new TypedData.StandardType("chainId", "felt"), new TypedData.StandardType("version", "felt"));

    private static final Map<String, List<TypedData.Type>> TYPES_ORDER = Map.of("StarkNetDomain", TYPES_DOMAIN, "Order",
            List.of(new TypedData.StandardType("timestamp", "felt"), new TypedData.StandardType("market", "felt"),
                    new TypedData.StandardType("side", "felt"), new TypedData.StandardType("orderType", "felt"),
                    new TypedData.StandardType("size", "felt"), new TypedData.StandardType("price", "felt")));

    private static final Map<String, List<TypedData.Type>> TYPES_MODIFY = Map.of("StarkNetDomain", TYPES_DOMAIN,
            "ModifyOrder",
            List.of(new TypedData.StandardType("timestamp", "felt"), new TypedData.StandardType("market", "felt"),
                    new TypedData.StandardType("side", "felt"), new TypedData.StandardType("orderType", "felt"),
                    new TypedData.StandardType("size", "felt"), new TypedData.StandardType("price", "felt"),
                    new TypedData.StandardType("id", "felt")));

    private static final Map<String, List<TypedData.Type>> TYPES_REQUEST = Map.of("StarkNetDomain", TYPES_DOMAIN,
            "Request",
            List.of(new TypedData.StandardType("method", "felt"), new TypedData.StandardType("path", "felt"),
                    new TypedData.StandardType("body", "felt"), new TypedData.StandardType("timestamp", "felt"),
                    new TypedData.StandardType("expiration", "felt")));

    // ---------- Instance state (cached per key/account/chain) ----------

    private final Felt accountAddress;
    private final BcStarknetCurveSigner bcSigner;
    private final String domainJson;

    // Reuse builders to reduce allocations/GC
    private final ThreadLocal<StringBuilder> sbLocal = ThreadLocal.withInitial(() -> new StringBuilder(512));

    /**
     * @param accountAddressHex Starknet account address (0x...)
     * @param privateKeyHex     Private key (0x...)
     * @param chainIdHex        Chain ID (0x...), e.g. "0x3039"
     */
    public ParadexTypedDataSigner(String accountAddressHex, String privateKeyHex, String chainIdHex) {
        this.accountAddress = Felt.fromHex(accountAddressHex);
        Felt privateKey = Felt.fromHex(privateKeyHex);
        this.bcSigner = new BcStarknetCurveSigner(privateKey);

        // Keep format exactly as Paradex expects
        this.domainJson = "{\"name\":\"Paradex\",\"chainId\":\"" + chainIdHex + "\",\"version\":\"1\"}";
    }

    // ===================== ORDER =====================

    public StarknetCurveSignature signOrder(long timestamp, String market, String side, String orderType, String size,
            String price) {

        String messageJson = buildOrderMessageJson(timestamp, market, side, orderType, size, price);
        TypedData typedData = new TypedData(TYPES_ORDER, "Order", domainJson, messageJson);
        Felt msgHash = typedData.getMessageHash(accountAddress);
        return bcSigner.sign(msgHash);
    }

    public String signOrderAsParadexArray(long timestamp, String market, String side, String orderType, String size,
            String price) {

        return toParadexArray(signOrder(timestamp, market, side, orderType, size, price));
    }

    // ===================== MODIFY ORDER =====================

    public StarknetCurveSignature signModifyOrder(long timestamp, String market, String side, String orderType,
            String size, String price, String orderId) {

        String messageJson = buildModifyMessageJson(timestamp, market, side, orderType, size, price, orderId);
        TypedData typedData = new TypedData(TYPES_MODIFY, "ModifyOrder", domainJson, messageJson);
        Felt msgHash = typedData.getMessageHash(accountAddress);
        return bcSigner.sign(msgHash);
    }

    public String signModifyOrderAsParadexArray(long timestamp, String market, String side, String orderType,
            String size, String price, String orderId) {

        return toParadexArray(signModifyOrder(timestamp, market, side, orderType, size, price, orderId));
    }

    // ===================== REQUEST (AUTH JWT) =====================

    /**
     * Convenience wrapper for the JWT auth request signature: method=POST,
     * path=/v1/auth, body=""
     */
    public String signAuthRequestAsParadexArray(long timestamp, long expiration) {
        return toParadexArray(signRequest("POST", "/v1/auth", "", timestamp, expiration));
    }

    /**
     * General Request signature (can be reused for other endpoints if Paradex uses
     * Request signing elsewhere).
     */
    public StarknetCurveSignature signRequest(String method, String path, String body, long timestamp,
            long expiration) {

        String messageJson = buildRequestMessageJson(method, path, body, timestamp, expiration);
        TypedData typedData = new TypedData(TYPES_REQUEST, "Request", domainJson, messageJson);
        Felt msgHash = typedData.getMessageHash(accountAddress);
        return bcSigner.sign(msgHash);
    }

    public String signRequestAsParadexArray(String method, String path, String body, long timestamp, long expiration) {

        return toParadexArray(signRequest(method, path, body, timestamp, expiration));
    }

    // ===================== Helpers =====================

    /**
     * Paradex signature format you used previously: ["r","s"] as decimal strings.
     */
    private String toParadexArray(StarknetCurveSignature sig) {
        return "[\"" + sig.getR().getValue().toString() + "\",\"" + sig.getS().getValue().toString() + "\"]";
    }

    private String buildOrderMessageJson(long timestamp, String market, String side, String orderType, String size,
            String price) {

        StringBuilder sb = sbLocal.get();
        sb.setLength(0);
        sb.append('{').append("\"timestamp\":").append(timestamp).append(',').append("\"market\":\"")
                .append(escapeJson(market)).append("\",").append("\"side\":\"").append(escapeJson(side)).append("\",")
                .append("\"orderType\":\"").append(escapeJson(orderType)).append("\",").append("\"size\":\"")
                .append(escapeJson(size)).append("\",").append("\"price\":\"").append(escapeJson(price)).append("\"")
                .append('}');
        return sb.toString();
    }

    private String buildModifyMessageJson(long timestamp, String market, String side, String orderType, String size,
            String price, String orderId) {

        StringBuilder sb = sbLocal.get();
        sb.setLength(0);
        sb.append('{').append("\"timestamp\":").append(timestamp).append(',').append("\"market\":\"")
                .append(escapeJson(market)).append("\",").append("\"side\":\"").append(escapeJson(side)).append("\",")
                .append("\"orderType\":\"").append(escapeJson(orderType)).append("\",").append("\"size\":\"")
                .append(escapeJson(size)).append("\",").append("\"price\":\"").append(escapeJson(price)).append("\",")
                .append("\"id\":\"").append(escapeJson(orderId)).append("\"").append('}');
        return sb.toString();
    }

    private String buildRequestMessageJson(String method, String path, String body, long timestamp, long expiration) {

        StringBuilder sb = sbLocal.get();
        sb.setLength(0);
        sb.append('{').append("\"method\":\"").append(escapeJson(method)).append("\",").append("\"path\":\"")
                .append(escapeJson(path)).append("\",").append("\"body\":\"").append(escapeJson(body)).append("\",")
                .append("\"timestamp\":").append(timestamp).append(',').append("\"expiration\":").append(expiration)
                .append('}');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        // Fast-path: no escaping needed
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                return s.replace("\\", "\\\\").replace("\"", "\\\"");
            }
        }
        return s;
    }

    public String getDomainJson() {
        return domainJson;
    }
}
