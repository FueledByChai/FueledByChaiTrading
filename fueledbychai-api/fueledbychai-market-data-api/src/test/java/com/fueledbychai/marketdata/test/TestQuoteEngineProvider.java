package com.fueledbychai.marketdata.test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class TestQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.BOX;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return TestQuoteEngine.class;
    }
}
