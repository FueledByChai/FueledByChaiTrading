package com.fueledbychai.hyperliquid.ws;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class HyperliquidTickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instace;
    protected IHyperliquidRestApi restApi = ExchangeRestApiFactory.getPublicApi(Exchange.HYPERLIQUID,
            IHyperliquidRestApi.class);

    public static ITickerRegistry getInstance() {
        if (instace == null) {
            instace = new HyperliquidTickerRegistry();
        }
        return instace;
    }

    protected HyperliquidTickerRegistry() {
        super(new HyperliquidTickerBuilder());
        initialize();
    }

    protected void initialize() {
        try {
            for (InstrumentDescriptor descriptor : restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)) {
                translateTicker(descriptor);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize HyperliquidTickerRegistry", e);
        }
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        // Hyperliquid uses plain base currency names (e.g. "BTC", "PAXG"), not "BTC/USDT" or "BTC-USDT"
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }
        // Strip quote currency if present (e.g. "BTC/USDT" -> "BTC", "PAXG/USDC" -> "PAXG")
        int slashIndex = commonSymbol.indexOf('/');
        return slashIndex >= 0 ? commonSymbol.substring(0, slashIndex) : commonSymbol;
    }
}
