package com.fueledbychai.paradex.common;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.paradex.common.api.ParadexApiFactory;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ITickerRegistry;

public class ParadexTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instace;
    protected IParadexRestApi restApi = ParadexApiFactory.getPublicApi();

    public static ITickerRegistry getInstance() {
        if (instace == null) {
            instace = new ParadexTickerRegistry();
        }
        return instace;
    }

    protected ParadexTickerRegistry() {
        super(new TickerTranslator());
        initialize();
    }

    protected void initialize() {
        try {
            for (InstrumentDescriptor descriptor : restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)) {
                translateTicker(descriptor);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ParadexTickerRegistry", e);
        }
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        // common symbol is like BTC/USDT, exchange symbol is BTC-USD-PERP, if its
        // BTC/USDC or BTC/USDT, then common symbol is BTC-USD-PERP
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }
        if (commonSymbol.endsWith("/USDC")) {
            commonSymbol = commonSymbol.substring(0, commonSymbol.length() - 5) + "/USD";
        } else if (commonSymbol.endsWith("/USDT")) {
            commonSymbol = commonSymbol.substring(0, commonSymbol.length() - 5) + "/USD";
        }
        String exchangeSymbol = commonSymbol.replace("/", "-") + "-PERP";
        return exchangeSymbol;
    }
}
