package com.fueledbychai.marketdata.lighter;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class LighterQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.LIGHTER;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return LighterQuoteEngine.class;
    }
}
