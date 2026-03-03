package com.fueledbychai.deribit.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class DeribitTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.DERIBIT;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return DeribitTickerRegistry.getInstance();
    }
}
