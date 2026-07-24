package com.fueledbychai.grvt.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

/**
 * Registers the GRVT WebSocket API with the shared websocket API factory.
 */
public class GrvtWebSocketApiProvider implements ExchangeWebSocketApiProvider<IGrvtWebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.GRVT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IGrvtWebSocketApi> getApiType() {
        return (Class<IGrvtWebSocketApi>) (Class<?>) IGrvtWebSocketApi.class;
    }

    @Override
    public IGrvtWebSocketApi getWebSocketApi() {
        GrvtConfiguration config = GrvtConfiguration.getInstance();
        IGrvtRestApi restApi = config.hasPrivateKeyConfiguration()
                ? ExchangeRestApiFactory.getPrivateApi(Exchange.GRVT, IGrvtRestApi.class)
                : ExchangeRestApiFactory.getPublicApi(Exchange.GRVT, IGrvtRestApi.class);
        return new GrvtWebSocketApi(config.getGrvtEnvironment(), restApi);
    }
}
