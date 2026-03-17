package com.fueledbychai.marketdata.aster;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class AsterQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.ASTER;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return AsterQuoteEngine.class;
    }
}
