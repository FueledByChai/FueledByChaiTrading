package com.fueledbychai.binancefutures.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesWebSocketApi;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * BinanceFutures websocket API from the shared factory.
 */
public class BinanceFuturesWebSocketApiExample {

    public static void main(String[] args) {
        IBinanceFuturesWebSocketApi api = ExchangeWebSocketApiFactory.getApi(Exchange.BINANCE_FUTURES,
                IBinanceFuturesWebSocketApi.class);
        api.connect();
        api.connectOrderEntryWebSocket();
        api.disconnectAll();
    }
}
