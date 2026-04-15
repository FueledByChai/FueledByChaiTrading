package com.fueledbychai.hibachi.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class HibachiTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.HIBACHI;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return HibachiTickerRegistry.getInstance();
    }
}
