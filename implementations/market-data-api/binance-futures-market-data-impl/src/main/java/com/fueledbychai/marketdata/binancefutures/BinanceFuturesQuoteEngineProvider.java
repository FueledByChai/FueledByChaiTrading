package com.fueledbychai.marketdata.binancefutures;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class BinanceFuturesQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.BINANCE_FUTURES;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return BinanceFuturesQuoteEngine.class;
    }
}
