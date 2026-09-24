package com.fueledbychai.qfex.common.api;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * QFEX request authentication, shared by REST and the trade WebSocket:
 * {@code signature = hex(HMAC_SHA256(secret, nonce + ":" + unixTsSeconds))}.
 * The request body is not signed. Nonces must be unique within 15 minutes.
 */
public final class QfexHmacSigner {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String publicKey;
    private final byte[] secret;

    public QfexHmacSigner(String publicKey, String secretKey) {
        if (publicKey == null || publicKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("QFEX public and secret keys are required");
        }
        this.publicKey = publicKey;
        this.secret = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    public record Credentials(String publicKey, String nonce, long unixTs, String signature) {
    }

    public Credentials sign() {
        return sign(newNonce(), Instant.now().getEpochSecond());
    }

    public Credentials sign(String nonce, long unixTs) {
        return new Credentials(publicKey, nonce, unixTs, signature(nonce, unixTs));
    }

    String signature(String nonce, long unixTs) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((nonce + ":" + unixTs).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    static String newNonce() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
