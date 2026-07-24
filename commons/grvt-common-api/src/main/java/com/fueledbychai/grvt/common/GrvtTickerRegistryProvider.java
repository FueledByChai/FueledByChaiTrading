package com.fueledbychai.grvt.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class GrvtTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.GRVT;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return GrvtTickerRegistry.getInstance();
    }
}
