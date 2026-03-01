package com.fueledbychai.deribit.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.deribit.common.api.IDeribitWebSocketApi;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Deribit websocket API from the shared factory.
 */
public class DeribitWebSocketApiExample {

    public static void main(String[] args) {
        IDeribitWebSocketApi api = ExchangeWebSocketApiFactory.getApi(Exchange.DERIBIT,
                IDeribitWebSocketApi.class);
        api.subscribeTicker("BTC-PERPETUAL", update -> System.out.println(update.getLastPrice()));
        api.disconnectAll();
    }
}
