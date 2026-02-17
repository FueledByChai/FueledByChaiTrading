/**
 * MIT License
 *
 * Copyright (c) 2015  FueledByChai Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.fueledbychai.lighter.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

public class LighterRestApiProvider implements ExchangeRestApiProvider<ILighterRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.LIGHTER;
    }

    @Override
    public Class<ILighterRestApi> getApiType() {
        return ILighterRestApi.class;
    }

    @Override
    public ILighterRestApi getPublicApi() {
        LighterConfiguration config = LighterConfiguration.getInstance();
        return new LighterRestApi(config.getRestUrl(), !config.isProductionEnvironment());
    }

    @Override
    public ILighterRestApi getApi() {
        if (isPrivateApiAvailable()) {
            return getPrivateApi();
        }
        return getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return LighterConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    @Override
    public ILighterRestApi getPrivateApi() {
        LighterConfiguration config = LighterConfiguration.getInstance();
        if (!config.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException("Private key configuration not available. Please set "
                    + LighterConfiguration.LIGHTER_ACCOUNT_ADDRESS + " and "
                    + LighterConfiguration.LIGHTER_PRIVATE_KEY + " properties.");
        }
        return new LighterRestApi(config.getRestUrl(), config.getAccountAddress(), config.getPrivateKey(),
                !config.isProductionEnvironment());
    }
}
