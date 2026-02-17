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

public final class TickerRegistryFactory {

    private static final Logger logger = LoggerFactory.getLogger(TickerRegistryFactory.class);
    private static final Map<Exchange, ITickerRegistry> registry = new ConcurrentHashMap<>();
    private static final Map<Exchange, TickerRegistryProvider> providers = new ConcurrentHashMap<>();
    private static volatile boolean providersLoaded = false;

    private TickerRegistryFactory() {
    }

    public static void registerTickerRegistry(Exchange exchange, ITickerRegistry tickerRegistry) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        if (tickerRegistry == null) {
            throw new IllegalArgumentException("TickerRegistry is required");
        }
        ITickerRegistry existing = registry.putIfAbsent(exchange, tickerRegistry);
        if (existing != null && !existing.equals(tickerRegistry)) {
            throw new IllegalStateException("TickerRegistry already registered for " + exchange.getExchangeName());
        }
    }

    public static boolean isRegistered(Exchange exchange) {
        if (exchange == null) {
            return false;
        }
        ensureProvidersLoaded();
        return registry.containsKey(exchange) || providers.containsKey(exchange);
    }

    public static ITickerRegistry getInstance(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        ensureProvidersLoaded();
        ITickerRegistry tickerRegistry = registry.get(exchange);
        if (tickerRegistry != null) {
            return tickerRegistry;
        }
        TickerRegistryProvider provider = providers.get(exchange);
        if (provider == null) {
            throw new IllegalStateException("No TickerRegistry registered for exchange " + exchange.getExchangeName());
        }
        synchronized (TickerRegistryFactory.class) {
            tickerRegistry = registry.get(exchange);
            if (tickerRegistry != null) {
                return tickerRegistry;
            }
            ITickerRegistry created = provider.getRegistry();
            if (created == null) {
                throw new IllegalStateException("TickerRegistry provider returned null for exchange "
                        + exchange.getExchangeName());
            }
            registerTickerRegistry(exchange, created);
            return created;
        }
    }

    private static void ensureProvidersLoaded() {
        if (providersLoaded) {
            return;
        }
        synchronized (TickerRegistryFactory.class) {
            if (providersLoaded) {
                return;
            }
            ServiceLoader<TickerRegistryProvider> loader = ServiceLoader.load(TickerRegistryProvider.class);
            for (TickerRegistryProvider provider : loader) {
                try {
                    Exchange exchange = provider.getExchange();
                    if (exchange == null) {
                        logger.warn("TickerRegistryProvider {} returned null exchange", provider.getClass().getName());
                        continue;
                    }
                    providers.putIfAbsent(exchange, provider);
                } catch (RuntimeException e) {
                    logger.warn("Failed to register TickerRegistry provider: {}", provider.getClass().getName(), e);
                }
            }
            providersLoaded = true;
        }
    }
}
