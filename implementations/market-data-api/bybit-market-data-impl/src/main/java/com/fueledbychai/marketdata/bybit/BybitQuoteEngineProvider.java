package com.fueledbychai.marketdata.bybit;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class BybitQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.BYBIT;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return BybitQuoteEngine.class;
    }
}
