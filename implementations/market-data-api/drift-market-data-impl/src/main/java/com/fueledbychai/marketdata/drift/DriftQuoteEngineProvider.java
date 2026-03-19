package com.fueledbychai.marketdata.drift;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class DriftQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.DRIFT;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return DriftQuoteEngine.class;
    }
}
