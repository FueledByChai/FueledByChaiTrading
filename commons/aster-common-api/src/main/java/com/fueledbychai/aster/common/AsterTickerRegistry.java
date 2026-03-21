package com.fueledbychai.aster.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.aster.common.api.IAsterRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class AsterTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected final IAsterRestApi restApi;

    public static ITickerRegistry getInstance(IAsterRestApi restApi) {
        if (instance == null) {
            instance = new AsterTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new AsterTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.ASTER, IAsterRestApi.class));
        }
        return instance;
    }

    protected AsterTickerRegistry(IAsterRestApi restApi) {
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
        String normalized = commonSymbol.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.contains("/")) {
            return normalized.replace("/", "");
        }
        if (normalized.endsWith("USDT") || normalized.endsWith("USDC") || normalized.endsWith("BUSD")) {
            return normalized;
        }
        return normalized + "USDT";
    }
}
