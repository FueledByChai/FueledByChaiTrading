package com.fueledbychai.util.test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

public class TestExchangeRestApiProvider implements ExchangeRestApiProvider<TestExchangeRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.CBOE;
    }

    @Override
    public Class<TestExchangeRestApi> getApiType() {
        return TestExchangeRestApi.class;
    }

    @Override
    public TestExchangeRestApi getPublicApi() {
        return new TestExchangeRestApi("public");
    }

    @Override
    public TestExchangeRestApi getApi() {
        return new TestExchangeRestApi("default");
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return true;
    }

    @Override
    public TestExchangeRestApi getPrivateApi() {
        return new TestExchangeRestApi("private");
    }
}
