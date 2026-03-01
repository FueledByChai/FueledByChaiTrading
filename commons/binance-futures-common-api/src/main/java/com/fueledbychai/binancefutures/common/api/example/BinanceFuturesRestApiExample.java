package com.fueledbychai.binancefutures.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesRestApi;
import com.fueledbychai.util.ExchangeRestApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * BinanceFutures REST API from the shared factory.
 */
public class BinanceFuturesRestApiExample {

    public static void main(String[] args) {
        IBinanceFuturesRestApi api = ExchangeRestApiFactory.getApi(Exchange.BINANCE_FUTURES,
                IBinanceFuturesRestApi.class);
        api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
    }
}
