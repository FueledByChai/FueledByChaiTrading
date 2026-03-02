package com.fueledbychai.binance;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ITickerRegistry;

public class BinanceTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instace;
    protected BinanceInstrumentLookup instrumentLookup = new BinanceInstrumentLookup();

    public static ITickerRegistry getInstance() {
        if (instace == null) {
            instace = new BinanceTickerRegistry();
        }
        return instace;
    }

    protected BinanceTickerRegistry() {
        super(new TickerTranslator());
        initialize();
    }

    protected void initialize() {
        try {
            InstrumentDescriptor[] descriptors = instrumentLookup.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);
            for (InstrumentDescriptor descriptor : descriptors) {
                translateTicker(descriptor);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize BinanceTickerRegistry", e);
        }
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.CRYPTO_SPOT;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        // common symbol is like BTC/USDT, exchange symbol is BTCUSDT
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }
        String exchangeSymbol = commonSymbol.replace("/", "");
        return exchangeSymbol;

    }
}
