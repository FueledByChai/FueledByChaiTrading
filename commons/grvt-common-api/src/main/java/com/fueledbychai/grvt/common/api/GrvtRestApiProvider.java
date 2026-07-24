package com.fueledbychai.grvt.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

/**
 * Registers the GRVT REST API with the shared REST API factory.
 */
public class GrvtRestApiProvider implements ExchangeRestApiProvider<IGrvtRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.GRVT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IGrvtRestApi> getApiType() {
        return (Class<IGrvtRestApi>) (Class<?>) IGrvtRestApi.class;
    }

    @Override
    public IGrvtRestApi getPublicApi() {
        GrvtConfiguration config = GrvtConfiguration.getInstance();
        return new GrvtRestApi(config.getGrvtEnvironment());
    }

    @Override
    public IGrvtRestApi getApi() {
        return isPrivateApiAvailable() ? getPrivateApi() : getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return GrvtConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    @Override
    public IGrvtRestApi getPrivateApi() {
        GrvtConfiguration config = GrvtConfiguration.getInstance();
        if (!config.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException(
                    "GRVT private API requires api key, private key and sub account id configuration.");
        }
        return new GrvtRestApi(config.getGrvtEnvironment(), config.getApiKey(), config.getPrivateKey(),
                config.getSubAccountId());
    }
}
