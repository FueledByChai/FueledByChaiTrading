package com.fueledbychai.bybit.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

/**
 * Registers the Bybit websocket API with the shared websocket API factory.
 */
public class BybitWebSocketApiProvider implements ExchangeWebSocketApiProvider<IBybitWebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.BYBIT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IBybitWebSocketApi> getApiType() {
        return (Class<IBybitWebSocketApi>) (Class<?>) IBybitWebSocketApi.class;
    }

    @Override
    public IBybitWebSocketApi getWebSocketApi() {
        return new BybitWebSocketApi(BybitConfiguration.getInstance());
    }
}
