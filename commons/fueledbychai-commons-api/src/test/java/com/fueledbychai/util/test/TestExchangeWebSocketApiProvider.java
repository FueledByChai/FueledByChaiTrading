package com.fueledbychai.util.test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

public class TestExchangeWebSocketApiProvider implements ExchangeWebSocketApiProvider<TestExchangeWebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.CFE;
    }

    @Override
    public Class<TestExchangeWebSocketApi> getApiType() {
        return TestExchangeWebSocketApi.class;
    }

    @Override
    public TestExchangeWebSocketApi getWebSocketApi() {
        return new TestExchangeWebSocketApi("streaming");
    }
}
