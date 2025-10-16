package com.fueledbychai.paradex.common;

import java.util.HashMap;
import java.util.Map;

import com.fueledbychai.data.ITickerBuilder;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.ParadexTickerBuilder;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.paradex.common.api.ParadexApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class ParadexTickerRegistry implements ITickerBuilder, ITickerRegistry {

    protected static ITickerRegistry instace;
    protected Map<String, Ticker> tickerMap = new HashMap<>();
    protected Map<String, Ticker> commonSymbolMap = new HashMap<>();
    protected Map<InstrumentDescriptor, Ticker> descriptorMap = new HashMap<>();
    protected IParadexRestApi restApi = ParadexApiFactory.getPublicApi();
    protected ITickerBuilder tickerBuilder = new ParadexTickerBuilder();

    public static ITickerRegistry getInstance() {
        if (instace == null) {
            instace = new ParadexTickerRegistry();
        }
        return instace;
    }

    protected ParadexTickerRegistry() {
        try {
            for (InstrumentDescriptor descriptor : restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)) {
                buildTicker(descriptor);
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
        return (Ticker) commonSymbolMap.get(commonSymbol);
    }

    @Override
    public Ticker buildTicker(InstrumentDescriptor descriptor) {
        if (descriptor.getInstrumentType() == InstrumentType.PERPETUAL_FUTURES) {

            Ticker ticker = tickerBuilder.buildTicker(descriptor);
            descriptorMap.put(descriptor, ticker);
            commonSymbolMap.put(descriptor.getCommonSymbol(), ticker);
            tickerMap.put(ticker.getSymbol(), ticker);

            return ticker;
        } else {
            throw new IllegalArgumentException("Unsupported instrument type: " + descriptor.getInstrumentType());
        }
    }
}
