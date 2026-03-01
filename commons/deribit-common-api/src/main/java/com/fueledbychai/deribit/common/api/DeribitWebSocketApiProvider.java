package com.fueledbychai.deribit.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

/**
 * Registers the Deribit websocket API with the shared websocket API
 * factory.
 */
public class DeribitWebSocketApiProvider implements ExchangeWebSocketApiProvider<IDeribitWebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.DERIBIT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IDeribitWebSocketApi> getApiType() {
        return (Class<IDeribitWebSocketApi>) (Class<?>) IDeribitWebSocketApi.class;
    }

    @Override
    public IDeribitWebSocketApi getWebSocketApi() {
        return new DeribitWebSocketApi(DeribitConfiguration.getInstance().getWebSocketUrl());
    }
}
