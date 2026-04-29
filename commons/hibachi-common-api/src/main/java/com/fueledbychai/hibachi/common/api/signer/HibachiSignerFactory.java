package com.fueledbychai.hibachi.common.api.signer;

import com.fueledbychai.hibachi.common.api.HibachiConfiguration;

public final class HibachiSignerFactory {

    private HibachiSignerFactory() {
    }

    public static IHibachiSigner create() {
        return create(HibachiConfiguration.getInstance());
    }

    public static IHibachiSigner create(HibachiConfiguration config) {
        String privateKey = config.getPrivateKey();
        if (privateKey != null && !privateKey.isBlank()) {
            return new EcdsaHibachiSigner(privateKey);
        }
        String secret = config.getApiSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Cannot create Hibachi signer: set " + HibachiConfiguration.HIBACHI_PRIVATE_KEY
                            + " (trustless ECDSA) or " + HibachiConfiguration.HIBACHI_API_SECRET
                            + " (exchange-managed HMAC).");
        }
        return new HmacHibachiSigner(secret);
    }
}
