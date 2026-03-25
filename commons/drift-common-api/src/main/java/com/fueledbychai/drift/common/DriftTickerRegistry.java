package com.fueledbychai.drift.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.drift.common.api.IDriftRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class DriftTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected final IDriftRestApi restApi;

    public static ITickerRegistry getInstance(IDriftRestApi restApi) {
        if (instance == null) {
            instance = new DriftTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new DriftTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.DRIFT, IDriftRestApi.class));
        }
        return instance;
    }

    protected DriftTickerRegistry(IDriftRestApi restApi) {
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
                return trimmed.substring(0, trimmed.length() - 5) + "-PERP";
            }
            String result = trimmed.replace("/", "-");
            if (!result.endsWith("-PERP")) {
                return result + "-PERP";
            }
            return result;
        }

        if (trimmed.endsWith("/USDC")) {
            return trimmed.substring(0, trimmed.length() - 5);
        }
        return trimmed.replace("/", "");
    }
}
