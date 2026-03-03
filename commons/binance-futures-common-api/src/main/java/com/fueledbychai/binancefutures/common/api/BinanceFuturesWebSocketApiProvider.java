package com.fueledbychai.binancefutures.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

/**
 * Registers the BinanceFutures websocket API with the shared websocket API
 * factory.
 */
public class BinanceFuturesWebSocketApiProvider implements ExchangeWebSocketApiProvider<IBinanceFuturesWebSocketApi> {

    private static volatile IBinanceFuturesWebSocketApi websocketApi;

    @Override
    public Exchange getExchange() {
        return Exchange.BINANCE_FUTURES;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IBinanceFuturesWebSocketApi> getApiType() {
        return (Class<IBinanceFuturesWebSocketApi>) (Class<?>) IBinanceFuturesWebSocketApi.class;
    }

    @Override
    public IBinanceFuturesWebSocketApi getWebSocketApi() {
        if (websocketApi == null) {
            synchronized (BinanceFuturesWebSocketApiProvider.class) {
                if (websocketApi == null) {
                    BinanceFuturesConfiguration config = BinanceFuturesConfiguration.getInstance();
                    websocketApi = new BinanceFuturesWebSocketApi(config.getWebSocketUrl(),
                            config.getOptionsWebSocketUrl());
                }
            }
        }
        return websocketApi;
    }
}
