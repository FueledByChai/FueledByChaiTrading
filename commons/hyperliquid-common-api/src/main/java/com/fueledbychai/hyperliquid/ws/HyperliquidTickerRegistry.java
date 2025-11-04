package com.fueledbychai.hyperliquid.ws;

import java.util.HashMap;
import java.util.Map;

import com.fueledbychai.data.ITickerTranslator;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;

public class HyperliquidTickerRegistry implements ITickerTranslator, ITickerRegistry {

    protected static ITickerRegistry instace;
    protected Map<String, Ticker> tickerMap = new HashMap<>();
    protected Map<String, Ticker> commonSymbolMap = new HashMap<>();
    protected Map<InstrumentDescriptor, Ticker> descriptorMap = new HashMap<>();
    protected IHyperliquidRestApi restApi = HyperliquidApiFactory.getRestApi();
    protected ITickerTranslator tickerBuilder = new HyperliquidTickerBuilder();

    public static ITickerRegistry getInstance() {
        if (instace == null) {
            instace = new HyperliquidTickerRegistry();
        }
        return instace;
    }

    protected HyperliquidTickerRegistry() {
        try {
            for (InstrumentDescriptor descriptor : restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)) {
                translateTicker(descriptor);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize HyperliquidTickerRegistry", e);
        }
    }

    @Override
    public Ticker lookupByBrokerSymbol(String tickerString) {
        return (Ticker) tickerMap.get(tickerString);
    }

    @Override
    public Ticker lookupByCommonSymbol(String commonSymbol) {
        return (Ticker) commonSymbolMap.get(commonSymbol);
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
        // common symbol is like BTC/USDT, exchange symbol is BTC-USDT
        String exchangeSymbol = commonSymbol.replace("/", "-");
        return exchangeSymbol;
    }
}
