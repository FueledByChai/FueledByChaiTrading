package com.fueledbychai.aster.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class AsterTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.ASTER;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return AsterTickerRegistry.getInstance();
    }
}
