package com.fueledbychai.marketdata.extended;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class ExtendedQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.EXTENDED;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return ExtendedQuoteEngine.class;
    }
}
