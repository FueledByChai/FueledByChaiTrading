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

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;

public final class ExchangeRestApiFactory {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRestApiFactory.class);
    private static final Map<Exchange, ExchangeRestApiProvider<?>> providers = new ConcurrentHashMap<>();
    private static final Map<Exchange, Object> publicApis = new ConcurrentHashMap<>();
    private static final Map<Exchange, Object> apis = new ConcurrentHashMap<>();
    private static final Map<Exchange, Object> privateApis = new ConcurrentHashMap<>();
    private static volatile boolean providersLoaded = false;

    private ExchangeRestApiFactory() {
    }

    public static void registerProvider(Exchange exchange, ExchangeRestApiProvider<?> provider) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        if (provider == null) {
            throw new IllegalArgumentException("ExchangeRestApiProvider is required");
        }
        Exchange providerExchange = provider.getExchange();
        if (providerExchange == null) {
            throw new IllegalArgumentException("ExchangeRestApiProvider returned null exchange");
        }
        if (!exchange.equals(providerExchange)) {
            throw new IllegalArgumentException("ExchangeRestApiProvider exchange mismatch: expected "
                    + exchange.getExchangeName() + " but provider declares " + providerExchange.getExchangeName());
        }
        if (provider.getApiType() == null) {
            throw new IllegalArgumentException("ExchangeRestApiProvider returned null API type");
        }
        ExchangeRestApiProvider<?> existing = providers.putIfAbsent(exchange, provider);
        if (existing != null && !existing.equals(provider)) {
            throw new IllegalStateException("ExchangeRestApiProvider already registered for "
                    + exchange.getExchangeName());
        }
    }

    public static boolean isRegistered(Exchange exchange) {
        if (exchange == null) {
            return false;
        }
        ensureProvidersLoaded();
        return providers.containsKey(exchange);
    }

    public static boolean isPrivateApiAvailable(Exchange exchange) {
        ExchangeRestApiProvider<?> provider = getProvider(exchange);
        return provider.isPrivateApiAvailable();
    }

    public static <T> T getPublicApi(Exchange exchange, Class<T> apiType) {
        ExchangeRestApiProvider<?> provider = getProvider(exchange);
        validateRequestedType(exchange, apiType, provider, "public REST API");
        Object api = getOrCreateApi(publicApis, exchange, "public REST API",
                provider::getPublicApi);
        return castApi(exchange, apiType, api, "public REST API");
    }

    public static <T> T getApi(Exchange exchange, Class<T> apiType) {
        ExchangeRestApiProvider<?> provider = getProvider(exchange);
        validateRequestedType(exchange, apiType, provider, "default REST API");
        Object api = getOrCreateApi(apis, exchange, "default REST API",
                provider::getApi);
        return castApi(exchange, apiType, api, "default REST API");
    }

    public static <T> T getPrivateApi(Exchange exchange, Class<T> apiType) {
        ExchangeRestApiProvider<?> provider = getProvider(exchange);
        validateRequestedType(exchange, apiType, provider, "private REST API");
        if (!provider.isPrivateApiAvailable()) {
            throw new IllegalStateException(
                    "Private REST API not available for exchange " + exchange.getExchangeName());
        }
        Object api = getOrCreateApi(privateApis, exchange, "private REST API", provider::getPrivateApi);
        return castApi(exchange, apiType, api, "private REST API");
    }

    private static Object getOrCreateApi(Map<Exchange, Object> cache, Exchange exchange, String label,
            Supplier<?> supplier) {
        Object api = cache.get(exchange);
        if (api != null) {
            return api;
        }
        synchronized (ExchangeRestApiFactory.class) {
            api = cache.get(exchange);
            if (api != null) {
                return api;
            }
            Object created = supplier.get();
            if (created == null) {
                throw new IllegalStateException(label + " provider returned null for exchange "
                        + exchange.getExchangeName());
            }
            cache.put(exchange, created);
            return created;
        }
    }

    private static ExchangeRestApiProvider<?> getProvider(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        ensureProvidersLoaded();
        ExchangeRestApiProvider<?> provider = providers.get(exchange);
        if (provider == null) {
            throw new IllegalStateException("No ExchangeRestApiProvider registered for exchange "
                    + exchange.getExchangeName());
        }
        return provider;
    }

    private static <T> T castApi(Exchange exchange, Class<T> apiType, Object api, String label) {
        if (apiType == null) {
            throw new IllegalArgumentException("API type is required");
        }
        if (!apiType.isInstance(api)) {
            throw new IllegalStateException("Expected " + label + " of type " + apiType.getName() + " for exchange "
                    + exchange.getExchangeName() + " but received " + api.getClass().getName());
        }
        return apiType.cast(api);
    }

    private static <T> void validateRequestedType(Exchange exchange, Class<T> apiType,
            ExchangeRestApiProvider<?> provider, String label) {
        if (apiType == null) {
            throw new IllegalArgumentException("API type is required");
        }
        Class<?> providedType = provider.getApiType();
        if (providedType == null) {
            throw new IllegalStateException("Provider API type is null for exchange " + exchange.getExchangeName());
        }
        if (!apiType.isAssignableFrom(providedType)) {
            throw new IllegalStateException("Requested " + label + " type " + apiType.getName() + " for exchange "
                    + exchange.getExchangeName() + " but provider declares " + providedType.getName());
        }
    }

    private static void ensureProvidersLoaded() {
        if (providersLoaded) {
            return;
        }
        synchronized (ExchangeRestApiFactory.class) {
            if (providersLoaded) {
                return;
            }
            ServiceLoader<ExchangeRestApiProvider> loader = ServiceLoader.load(ExchangeRestApiProvider.class);
            for (ExchangeRestApiProvider<?> provider : loader) {
                try {
                    Exchange exchange = provider.getExchange();
                    if (exchange == null) {
                        logger.warn("ExchangeRestApiProvider {} returned null exchange",
                                provider.getClass().getName());
                        continue;
                    }
                    if (provider.getApiType() == null) {
                        logger.warn("ExchangeRestApiProvider {} returned null API type",
                                provider.getClass().getName());
                        continue;
                    }
                    providers.putIfAbsent(exchange, provider);
                } catch (RuntimeException e) {
                    logger.warn("Failed to register ExchangeRestApiProvider: {}", provider.getClass().getName(), e);
                }
            }
            providersLoaded = true;
        }
    }
}
