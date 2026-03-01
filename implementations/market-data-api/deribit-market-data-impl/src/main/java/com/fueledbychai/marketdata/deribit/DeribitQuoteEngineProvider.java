package com.fueledbychai.marketdata.deribit;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class DeribitQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.DERIBIT;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return DeribitQuoteEngine.class;
    }
}
