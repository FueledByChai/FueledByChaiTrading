package com.fueledbychai.aster.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

/**
 * Registers the Aster REST API with the shared REST API factory.
 */
public class AsterRestApiProvider implements ExchangeRestApiProvider<IAsterRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.ASTER;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IAsterRestApi> getApiType() {
        return (Class<IAsterRestApi>) (Class<?>) IAsterRestApi.class;
    }

    @Override
    public IAsterRestApi getPublicApi() {
        AsterConfiguration config = AsterConfiguration.getInstance();
        return new AsterRestApi(config.getRestUrl(), config.getSpotRestUrl());
    }

    @Override
    public IAsterRestApi getApi() {
        return isPrivateApiAvailable() ? getPrivateApi() : getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return AsterConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    @Override
    public IAsterRestApi getPrivateApi() {
        AsterConfiguration config = AsterConfiguration.getInstance();
        if (!config.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException("Aster private API requires api key and api secret configuration.");
        }
        return new AsterRestApi(config.getRestUrl(), config.getSpotRestUrl(), config.getApiKey(),
                config.getApiSecret(), config.getRecvWindow());
    }
}
