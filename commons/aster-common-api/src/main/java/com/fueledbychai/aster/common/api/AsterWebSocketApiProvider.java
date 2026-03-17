package com.fueledbychai.aster.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

/**
 * Registers the Aster websocket API with the shared websocket API
 * factory.
 */
public class AsterWebSocketApiProvider implements ExchangeWebSocketApiProvider<IAsterWebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.ASTER;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IAsterWebSocketApi> getApiType() {
        return (Class<IAsterWebSocketApi>) (Class<?>) IAsterWebSocketApi.class;
    }

    @Override
    public IAsterWebSocketApi getWebSocketApi() {
        return new AsterWebSocketApi(AsterConfiguration.getInstance().getWebSocketUrl());
    }
}
