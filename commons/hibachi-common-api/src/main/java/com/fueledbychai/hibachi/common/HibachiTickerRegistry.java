package com.fueledbychai.hibachi.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.hibachi.common.api.IHibachiRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

/**
 * Hibachi ticker registry. Symbols are already canonical ({@code BTC/USDT-P}), so the
 * common/exchange symbol mapping is a passthrough.
 */
public class HibachiTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected final IHibachiRestApi restApi;

    public static ITickerRegistry getInstance(IHibachiRestApi restApi) {
        if (instance == null) {
            instance = new HibachiTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new HibachiTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.HIBACHI, IHibachiRestApi.class));
        }
        return instance;
    }

    protected HibachiTickerRegistry(IHibachiRestApi restApi) {
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
        // Hibachi uses the canonical form directly, e.g. BTC/USDT-P.
        if (normalized.endsWith("-P")) {
            return normalized;
        }
        if (normalized.contains("/")) {
            return normalized + "-P";
        }
        // Bare base (e.g. "BTC") — assume USDT perp.
        return normalized + "/USDT-P";
    }
}
