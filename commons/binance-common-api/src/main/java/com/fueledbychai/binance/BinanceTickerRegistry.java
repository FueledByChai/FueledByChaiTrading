package com.fueledbychai.binance;

import java.util.HashMap;
import java.util.Map;

import com.fueledbychai.data.ITickerTranslator;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.util.ITickerRegistry;

public class BinanceTickerRegistry implements ITickerTranslator, ITickerRegistry {

    protected static ITickerRegistry instace;
    protected Map<String, Ticker> tickerMap = new HashMap<>();
    protected Map<String, Ticker> commonSymbolMap = new HashMap<>();
    protected Map<InstrumentDescriptor, Ticker> descriptorMap = new HashMap<>();

    protected ITickerTranslator tickerBuilder = new TickerTranslator();
    protected BinanceInstrumentLookup instrumentLookup = new BinanceInstrumentLookup();

    public static ITickerRegistry getInstance() {
        if (instace == null) {
            instace = new BinanceTickerRegistry();
        }
        return instace;
    }

    protected BinanceTickerRegistry() {
        try {
            InstrumentDescriptor[] descriptors = instrumentLookup.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);
            for (InstrumentDescriptor descriptor : descriptors) {
                translateTicker(descriptor);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize BinanceTickerRegistry", e);
        }
    }

    @Override
    public Ticker lookupByBrokerSymbol(String tickerString) {
        return tickerMap.get(tickerString);
    }

    @Override
    public Ticker lookupByCommonSymbol(String commonSymbol) {
        return lookupByBrokerSymbol(commonSymbolToExchangeSymbol(commonSymbol));
    }

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {

        Ticker ticker = tickerBuilder.translateTicker(descriptor);
        descriptorMap.put(descriptor, ticker);
        commonSymbolMap.put(descriptor.getCommonSymbol(), ticker);
        tickerMap.put(ticker.getSymbol(), ticker);

        return ticker;

    }

    @Override
    public String commonSymbolToExchangeSymbol(String commonSymbol) {
        // common symbol is like BTC/USDT, exchange symbol is BTCUSDT
        String exchangeSymbol = commonSymbol.replace("/", "");
        return exchangeSymbol;

    }
}
