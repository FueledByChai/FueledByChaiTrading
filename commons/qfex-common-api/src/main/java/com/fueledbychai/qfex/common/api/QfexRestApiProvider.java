package com.fueledbychai.qfex.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

/**
 * Registers the Qfex REST API with the shared REST API factory.
 */
public class QfexRestApiProvider implements ExchangeRestApiProvider<IQfexRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.QFEX;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IQfexRestApi> getApiType() {
        return (Class<IQfexRestApi>) (Class<?>) IQfexRestApi.class;
    }

    @Override
    public IQfexRestApi getPublicApi() {
        return new QfexRestApi(QfexConfiguration.getInstance().getRestUrl());
    }

    @Override
    public IQfexRestApi getApi() {
        return isPrivateApiAvailable() ? getPrivateApi() : getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return QfexConfiguration.getInstance().hasPrivateApiConfiguration();
    }

    @Override
    public IQfexRestApi getPrivateApi() {
        QfexConfiguration config = QfexConfiguration.getInstance();
        if (!config.hasPrivateApiConfiguration()) {
            throw new IllegalStateException("QFEX private API requires " + QfexConfiguration.QFEX_API_PUBLIC_KEY
                    + " and " + QfexConfiguration.QFEX_API_SECRET_KEY);
        }
        return new QfexRestApi(config.getRestUrl(),
                new QfexHmacSigner(config.getPublicKey(), config.getSecretKey()), config.getAccountId());
    }
}
