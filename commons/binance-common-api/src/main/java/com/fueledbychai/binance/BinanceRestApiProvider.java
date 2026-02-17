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
package com.fueledbychai.binance;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

public class BinanceRestApiProvider implements ExchangeRestApiProvider<IBinanceRestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.BINANCE_SPOT;
    }

    @Override
    public Class<IBinanceRestApi> getApiType() {
        return IBinanceRestApi.class;
    }

    @Override
    public IBinanceRestApi getPublicApi() {
        BinanceConfiguration config = BinanceConfiguration.getInstance();
        return new BinanceRestApi(config.getRestUrl());
    }

    @Override
    public IBinanceRestApi getApi() {
        if (isPrivateApiAvailable()) {
            return getPrivateApi();
        }
        return getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return BinanceConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    @Override
    public IBinanceRestApi getPrivateApi() {
        BinanceConfiguration config = BinanceConfiguration.getInstance();
        if (!config.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException("Private key configuration not available. Please set "
                    + BinanceConfiguration.BINANCE_ACCOUNT_ADDRESS + " and "
                    + BinanceConfiguration.BINANCE_PRIVATE_KEY + " properties.");
        }
        return new BinanceRestApi(config.getRestUrl(), config.getAccountAddress(), config.getPrivateKey());
    }
}
