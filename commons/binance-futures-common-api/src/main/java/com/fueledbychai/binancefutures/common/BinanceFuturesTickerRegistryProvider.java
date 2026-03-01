package com.fueledbychai.binancefutures.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class BinanceFuturesTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.BINANCE_FUTURES;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return BinanceFuturesTickerRegistry.getInstance();
    }
}
