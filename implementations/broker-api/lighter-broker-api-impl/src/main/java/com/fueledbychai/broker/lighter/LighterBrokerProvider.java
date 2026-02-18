package com.fueledbychai.broker.lighter;

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class LighterBrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.LIGHTER;
    }

    @Override
    public IBroker getBroker() {
        return new LighterBroker();
    }
}
