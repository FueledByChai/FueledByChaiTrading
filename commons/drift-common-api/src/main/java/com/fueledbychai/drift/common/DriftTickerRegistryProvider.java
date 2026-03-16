package com.fueledbychai.drift.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class DriftTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.DRIFT;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return DriftTickerRegistry.getInstance();
    }
}
