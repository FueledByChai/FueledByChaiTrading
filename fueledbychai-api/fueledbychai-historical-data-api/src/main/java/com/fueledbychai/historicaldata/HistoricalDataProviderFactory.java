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
package com.fueledbychai.historicaldata;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;

public final class HistoricalDataProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger(HistoricalDataProviderFactory.class);
    private static final Map<Exchange, IHistoricalDataProvider> registry = new ConcurrentHashMap<>();
    private static final Map<Exchange, HistoricalDataProviderProvider> providers = new ConcurrentHashMap<>();
    private static volatile boolean providersLoaded = false;

    private HistoricalDataProviderFactory() {
    }

    public static void registerProvider(Exchange exchange, IHistoricalDataProvider provider) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        if (provider == null) {
            throw new IllegalArgumentException("HistoricalDataProvider is required");
        }
        IHistoricalDataProvider existing = registry.putIfAbsent(exchange, provider);
        if (existing != null && !existing.equals(provider)) {
            throw new IllegalStateException("HistoricalDataProvider already registered for "
                    + exchange.getExchangeName());
        }
    }

    public static boolean isRegistered(Exchange exchange) {
        if (exchange == null) {
            return false;
        }
        ensureProvidersLoaded();
        return registry.containsKey(exchange) || providers.containsKey(exchange);
    }

    public static IHistoricalDataProvider getInstance(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        ensureProvidersLoaded();
        IHistoricalDataProvider provider = registry.get(exchange);
        if (provider != null) {
            return provider;
        }
        HistoricalDataProviderProvider loaderProvider = providers.get(exchange);
        if (loaderProvider == null) {
            throw new IllegalStateException(
                    "No HistoricalDataProvider registered for exchange " + exchange.getExchangeName());
        }
        synchronized (HistoricalDataProviderFactory.class) {
            provider = registry.get(exchange);
            if (provider != null) {
                return provider;
            }
            IHistoricalDataProvider created = loaderProvider.getProvider();
            if (created == null) {
                throw new IllegalStateException(
                        "HistoricalDataProvider provider returned null for exchange " + exchange.getExchangeName());
            }
            registerProvider(exchange, created);
            return created;
        }
    }

    private static void ensureProvidersLoaded() {
        if (providersLoaded) {
            return;
        }
        synchronized (HistoricalDataProviderFactory.class) {
            if (providersLoaded) {
                return;
            }
            ServiceLoader<HistoricalDataProviderProvider> loader = ServiceLoader.load(
                    HistoricalDataProviderProvider.class);
            for (HistoricalDataProviderProvider provider : loader) {
                try {
                    Exchange exchange = provider.getExchange();
                    if (exchange == null) {
                        logger.warn("HistoricalDataProvider {} returned null exchange",
                                provider.getClass().getName());
                        continue;
                    }
                    providers.putIfAbsent(exchange, provider);
                } catch (RuntimeException e) {
                    logger.warn("Failed to register HistoricalData provider: {}", provider.getClass().getName(), e);
                }
            }
            providersLoaded = true;
        }
    }
}
