package com.fueledbychai.historicaldata.test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.historicaldata.HistoricalDataProviderProvider;
import com.fueledbychai.historicaldata.IHistoricalDataProvider;

public class TestHistoricalDataProviderProvider implements HistoricalDataProviderProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.SGX;
    }

    @Override
    public IHistoricalDataProvider getProvider() {
        return new TestHistoricalDataProvider();
    }
}
