package com.fueledbychai.drift.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

/**
 * Registers the Drift REST API with the shared REST API factory.
 */
public class DriftRestApiProvider implements ExchangeRestApiProvider<IDriftRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.DRIFT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IDriftRestApi> getApiType() {
        return (Class<IDriftRestApi>) (Class<?>) IDriftRestApi.class;
    }

    @Override
    public IDriftRestApi getPublicApi() {
        DriftConfiguration config = DriftConfiguration.getInstance();
        return new DriftRestApi(config.getDataRestUrl(), config.getDlobRestUrl(), null, null);
    }

    @Override
    public IDriftRestApi getApi() {
        return isPrivateApiAvailable() ? getPrivateApi() : getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return DriftConfiguration.getInstance().hasGatewayConfiguration();
    }

    @Override
    public IDriftRestApi getPrivateApi() {
        DriftConfiguration config = DriftConfiguration.getInstance();
        if (!config.hasGatewayConfiguration()) {
            throw new IllegalStateException("Drift gateway REST/WS configuration is required for broker access.");
        }
        return new DriftRestApi(config.getDataRestUrl(), config.getDlobRestUrl(), config.getGatewayRestUrl(), null);
    }
}
