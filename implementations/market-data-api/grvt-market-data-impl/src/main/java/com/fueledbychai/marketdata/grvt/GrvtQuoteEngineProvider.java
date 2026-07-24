package com.fueledbychai.marketdata.grvt;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class GrvtQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.GRVT;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return GrvtQuoteEngine.class;
    }
}
