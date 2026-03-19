package com.fueledbychai.drift.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

/**
 * Registers the Drift websocket API with the shared websocket API
 * factory.
 */
public class DriftWebSocketApiProvider implements ExchangeWebSocketApiProvider<IDriftWebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.DRIFT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IDriftWebSocketApi> getApiType() {
        return (Class<IDriftWebSocketApi>) (Class<?>) IDriftWebSocketApi.class;
    }

    @Override
    public IDriftWebSocketApi getWebSocketApi() {
        DriftConfiguration config = DriftConfiguration.getInstance();
        return new DriftWebSocketApi(config.getDlobWebSocketUrl(), config.getGatewayWebSocketUrl());
    }
}
