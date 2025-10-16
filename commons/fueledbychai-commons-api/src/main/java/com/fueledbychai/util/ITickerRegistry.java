package com.fueledbychai.util;

import com.fueledbychai.data.Ticker;

public interface ITickerRegistry {

    Ticker lookupByBrokerSymbol(String tickerString);

    Ticker lookupByCommonSymbol(String commonSymbol);

}