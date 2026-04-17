package com.fueledbychai.broker.hibachi;

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class HibachiBrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.HIBACHI;
    }

    @Override
    public IBroker getBroker() {
        return new HibachiBroker();
    }
}
