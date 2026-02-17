package com.fueledbychai.util.test;

import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;

public class TestTickerRegistry implements ITickerRegistry {

    @Override
    public Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString) {
        return null;
    }

    @Override
    public Ticker lookupByCommonSymbol(InstrumentType instrumentType, String commonSymbol) {
        return null;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        return commonSymbol;
    }
}
