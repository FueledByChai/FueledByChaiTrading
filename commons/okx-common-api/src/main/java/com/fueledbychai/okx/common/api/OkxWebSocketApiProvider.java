package com.fueledbychai.okx.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

/**
 * Registers the Okx websocket API with the shared websocket API
 * factory.
 */
public class OkxWebSocketApiProvider implements ExchangeWebSocketApiProvider<IOkxWebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.OKX;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IOkxWebSocketApi> getApiType() {
        return (Class<IOkxWebSocketApi>) (Class<?>) IOkxWebSocketApi.class;
    }

    @Override
    public IOkxWebSocketApi getWebSocketApi() {
        return new OkxWebSocketApi(OkxConfiguration.getInstance().getWebSocketUrl());
    }
}
