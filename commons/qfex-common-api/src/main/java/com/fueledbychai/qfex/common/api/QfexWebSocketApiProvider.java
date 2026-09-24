package com.fueledbychai.qfex.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

/**
 * Registers the Qfex websocket API with the shared websocket API
 * factory.
 */
public class QfexWebSocketApiProvider implements ExchangeWebSocketApiProvider<IQfexWebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.QFEX;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IQfexWebSocketApi> getApiType() {
        return (Class<IQfexWebSocketApi>) (Class<?>) IQfexWebSocketApi.class;
    }

    @Override
    public IQfexWebSocketApi getWebSocketApi() {
        return new QfexWebSocketApi(QfexConfiguration.getInstance());
    }
}
