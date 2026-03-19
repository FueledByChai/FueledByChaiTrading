package com.fueledbychai.drift.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.drift.common.api.IDriftWebSocketApi;
import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Drift websocket API from the shared factory.
 */
public class DriftWebSocketApiExample {

    public static void main(String[] args) {
        IDriftWebSocketApi api = ExchangeWebSocketApiFactory.getApi(Exchange.DRIFT,
                IDriftWebSocketApi.class);
        api.subscribeOrderBook("SOL-PERP", DriftMarketType.PERP, snapshot -> {
            System.out.println(snapshot.getMarketName() + " bid=" + snapshot.getBestBidPrice() + " ask="
                    + snapshot.getBestAskPrice());
        });
        api.disconnectAll();
    }
}
