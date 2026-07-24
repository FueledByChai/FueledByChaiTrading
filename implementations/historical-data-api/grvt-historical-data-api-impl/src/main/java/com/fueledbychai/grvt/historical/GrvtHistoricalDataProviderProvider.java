package com.fueledbychai.grvt.historical;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.historicaldata.HistoricalDataProviderProvider;
import com.fueledbychai.historicaldata.IHistoricalDataProvider;

public class GrvtHistoricalDataProviderProvider implements HistoricalDataProviderProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.GRVT;
    }

    @Override
    public IHistoricalDataProvider getProvider() {
        return new GrvtHistoricalDataProvider();
    }
}
