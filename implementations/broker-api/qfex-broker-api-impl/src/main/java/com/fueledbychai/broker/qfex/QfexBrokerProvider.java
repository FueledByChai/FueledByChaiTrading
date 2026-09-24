package com.fueledbychai.broker.qfex;

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class QfexBrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.QFEX;
    }

    @Override
    public IBroker getBroker() {
        return new QfexBroker();
    }
}
