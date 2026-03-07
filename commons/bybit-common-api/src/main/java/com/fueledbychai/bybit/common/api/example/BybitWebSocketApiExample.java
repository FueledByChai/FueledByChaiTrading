package com.fueledbychai.bybit.common.api.example;

import com.fueledbychai.bybit.common.api.IBybitWebSocketApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

public class BybitWebSocketApiExample {

    public static void main(String[] args) throws Exception {
        IBybitWebSocketApi api = ExchangeWebSocketApiFactory.getApi(Exchange.BYBIT, IBybitWebSocketApi.class);

        api.connect();
        api.subscribeTicker("BTCUSDT", InstrumentType.CRYPTO_SPOT,
                update -> System.out.println("ticker " + update.getInstrumentId() + " last=" + update.getLastPrice()));
        api.subscribeOrderBook("BTCUSDT", InstrumentType.CRYPTO_SPOT,
                update -> System.out.println("book " + update.getInstrumentId() + " bids=" + update.getBids().size()));
        api.subscribeTrades("BTCUSDT", InstrumentType.CRYPTO_SPOT,
                (instrumentId, trades) -> System.out.println("trades " + instrumentId + " count=" + trades.size()));

        Thread.sleep(15_000L);
        api.disconnectAll();
    }
}
