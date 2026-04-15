package com.fueledbychai.hibachi.common.api.signer;

import com.fueledbychai.hibachi.common.api.HibachiConfiguration;

public final class HibachiSignerFactory {

    private HibachiSignerFactory() {
    }

    public static IHibachiSigner create() {
        return create(HibachiConfiguration.getInstance());
    }

    public static IHibachiSigner create(HibachiConfiguration config) {
        String secret = config.getApiSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Cannot create Hibachi signer: " + HibachiConfiguration.HIBACHI_API_SECRET + " is not configured");
        }
        return new HmacHibachiSigner(secret);
    }
}
