package com.fueledbychai.qfex.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class QfexTickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.QFEX;
    }

    @Override
    public ITickerRegistry getRegistry() {
        return QfexTickerRegistry.getInstance();
    }
}
