package com.fueledbychai.broker.grvt;

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class GrvtBrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.GRVT;
    }

    @Override
    public IBroker getBroker() {
        return new GrvtBroker();
    }
}
