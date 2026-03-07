package com.fueledbychai.bybit.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

/**
 * Registers the Bybit REST API with the shared REST API factory.
 */
public class BybitRestApiProvider implements ExchangeRestApiProvider<IBybitRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.BYBIT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IBybitRestApi> getApiType() {
        return (Class<IBybitRestApi>) (Class<?>) IBybitRestApi.class;
    }

    @Override
    public IBybitRestApi getPublicApi() {
        return new BybitRestApi(BybitConfiguration.getInstance().getRestUrl());
    }

    @Override
    public IBybitRestApi getApi() {
        return isPrivateApiAvailable() ? getPrivateApi() : getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return BybitConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    @Override
    public IBybitRestApi getPrivateApi() {
        BybitConfiguration config = BybitConfiguration.getInstance();
        if (!config.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException("Bybit private API requires api key and api secret configuration.");
        }
        return new BybitRestApi(config.getRestUrl(), config.getApiKey(), config.getApiSecret());
    }
}
