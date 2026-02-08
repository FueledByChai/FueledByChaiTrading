package com.fueledbychai.util;

import java.util.HashMap;
import java.util.Map;

import com.fueledbychai.data.ITickerTranslator;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public abstract class AbstractTickerRegistry implements ITickerTranslator, ITickerRegistry {

    protected final Map<InstrumentType, Map<String, Ticker>> tickerMap = new HashMap<>();
    protected final Map<InstrumentType, Map<String, Ticker>> commonSymbolMap = new HashMap<>();
    protected final Map<InstrumentType, Map<InstrumentDescriptor, Ticker>> descriptorMap = new HashMap<>();
    protected ITickerTranslator tickerBuilder;

    protected AbstractTickerRegistry(ITickerTranslator tickerBuilder) {
        if (tickerBuilder == null) {
            throw new IllegalArgumentException("tickerBuilder is required");
        }
        this.tickerBuilder = tickerBuilder;
    }

    protected void requireInstrumentType(InstrumentType instrumentType) {
        if (instrumentType == null) {
            throw new IllegalArgumentException("InstrumentType is required");
        }
    }

    protected void requireSupportedInstrumentType(InstrumentType instrumentType) {
        requireInstrumentType(instrumentType);
        if (!supportsInstrumentType(instrumentType)) {
            throw new IllegalArgumentException("Unsupported instrument type: " + instrumentType);
        }
    }

    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return true;
    }

    protected Map<String, Ticker> getTickerMap(InstrumentType instrumentType) {
        return tickerMap.computeIfAbsent(instrumentType, key -> new HashMap<>());
    }

    protected Map<String, Ticker> getCommonSymbolMap(InstrumentType instrumentType) {
        return commonSymbolMap.computeIfAbsent(instrumentType, key -> new HashMap<>());
    }

    protected Map<InstrumentDescriptor, Ticker> getDescriptorMap(InstrumentType instrumentType) {
        return descriptorMap.computeIfAbsent(instrumentType, key -> new HashMap<>());
    }

    protected void cacheTicker(InstrumentDescriptor descriptor, Ticker ticker) {
        InstrumentType instrumentType = descriptor.getInstrumentType();
        getDescriptorMap(instrumentType).put(descriptor, ticker);
        getCommonSymbolMap(instrumentType).put(descriptor.getCommonSymbol(), ticker);
        getTickerMap(instrumentType).put(ticker.getSymbol(), ticker);
    }

    protected void registerDescriptors(InstrumentDescriptor[] descriptors) {
        if (descriptors == null) {
            return;
        }
        for (InstrumentDescriptor descriptor : descriptors) {
            if (descriptor != null) {
                translateTicker(descriptor);
            }
        }
    }

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("InstrumentDescriptor is required");
        }
        InstrumentType instrumentType = descriptor.getInstrumentType();
        requireSupportedInstrumentType(instrumentType);

        Ticker ticker = tickerBuilder.translateTicker(descriptor);
        cacheTicker(descriptor, ticker);
        return ticker;
    }

    @Override
    public Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString) {
        requireSupportedInstrumentType(instrumentType);
        if (tickerString == null) {
            return null;
        }
        Map<String, Ticker> map = tickerMap.get(instrumentType);
        if (map == null) {
            return null;
        }
        return map.get(tickerString);
    }

    @Override
    public Ticker lookupByCommonSymbol(InstrumentType instrumentType, String commonSymbol) {
        requireSupportedInstrumentType(instrumentType);
        if (commonSymbol == null) {
            return null;
        }
        Map<String, Ticker> map = commonSymbolMap.get(instrumentType);
        if (map != null) {
            Ticker direct = map.get(commonSymbol);
            if (direct != null) {
                return direct;
            }
        }
        String exchangeSymbol = commonSymbolToExchangeSymbol(instrumentType, commonSymbol);
        if (exchangeSymbol == null) {
            return null;
        }
        return lookupByBrokerSymbol(instrumentType, exchangeSymbol);
    }
}
