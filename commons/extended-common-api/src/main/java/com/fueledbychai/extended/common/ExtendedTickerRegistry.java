package com.fueledbychai.extended.common;

import java.util.Locale;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.extended.common.api.IExtendedRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

/**
 * Instrument registry for Extended. Loads all perpetual-futures markets from the
 * REST API and maps common symbols (e.g. {@code BTC/USDT}) to Extended exchange
 * symbols (e.g. {@code BTC-USD}).
 */
public class ExtendedTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected IExtendedRestApi restApi = ExchangeRestApiFactory.getPublicApi(Exchange.EXTENDED, IExtendedRestApi.class);

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new ExtendedTickerRegistry();
        }
        return instance;
    }

    protected ExtendedTickerRegistry() {
        super(new ExtendedTickerTranslator());
        initialize();
    }

    protected void initialize() {
        try {
            registerDescriptors(
                    restApi.getAllInstrumentsForTypes(new InstrumentType[] { InstrumentType.PERPETUAL_FUTURES }));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ExtendedTickerRegistry", e);
        }
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }
        String symbol = commonSymbol.trim().toUpperCase(Locale.US);
        // Normalise stable-coin quotes to USD (Extended quotes everything in USD).
        if (symbol.endsWith("/USDC") || symbol.endsWith("/USDT")) {
            symbol = symbol.substring(0, symbol.length() - 5) + "/USD";
        }
        return symbol.replace("/", "-");
    }
}
