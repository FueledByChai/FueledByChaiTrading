package com.fueledbychai.broker.okx;

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class OkxBrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.OKX;
    }

    @Override
    public IBroker getBroker() {
        return new OkxBroker();
    }
}
