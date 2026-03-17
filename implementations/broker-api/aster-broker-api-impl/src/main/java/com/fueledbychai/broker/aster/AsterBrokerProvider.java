package com.fueledbychai.broker.aster;

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class AsterBrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.ASTER;
    }

    @Override
    public IBroker getBroker() {
        return new AsterBroker();
    }
}
