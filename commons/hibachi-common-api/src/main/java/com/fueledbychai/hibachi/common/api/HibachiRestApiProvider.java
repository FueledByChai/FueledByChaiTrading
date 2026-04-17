package com.fueledbychai.hibachi.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

public class HibachiRestApiProvider implements ExchangeRestApiProvider<IHibachiRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.HIBACHI;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IHibachiRestApi> getApiType() {
        return (Class<IHibachiRestApi>) (Class<?>) IHibachiRestApi.class;
    }

    @Override
    public IHibachiRestApi getPublicApi() {
        HibachiConfiguration config = HibachiConfiguration.getInstance();
        return new HibachiRestApi(config.getRestUrl(), config.getDataRestUrl(), config.getClient());
    }

    @Override
    public IHibachiRestApi getApi() {
        return isPrivateApiAvailable() ? getPrivateApi() : getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return HibachiConfiguration.getInstance().hasPrivateApiConfiguration();
    }

    @Override
    public IHibachiRestApi getPrivateApi() {
        HibachiConfiguration config = HibachiConfiguration.getInstance();
        if (!config.hasPrivateApiConfiguration()) {
            throw new IllegalStateException(
                    "Hibachi private API requires "
                            + HibachiConfiguration.HIBACHI_API_KEY + ", "
                            + HibachiConfiguration.HIBACHI_API_SECRET + ", "
                            + HibachiConfiguration.HIBACHI_ACCOUNT_ID + " configuration.");
        }
        return new HibachiRestApi(config.getRestUrl(), config.getDataRestUrl(), config.getClient(),
                config.getApiKey());
    }
}
