package com.fueledbychai.lighter.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

public class LighterWebSocketApiProvider implements ExchangeWebSocketApiProvider<ILighterWebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.LIGHTER;
    }

    @Override
    public Class<ILighterWebSocketApi> getApiType() {
        return ILighterWebSocketApi.class;
    }

    @Override
    public ILighterWebSocketApi getWebSocketApi() {
        return new LighterWebSocketApi(LighterConfiguration.getInstance().getWebSocketUrl());
    }
}

