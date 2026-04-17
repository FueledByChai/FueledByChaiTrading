package com.fueledbychai.marketdata.hibachi;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class HibachiQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.HIBACHI;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return HibachiQuoteEngine.class;
    }
}
