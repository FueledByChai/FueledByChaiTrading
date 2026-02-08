package com.fueledbychai.hyperliquid.ws;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.AbstractTickerRegistry;

public class HyperliquidTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instace;
    protected IHyperliquidRestApi restApi = HyperliquidApiFactory.getRestApi();

    public static ITickerRegistry getInstance() {
        if (instace == null) {
            instace = new HyperliquidTickerRegistry();
        }
        return instace;
    }

    protected HyperliquidTickerRegistry() {
        super(new HyperliquidTickerBuilder());
        initialize();
    }

    protected void initialize() {
        try {
            for (InstrumentDescriptor descriptor : restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)) {
                translateTicker(descriptor);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize HyperliquidTickerRegistry", e);
        }
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        // common symbol is like BTC/USDT, exchange symbol is BTC-USDT
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }
        String exchangeSymbol = commonSymbol.replace("/", "-");
        return exchangeSymbol;
    }
}
