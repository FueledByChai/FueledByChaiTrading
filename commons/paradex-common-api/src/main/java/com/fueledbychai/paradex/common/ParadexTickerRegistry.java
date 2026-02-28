package com.fueledbychai.paradex.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class ParadexTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instace;
    protected IParadexRestApi restApi = ExchangeRestApiFactory.getPublicApi(Exchange.PARADEX, IParadexRestApi.class);

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
            registerDescriptors(restApi.getAllInstrumentsForTypes(
                    new InstrumentType[] { InstrumentType.PERPETUAL_FUTURES, InstrumentType.CRYPTO_SPOT }));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ParadexTickerRegistry", e);
        }
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES || instrumentType == InstrumentType.CRYPTO_SPOT;
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
        String exchangeSymbol = commonSymbol.replace("/", "-");
        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            exchangeSymbol += "-PERP";
        }
        return exchangeSymbol;
    }
}
