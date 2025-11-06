package com.fueledbychai.paradex.common;

import java.util.HashMap;
import java.util.Map;

import com.fueledbychai.data.ITickerTranslator;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.paradex.common.api.ParadexApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class ParadexTickerRegistry implements ITickerTranslator, ITickerRegistry {

    protected static ITickerRegistry instace;
    protected Map<String, Ticker> tickerMap = new HashMap<>();
    protected Map<String, Ticker> commonSymbolMap = new HashMap<>();
    protected Map<InstrumentDescriptor, Ticker> descriptorMap = new HashMap<>();
    protected IParadexRestApi restApi = ParadexApiFactory.getPublicApi();
    protected ITickerTranslator tickerBuilder = new TickerTranslator();

    public static ITickerRegistry getInstance() {
        if (instace == null) {
            instace = new ParadexTickerRegistry();
        }
        return instace;
    }

    protected ParadexTickerRegistry() {
        try {
            for (InstrumentDescriptor descriptor : restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)) {
                translateTicker(descriptor);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ParadexTickerRegistry", e);
        }
    }

    @Override
    public Ticker lookupByBrokerSymbol(String tickerString) {
        return (Ticker) tickerMap.get(tickerString);
    }

    @Override
    public Ticker lookupByCommonSymbol(String commonSymbol) {
        return lookupByBrokerSymbol(commonSymbolToExchangeSymbol(commonSymbol));
    }

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        if (descriptor.getInstrumentType() == InstrumentType.PERPETUAL_FUTURES) {

            Ticker ticker = tickerBuilder.translateTicker(descriptor);
            descriptorMap.put(descriptor, ticker);
            commonSymbolMap.put(descriptor.getCommonSymbol(), ticker);
            tickerMap.put(ticker.getSymbol(), ticker);

            return ticker;
        } else {
            throw new IllegalArgumentException("Unsupported instrument type: " + descriptor.getInstrumentType());
        }
    }

    @Override
    public String commonSymbolToExchangeSymbol(String commonSymbol) {
        // common symbol is like BTC/USDT, exchange symbol is BTC-USD-PERP, if its
        // BTC/USDC or BTC/USDT, then common symbol is BTC-USD-PERP

        if (commonSymbol.endsWith("/USDC")) {
            commonSymbol = commonSymbol.substring(0, commonSymbol.length() - 5) + "/USD";
        } else if (commonSymbol.endsWith("/USDT")) {
            commonSymbol = commonSymbol.substring(0, commonSymbol.length() - 5) + "/USD";
        }
        String exchangeSymbol = commonSymbol.replace("/", "-") + "-PERP";
        return exchangeSymbol;
    }
}
