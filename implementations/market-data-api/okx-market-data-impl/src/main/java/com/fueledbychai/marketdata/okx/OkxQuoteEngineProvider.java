package com.fueledbychai.marketdata.okx;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class OkxQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.OKX;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return OkxQuoteEngine.class;
    }
}
