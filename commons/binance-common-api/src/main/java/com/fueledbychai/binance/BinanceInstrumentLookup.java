package com.fueledbychai.binance;

import com.fueledbychai.data.IInstrumentLookup;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public class BinanceInstrumentLookup implements IInstrumentLookup {

    // IBinanceRestApi api = BinanceApiFactory.getRestApi();

    @Override
    public InstrumentDescriptor lookupByCommonSymbol(String commonSymbol) {
        return lookupByExchangeSymbol(commonSymbol);
    }

    @Override
    public InstrumentDescriptor lookupByExchangeSymbol(String exchangeSymbol) {
        // return api.getInstrumentDescriptor(exchangeSymbol);
        return null;
    }

    @Override
    public InstrumentDescriptor lookupByTicker(Ticker ticker) {
        return lookupByExchangeSymbol(ticker.getSymbol());
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        // if (instrumentType != InstrumentType.PERPETUAL_FUTURES) {
        // throw new IllegalArgumentException("Only perpetual futures are supported at
        // this time.");
        // }
        // return api.getAllInstrumentsForType(instrumentType);
        return null;
    }

}
