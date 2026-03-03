package com.fueledbychai.util;

import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public interface ITickerRegistry {

    enum OptionRightFilter {
        CALL, PUT, ALL
    }

    Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString);

    Ticker lookupByCommonSymbol(InstrumentType instrumentType, String commonSymbol);

    // Common symbol is like BTC/USDT, exchange symbol is like BTCUSDT or
    // BTC-USD-PERP. InstrumentType disambiguates symbol formats.
    String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol);

    /**
     * Returns every ticker currently cached by the registry.
     */
    default Ticker[] getAllTickers() {
        return new Ticker[0];
    }

    /**
     * Returns every ticker currently cached for the requested instrument type.
     */
    default Ticker[] getAllTickersForType(InstrumentType instrumentType) {
        if (instrumentType == null) {
            throw new IllegalArgumentException("InstrumentType is required");
        }
        return new Ticker[0];
    }

    /**
     * Returns the full option chain for the requested underlying symbol.
     */
    default Ticker[] getOptionChain(String underlyingSymbol) {
        return getOptionChain(underlyingSymbol, 0, 0, 0, OptionRightFilter.ALL);
    }

    /**
     * Returns the option chain for the requested underlying symbol and expiry.
     * Any expiry component set to 0 is treated as a wildcard.
     */
    default Ticker[] getOptionChain(String underlyingSymbol, int expiryYear, int expiryMonth, int expiryDay) {
        return getOptionChain(underlyingSymbol, expiryYear, expiryMonth, expiryDay, OptionRightFilter.ALL);
    }

    /**
     * Returns the option chain for the requested underlying symbol, optional
     * expiry filters, and right selection.
     */
    default Ticker[] getOptionChain(String underlyingSymbol, int expiryYear, int expiryMonth, int expiryDay,
            OptionRightFilter optionRightFilter) {
        return new Ticker[0];
    }

}
