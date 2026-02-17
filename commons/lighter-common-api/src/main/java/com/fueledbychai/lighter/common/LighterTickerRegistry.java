package com.fueledbychai.lighter.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.lighter.common.api.ILighterRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class LighterTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected final ILighterRestApi restApi;

    public static ITickerRegistry getInstance(ILighterRestApi restApi) {
        if (instance == null) {
            instance = new LighterTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new LighterTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.LIGHTER, ILighterRestApi.class));
        }
        return instance;
    }

    protected LighterTickerRegistry(ILighterRestApi restApi) {
        super(new TickerTranslator());
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        this.restApi = restApi;
        initialize();
    }

    protected void initialize() {
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES));
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT));
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES || instrumentType == InstrumentType.CRYPTO_SPOT;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }

        String trimmed = commonSymbol.trim();
        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            if (trimmed.endsWith("/USDC")) {
                return trimmed.substring(0, trimmed.length() - 5);
            }
            return trimmed;
        }

        if (!trimmed.contains("/") && !trimmed.endsWith("/USDC")) {
            return trimmed + "/USDC";
        }
        return trimmed;
    }
}
