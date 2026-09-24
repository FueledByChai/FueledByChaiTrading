package com.fueledbychai.qfex.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.qfex.common.api.IQfexWebSocketApi;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Qfex websocket API from the shared factory.
 */
public class QfexWebSocketApiExample {

    public static void main(String[] args) {
        IQfexWebSocketApi api = ExchangeWebSocketApiFactory.getApi(Exchange.QFEX,
                IQfexWebSocketApi.class);
        api.connect();
        api.connectOrderEntryWebSocket();
        api.disconnectAll();
    }
}
