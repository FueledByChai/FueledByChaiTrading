package com.fueledbychai.qfex.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.qfex.common.api.IQfexRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class QfexTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected final IQfexRestApi restApi;

    public static ITickerRegistry getInstance(IQfexRestApi restApi) {
        if (instance == null) {
            instance = new QfexTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new QfexTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.QFEX, IQfexRestApi.class));
        }
        return instance;
    }

    protected QfexTickerRegistry(IQfexRestApi restApi) {
        super(new TickerTranslator());
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        this.restApi = restApi;
        initialize();
    }

    protected void initialize() {
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES));
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
        String normalized = commonSymbol.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        // QFEX symbols are BASE-QUOTE (AAPL-USD, GOLD-USD); a bare base means USD.
        if (normalized.contains("-")) {
            return normalized;
        }
        if (normalized.contains("/")) {
            return normalized.replace('/', '-');
        }
        return normalized + "-USD";
    }
}
