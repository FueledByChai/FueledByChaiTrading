package com.fueledbychai.binancefutures.common;

import java.math.BigDecimal;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.Ticker;
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
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.OPTION));
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES || instrumentType == InstrumentType.OPTION;
    }

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        Ticker ticker = super.translateTicker(descriptor);
        if (descriptor.getInstrumentType() == InstrumentType.OPTION) {
            enrichOptionTicker(ticker);
        }
        return ticker;
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
        if (instrumentType == InstrumentType.OPTION) {
            return normalized.replace("/", "-");
        }
        if (normalized.contains("/")) {
            return normalized.replace("/", "");
        }
        if (normalized.endsWith("USDT") || normalized.endsWith("USDC") || normalized.endsWith("BUSD")) {
            return normalized;
        }
        return normalized + "USDT";
    }

    protected void enrichOptionTicker(Ticker ticker) {
        if (ticker == null || ticker.getSymbol() == null) {
            return;
        }
        String[] parts = ticker.getSymbol().split("-");
        if (parts.length < 4) {
            return;
        }

        populateExpiry(ticker, parts[1]);
        populateStrike(ticker, parts[2]);
        populateRight(ticker, parts[3]);
    }

    protected void populateExpiry(Ticker ticker, String expiryToken) {
        if (expiryToken == null) {
            return;
        }
        String normalized = expiryToken.trim();
        if (normalized.length() == 6) {
            ticker.setExpiryYear(2000 + parseInt(normalized.substring(0, 2)));
            ticker.setExpiryMonth(parseInt(normalized.substring(2, 4)));
            ticker.setExpiryDay(parseInt(normalized.substring(4, 6)));
            return;
        }
        if (normalized.length() == 8) {
            ticker.setExpiryYear(parseInt(normalized.substring(0, 4)));
            ticker.setExpiryMonth(parseInt(normalized.substring(4, 6)));
            ticker.setExpiryDay(parseInt(normalized.substring(6, 8)));
        }
    }

    protected void populateStrike(Ticker ticker, String strikeToken) {
        if (strikeToken == null || strikeToken.isBlank()) {
            return;
        }
        try {
            ticker.setStrike(new BigDecimal(strikeToken));
        } catch (NumberFormatException ignored) {
            // Leave strike unset when the symbol token is not numeric.
        }
    }

    protected void populateRight(Ticker ticker, String rightToken) {
        if (rightToken == null || rightToken.isBlank()) {
            return;
        }
        String normalized = rightToken.trim().toUpperCase();
        if ("C".equals(normalized) || "CALL".equals(normalized)) {
            ticker.setRight(Ticker.Right.CALL);
        } else if ("P".equals(normalized) || "PUT".equals(normalized)) {
            ticker.setRight(Ticker.Right.PUT);
        }
    }

    protected int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
