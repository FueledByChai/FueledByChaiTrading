package com.fueledbychai.marketdata.qfex;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class QfexQuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.QFEX;
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return QfexQuoteEngine.class;
    }
}
