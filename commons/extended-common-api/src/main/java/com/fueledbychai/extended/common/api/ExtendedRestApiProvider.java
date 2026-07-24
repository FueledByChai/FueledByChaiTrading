package com.fueledbychai.extended.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

public class ExtendedRestApiProvider implements ExchangeRestApiProvider<IExtendedRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.EXTENDED;
    }

    @Override
    public Class<IExtendedRestApi> getApiType() {
        return IExtendedRestApi.class;
    }

    @Override
    public IExtendedRestApi getPublicApi() {
        ExtendedConfiguration config = ExtendedConfiguration.getInstance();
        return new ExtendedRestApi(config.getRestUrl());
    }

    @Override
    public IExtendedRestApi getApi() {
        if (isPrivateApiAvailable()) {
            return getPrivateApi();
        }
        return getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return ExtendedConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    @Override
    public IExtendedRestApi getPrivateApi() {
        ExtendedConfiguration config = ExtendedConfiguration.getInstance();
        if (!config.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException("Extended private API not available. Set " + ExtendedConfiguration.EXTENDED_API_KEY
                    + ", " + ExtendedConfiguration.EXTENDED_STARK_PRIVATE_KEY + ", "
                    + ExtendedConfiguration.EXTENDED_STARK_PUBLIC_KEY + " and " + ExtendedConfiguration.EXTENDED_VAULT_ID + ".");
        }
        return new ExtendedRestApi(config.getRestUrl(), config.getApiKey(), config.getStarkPrivateKey(),
                config.getStarkPublicKey(), config.getVaultId(), config.getStarknetDomain());
    }
}
