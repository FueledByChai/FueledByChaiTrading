package com.fueledbychai.hibachi.common.api.signer;

/**
 * Pluggable signer for Hibachi order payloads.
 *
 * <p>v1 ships with {@link HmacHibachiSigner} (HMAC-SHA256 over the packed binary payload).
 * ECDSA (trustless) accounts are a future follow-up — add a sibling implementation and
 * select via {@link HibachiSignerFactory}.
 */
public interface IHibachiSigner {

    /**
     * Signs a raw packed payload.
     *
     * @param packedPayload bytes assembled by {@link HibachiPayloadPacker} (big-endian)
     * @return the hex-encoded signature string to place in the Hibachi JSON envelope
     */
    String sign(byte[] packedPayload);

    SignatureScheme scheme();
}
