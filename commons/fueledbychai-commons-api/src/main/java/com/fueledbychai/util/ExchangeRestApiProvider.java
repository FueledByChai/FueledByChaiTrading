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
package com.fueledbychai.util;

import com.fueledbychai.data.Exchange;

/**
 * Service-provider contract used by {@link ExchangeRestApiFactory}.
 *
 * Each exchange module should provide exactly one implementation for its
 * exchange and register it under
 * {@code META-INF/services/com.fueledbychai.util.ExchangeRestApiProvider}.
 *
 * @param <TApi> the public REST API interface exposed by the provider
 */
public interface ExchangeRestApiProvider<TApi> {

    /**
     * Returns the exchange served by this provider.
     *
     * @return the provider's exchange
     */
    Exchange getExchange();

    /**
     * Returns the public API interface type exposed by this provider.
     *
     * @return the interface class used for factory type checks
     */
    Class<TApi> getApiType();

    /**
     * Returns the public, unauthenticated REST API instance.
     *
     * @return the public REST API
     */
    TApi getPublicApi();

    /**
     * Returns the default REST API instance for this exchange.
     *
     * Providers commonly return either the public API or an authenticated API
     * depending on local configuration.
     *
     * @return the default REST API instance
     */
    default TApi getApi() {
        return getPublicApi();
    }

    /**
     * Indicates whether the provider can supply a private REST API instance.
     *
     * @return {@code true} when {@link #getPrivateApi()} is supported
     */
    default boolean isPrivateApiAvailable() {
        return false;
    }

    /**
     * Returns the private, authenticated REST API instance.
     *
     * @return the private REST API
     */
    default TApi getPrivateApi() {
        throw new IllegalStateException("Private REST API is not available for exchange "
                + getExchange().getExchangeName());
    }
}
