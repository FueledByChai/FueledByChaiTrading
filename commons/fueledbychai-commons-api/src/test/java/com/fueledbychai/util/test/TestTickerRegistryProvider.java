package com.fueledbychai.util.test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class TestTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.NYMEX;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return new TestTickerRegistry();
    }
}
