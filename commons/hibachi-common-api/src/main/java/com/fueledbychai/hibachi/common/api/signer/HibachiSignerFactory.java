package com.fueledbychai.hibachi.common.api.signer;

import com.fueledbychai.hibachi.common.api.HibachiConfiguration;

/**
 * Factory for the order-signing {@link IHibachiSigner}.
 *
 * <p>Hibachi orders are always ECDSA secp256k1 — the server rejects HMAC for the
 * {@code signature} field on place/modify/cancel envelopes with
 * {@code "Invalid signature length (expected 64-byte signature and a recovery ID, 65 bytes
 * in total)"}. So this factory always returns an {@link EcdsaHibachiSigner}.
 *
 * <p>The ECDSA private key is read from {@link HibachiConfiguration#getPrivateKey()} if set;
 * otherwise from {@link HibachiConfiguration#getApiSecret()}, since Hibachi's account UI
 * surfaces the same value under the "API Secret" label. (The {@link HmacHibachiSigner} is
 * retained for any future REST header-auth use case but is not used for order signing.)
 */
public final class HibachiSignerFactory {

    private HibachiSignerFactory() {
    }

    public static IHibachiSigner create() {
        return create(HibachiConfiguration.getInstance());
    }

    public static IHibachiSigner create(HibachiConfiguration config) {
        String privateKey = config.getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            privateKey = config.getApiSecret();
        }
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException(
                    "Cannot create Hibachi signer: set " + HibachiConfiguration.HIBACHI_PRIVATE_KEY
                            + " or " + HibachiConfiguration.HIBACHI_API_SECRET
                            + " (the secp256k1 private key used to sign orders).");
        }
        return new EcdsaHibachiSigner(privateKey);
    }
}
