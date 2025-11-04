package com.fueledbychai.util;

import com.fueledbychai.data.Ticker;

public interface ITickerRegistry {

    Ticker lookupByBrokerSymbol(String tickerString);

    Ticker lookupByCommonSymbol(String commonSymbol);

    // Common symbol is like BTC/USDT, exchange symbol is like BTCUSDT or
    // BTC-USD-PERP
    String commonSymbolToExchangeSymbol(String commonSymbol);

}