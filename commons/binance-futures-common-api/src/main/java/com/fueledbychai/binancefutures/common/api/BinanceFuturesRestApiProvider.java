package com.fueledbychai.binancefutures.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

/**
 * Registers the BinanceFutures REST API with the shared REST API factory.
 */
public class BinanceFuturesRestApiProvider implements ExchangeRestApiProvider<IBinanceFuturesRestApi> {

    private static volatile IBinanceFuturesRestApi publicApi;
    private static volatile IBinanceFuturesRestApi privateApi;

    @Override
    public Exchange getExchange() {
        return Exchange.BINANCE_FUTURES;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IBinanceFuturesRestApi> getApiType() {
        return (Class<IBinanceFuturesRestApi>) (Class<?>) IBinanceFuturesRestApi.class;
    }

    @Override
    public IBinanceFuturesRestApi getPublicApi() {
        if (publicApi == null) {
            synchronized (BinanceFuturesRestApiProvider.class) {
                if (publicApi == null) {
                    BinanceFuturesConfiguration config = BinanceFuturesConfiguration.getInstance();
                    publicApi = new BinanceFuturesRestApi(config.getRestUrl(), config.getOptionsRestUrl());
                }
            }
        }
        return publicApi;
    }

    @Override
    public IBinanceFuturesRestApi getApi() {
        return isPrivateApiAvailable() ? getPrivateApi() : getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return BinanceFuturesConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    @Override
    public IBinanceFuturesRestApi getPrivateApi() {
        BinanceFuturesConfiguration config = BinanceFuturesConfiguration.getInstance();
        if (!config.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException("BinanceFutures private API requires account and private key configuration.");
        }
        if (privateApi == null) {
            synchronized (BinanceFuturesRestApiProvider.class) {
                if (privateApi == null) {
                    privateApi = new BinanceFuturesRestApi(config.getRestUrl(), config.getOptionsRestUrl(),
                            config.getAccountAddress(),
                            config.getPrivateKey());
                }
            }
        }
        return privateApi;
    }
}
