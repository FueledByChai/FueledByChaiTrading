package com.fueledbychai.okx.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class OkxTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.OKX;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return OkxTickerRegistry.getInstance();
    }
}
