package com.fueledbychai.hibachi.common.api.signer;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 Hibachi signer.
 *
 * <p>Key is the raw UTF-8 bytes of the apiSecret string (do not hex-decode, do not base64-decode).
 * Message is the raw packed payload (not pre-hashed). Output is lowercase hex (64 chars).
 * Matches {@code hibachi_xyz/api.py:__sign_payload} (api.py:1950-1987).
 */
public class HmacHibachiSigner implements IHibachiSigner {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();

    private final byte[] keyBytes;

    public HmacHibachiSigner(String apiSecret) {
        if (apiSecret == null || apiSecret.isEmpty()) {
            throw new IllegalArgumentException("apiSecret is required");
        }
        this.keyBytes = apiSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String sign(byte[] packedPayload) {
        if (packedPayload == null) {
            throw new IllegalArgumentException("packedPayload is required");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGO));
            return toHexLower(mac.doFinal(packedPayload));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    @Override
    public SignatureScheme scheme() {
        return SignatureScheme.HMAC_SHA256;
    }

    private static String toHexLower(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX_LOWER[v >>> 4];
            out[i * 2 + 1] = HEX_LOWER[v & 0x0F];
        }
        return new String(out);
    }
}
