package com.fueledbychai.okx.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

/**
 * Registers the Okx REST API with the shared REST API factory.
 */
public class OkxRestApiProvider implements ExchangeRestApiProvider<IOkxRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.OKX;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IOkxRestApi> getApiType() {
        return (Class<IOkxRestApi>) (Class<?>) IOkxRestApi.class;
    }

    @Override
    public IOkxRestApi getPublicApi() {
        return new OkxRestApi(OkxConfiguration.getInstance().getRestUrl());
    }

    @Override
    public IOkxRestApi getApi() {
        return isPrivateApiAvailable() ? getPrivateApi() : getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return OkxConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    @Override
    public IOkxRestApi getPrivateApi() {
        OkxConfiguration config = OkxConfiguration.getInstance();
        if (!config.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException("Okx private API requires account and private key configuration.");
        }
        return new OkxRestApi(config.getRestUrl(), config.getAccountAddress(), config.getPrivateKey());
    }
}
