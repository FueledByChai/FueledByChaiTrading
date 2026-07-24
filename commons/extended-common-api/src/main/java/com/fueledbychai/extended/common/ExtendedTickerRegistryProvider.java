package com.fueledbychai.extended.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class ExtendedTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.EXTENDED;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return ExtendedTickerRegistry.getInstance();
    }
}
