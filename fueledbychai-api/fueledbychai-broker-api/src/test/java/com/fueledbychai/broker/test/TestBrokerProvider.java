package com.fueledbychai.broker.test;

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class TestBrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.NYBOT;
    }

    @Override
    public IBroker getBroker() {
        return new TestBroker();
    }
}
