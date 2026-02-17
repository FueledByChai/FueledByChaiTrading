package com.fueledbychai.hyperliquid.ws;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.IInstrumentLookup;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ExchangeRestApiFactory;

public class HyperliquidInstrumentLookup implements IInstrumentLookup {

    private final IHyperliquidRestApi api;

    public HyperliquidInstrumentLookup() {
        this(ExchangeRestApiFactory.getPublicApi(Exchange.HYPERLIQUID, IHyperliquidRestApi.class));
    }

    public HyperliquidInstrumentLookup(IHyperliquidRestApi api) {
        if (api == null) {
            throw new IllegalArgumentException("api is required");
        }
        this.api = api;
    }

    @Override
    public InstrumentDescriptor lookupByCommonSymbol(String commonSymbol) {
        return lookupByExchangeSymbol(commonSymbol);
    }

    @Override
    public InstrumentDescriptor lookupByExchangeSymbol(String exchangeSymbol) {
        return api.getInstrumentDescriptor(exchangeSymbol);
    }

    @Override
    public InstrumentDescriptor lookupByTicker(Ticker ticker) {
        return lookupByExchangeSymbol(ticker.getSymbol());
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES) {
            throw new IllegalArgumentException("Only perpetual futures are supported at this time.");
        }
        return api.getAllInstrumentsForType(instrumentType);
    }

}
