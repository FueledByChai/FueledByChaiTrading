package com.fueledbychai.binancefutures.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesRestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class BinanceFuturesTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected final IBinanceFuturesRestApi restApi;

    public static ITickerRegistry getInstance(IBinanceFuturesRestApi restApi) {
        if (instance == null) {
            instance = new BinanceFuturesTickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new BinanceFuturesTickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.BINANCE_FUTURES, IBinanceFuturesRestApi.class));
        }
        return instance;
    }

    protected BinanceFuturesTickerRegistry(IBinanceFuturesRestApi restApi) {
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
        if (normalized.contains("/")) {
            return normalized.replace("/", "");
        }
        if (normalized.endsWith("USDT") || normalized.endsWith("USDC") || normalized.endsWith("BUSD")) {
            return normalized;
        }
        return normalized + "USDT";
    }
}
