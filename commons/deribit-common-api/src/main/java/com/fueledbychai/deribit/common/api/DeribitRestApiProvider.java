package com.fueledbychai.deribit.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

/**
 * Registers the Deribit REST API with the shared REST API factory.
 */
public class DeribitRestApiProvider implements ExchangeRestApiProvider<IDeribitRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.DERIBIT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IDeribitRestApi> getApiType() {
        return (Class<IDeribitRestApi>) (Class<?>) IDeribitRestApi.class;
    }

    @Override
    public IDeribitRestApi getPublicApi() {
        return new DeribitRestApi(DeribitConfiguration.getInstance().getRestUrl());
    }

    @Override
    public IDeribitRestApi getApi() {
        return getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return false;
    }

    @Override
    public IDeribitRestApi getPrivateApi() {
        throw new IllegalStateException("Private REST API is not implemented for Deribit.");
    }
}
