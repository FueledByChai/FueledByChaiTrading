package com.fueledbychai.broker.bybit;

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class BybitBrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.BYBIT;
    }

    @Override
    public IBroker getBroker() {
        return new BybitBroker();
    }
}
