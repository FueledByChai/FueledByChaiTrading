package com.fueledbychai.okx.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.okx.common.api.IOkxWebSocketApi;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Okx websocket API from the shared factory.
 */
public class OkxWebSocketApiExample {

    public static void main(String[] args) {
        IOkxWebSocketApi api = ExchangeWebSocketApiFactory.getApi(Exchange.OKX,
                IOkxWebSocketApi.class);
        api.connect();
        api.subscribeTicker("BTC-USDT", update -> System.out.println("Ticker: " + update.getInstrumentId() + " bid="
                + update.getBestBidPrice() + " ask=" + update.getBestAskPrice()));
        api.subscribeTrades("BTC-USDT", (instrumentId, trades) -> System.out
                .println("Trades: " + instrumentId + " count=" + trades.size()));
        api.disconnectAll();
    }
}
