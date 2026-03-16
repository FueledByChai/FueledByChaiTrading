package com.fueledbychai.broker.drift;

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class DriftBrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.DRIFT;
    }

    @Override
    public IBroker getBroker() {
        return new DriftBroker();
    }
}
