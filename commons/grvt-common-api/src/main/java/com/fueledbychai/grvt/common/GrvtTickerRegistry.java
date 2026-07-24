package com.fueledbychai.grvt.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.grvt.common.api.IGrvtRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

/**
 * Ticker registry for GRVT. Loads perpetual, futures, spot and option instruments and maps between
 * common symbols (e.g. {@code BTC/USDT}) and GRVT exchange symbols (e.g. {@code BTC_USDT_Perp}).
 */
public class GrvtTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected final IGrvtRestApi restApi;

    public static ITickerRegistry getInstance(IGrvtRestApi restApi) {
        if (instance == null) {
            instance = new GrvtTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new GrvtTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.GRVT, IGrvtRestApi.class));
        }
        return instance;
    }

    protected GrvtTickerRegistry(IGrvtRestApi restApi) {
        super(new TickerTranslator());
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        this.restApi = restApi;
        initialize();
    }

    protected void initialize() {
        registerDescriptors(restApi.getAllInstrumentsForTypes(new InstrumentType[] {
                InstrumentType.PERPETUAL_FUTURES, InstrumentType.FUTURES, InstrumentType.CRYPTO_SPOT,
                InstrumentType.OPTION }));
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES || instrumentType == InstrumentType.FUTURES
                || instrumentType == InstrumentType.CRYPTO_SPOT || instrumentType == InstrumentType.OPTION;
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

        String base;
        String quote;
        int slash = normalized.indexOf('/');
        if (slash > 0) {
            base = normalized.substring(0, slash);
            quote = normalized.substring(slash + 1);
        } else {
            base = normalized;
            quote = "USDT";
        }

        switch (instrumentType) {
            case PERPETUAL_FUTURES:
                return base + "_" + quote + "_Perp";
            case CRYPTO_SPOT:
                return base + "_" + quote + "_Spot";
            default:
                // Futures and options need expiry/strike beyond base/quote; rely on cached lookups.
                return null;
        }
    }
}
