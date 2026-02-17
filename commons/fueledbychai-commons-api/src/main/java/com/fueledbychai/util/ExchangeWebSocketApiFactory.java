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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;

public final class ExchangeWebSocketApiFactory {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeWebSocketApiFactory.class);
    private static final Map<Exchange, ExchangeWebSocketApiProvider<?>> providers = new ConcurrentHashMap<>();
    private static final Map<Exchange, Object> websocketApis = new ConcurrentHashMap<>();
    private static volatile boolean providersLoaded = false;

    private ExchangeWebSocketApiFactory() {
    }

    public static void registerProvider(Exchange exchange, ExchangeWebSocketApiProvider<?> provider) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        if (provider == null) {
            throw new IllegalArgumentException("ExchangeWebSocketApiProvider is required");
        }
        Exchange providerExchange = provider.getExchange();
        if (providerExchange == null) {
            throw new IllegalArgumentException("ExchangeWebSocketApiProvider returned null exchange");
        }
        if (!exchange.equals(providerExchange)) {
            throw new IllegalArgumentException("ExchangeWebSocketApiProvider exchange mismatch: expected "
                    + exchange.getExchangeName() + " but provider declares " + providerExchange.getExchangeName());
        }
        if (provider.getApiType() == null) {
            throw new IllegalArgumentException("ExchangeWebSocketApiProvider returned null API type");
        }
        ExchangeWebSocketApiProvider<?> existing = providers.putIfAbsent(exchange, provider);
        if (existing != null && !existing.equals(provider)) {
            throw new IllegalStateException("ExchangeWebSocketApiProvider already registered for "
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

    public static <T> T getApi(Exchange exchange, Class<T> apiType) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        if (apiType == null) {
            throw new IllegalArgumentException("API type is required");
        }
        ensureProvidersLoaded();

        ExchangeWebSocketApiProvider<?> provider = getProvider(exchange);
        validateRequestedType(exchange, apiType, provider);
        Object api = websocketApis.get(exchange);
        if (api == null) {
            synchronized (ExchangeWebSocketApiFactory.class) {
                api = websocketApis.get(exchange);
                if (api == null) {
                    api = provider.getWebSocketApi();
                    if (api == null) {
                        throw new IllegalStateException("ExchangeWebSocketApiProvider returned null for exchange "
                                + exchange.getExchangeName());
                    }
                    websocketApis.put(exchange, api);
                }
            }
        }

        if (!apiType.isInstance(api)) {
            throw new IllegalStateException("Expected WebSocket API of type " + apiType.getName() + " for exchange "
                    + exchange.getExchangeName() + " but received " + api.getClass().getName());
        }
        return apiType.cast(api);
    }

    private static ExchangeWebSocketApiProvider<?> getProvider(Exchange exchange) {
        ExchangeWebSocketApiProvider<?> provider = providers.get(exchange);
        if (provider == null) {
            throw new IllegalStateException(
                    "No ExchangeWebSocketApiProvider registered for exchange " + exchange.getExchangeName());
        }
        return provider;
    }

    private static <T> void validateRequestedType(Exchange exchange, Class<T> apiType,
            ExchangeWebSocketApiProvider<?> provider) {
        Class<?> providerType = provider.getApiType();
        if (providerType == null) {
            throw new IllegalStateException(
                    "Provider API type is null for exchange " + exchange.getExchangeName());
        }
        if (!apiType.isAssignableFrom(providerType)) {
            throw new IllegalStateException("Requested WebSocket API type " + apiType.getName() + " for exchange "
                    + exchange.getExchangeName() + " but provider declares " + providerType.getName());
        }
    }

    private static void ensureProvidersLoaded() {
        if (providersLoaded) {
            return;
        }
        synchronized (ExchangeWebSocketApiFactory.class) {
            if (providersLoaded) {
                return;
            }
            ServiceLoader<ExchangeWebSocketApiProvider> loader = ServiceLoader.load(ExchangeWebSocketApiProvider.class);
            for (ExchangeWebSocketApiProvider<?> provider : loader) {
                try {
                    Exchange exchange = provider.getExchange();
                    if (exchange == null) {
                        logger.warn("ExchangeWebSocketApiProvider {} returned null exchange",
                                provider.getClass().getName());
                        continue;
                    }
                    if (provider.getApiType() == null) {
                        logger.warn("ExchangeWebSocketApiProvider {} returned null API type",
                                provider.getClass().getName());
                        continue;
                    }
                    providers.putIfAbsent(exchange, provider);
                } catch (RuntimeException e) {
                    logger.warn("Failed to register ExchangeWebSocketApiProvider: {}",
                            provider.getClass().getName(), e);
                }
            }
            providersLoaded = true;
        }
    }
}
