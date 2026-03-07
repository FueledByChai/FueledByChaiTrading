package com.fueledbychai.bybit.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class BybitTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.BYBIT;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return BybitTickerRegistry.getInstance();
    }
}
