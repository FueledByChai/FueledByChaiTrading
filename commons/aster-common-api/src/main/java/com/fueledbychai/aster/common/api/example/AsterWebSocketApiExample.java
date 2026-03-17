package com.fueledbychai.aster.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.aster.common.api.IAsterWebSocketApi;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Aster websocket API from the shared factory.
 */
public class AsterWebSocketApiExample {

    public static void main(String[] args) {
        IAsterWebSocketApi api = ExchangeWebSocketApiFactory.getApi(Exchange.ASTER,
                IAsterWebSocketApi.class);
        api.connect();
        api.connectOrderEntryWebSocket();
        api.disconnectAll();
    }
}
