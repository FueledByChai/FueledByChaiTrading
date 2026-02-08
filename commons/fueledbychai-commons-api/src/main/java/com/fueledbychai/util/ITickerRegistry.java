package com.fueledbychai.util;

import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public interface ITickerRegistry {

    Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString);

    Ticker lookupByCommonSymbol(InstrumentType instrumentType, String commonSymbol);

    // Common symbol is like BTC/USDT, exchange symbol is like BTCUSDT or
    // BTC-USD-PERP. InstrumentType disambiguates symbol formats.
    String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol);

}
