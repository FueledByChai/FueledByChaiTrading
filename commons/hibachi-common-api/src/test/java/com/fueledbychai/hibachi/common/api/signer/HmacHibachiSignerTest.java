package com.fueledbychai.hibachi.common.api.signer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Validates HMAC-SHA256 wiring against RFC 4231 / canonical test vectors, and pins the
 * lowercase-hex output contract.
 *
 * <p>The Python SDK reference ({@code hibachi_xyz/api.py:__sign_payload}) uses
 * {@code hmac.new(secret.encode(), payload, sha256).hexdigest()} — raw UTF-8 secret,
 * raw payload bytes, lowercase hex.
 */
class HmacHibachiSignerTest {

    @Test
    void sign_asciiPayload_matchesKnownHmacSha256() {
        // Input: secret="key", message="The quick brown fox jumps over the lazy dog"
        // Canonical HMAC-SHA256 output (RFC 4231-style hand-verified):
        //   f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8
        HmacHibachiSigner signer = new HmacHibachiSigner("key");
        String sig = signer.sign("The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8", sig);
    }

    @Test
    void sign_emptyMessage_spaceKey_matchesOpensslReference() {
        // printf '' | openssl dgst -sha256 -hmac ' '
        HmacHibachiSigner signer = new HmacHibachiSigner(" ");
        String sig = signer.sign(new byte[0]);
        assertEquals("7ff2d145855d701733499c9212d4fa10d70e0dfd4c24c3ba68f2e7c031253e44", sig);
    }

    @Test
    void sign_utf8SecretEncoding_notHexDecoded() {
        // Critical: the secret "deadbeef" must be treated as ASCII bytes (UTF-8), NOT hex-decoded.
        // Python SDK uses secret.encode(). Reference:
        //   printf 'test' | openssl dgst -sha256 -hmac 'deadbeef'
        HmacHibachiSigner signer = new HmacHibachiSigner("deadbeef");
        String sig = signer.sign("test".getBytes(StandardCharsets.UTF_8));
        assertEquals("635a17f871abfe7363b7bb590bc8f74abd3693c56b2c2656f1d841c558355b02", sig);
    }

    @Test
    void constructor_rejectsNullOrEmptySecret() {
        assertThrows(IllegalArgumentException.class, () -> new HmacHibachiSigner(null));
        assertThrows(IllegalArgumentException.class, () -> new HmacHibachiSigner(""));
    }

    @Test
    void scheme_isHmacSha256() {
        assertEquals(SignatureScheme.HMAC_SHA256, new HmacHibachiSigner("x").scheme());
    }
}
