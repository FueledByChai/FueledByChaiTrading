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

public final class ExchangeCapabilitiesRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeCapabilitiesRegistry.class);
    private static final Map<Exchange, ExchangeCapabilities> registry = new ConcurrentHashMap<>();
    private static final Map<Exchange, ExchangeCapabilitiesProvider> providers = new ConcurrentHashMap<>();
    private static volatile boolean providersLoaded = false;

    private ExchangeCapabilitiesRegistry() {
    }

    public static void registerCapabilities(Exchange exchange, ExchangeCapabilities capabilities) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("ExchangeCapabilities is required");
        }
        ExchangeCapabilities existing = registry.putIfAbsent(exchange, capabilities);
        if (existing != null && !existing.equals(capabilities)) {
            throw new IllegalStateException("ExchangeCapabilities already registered for " + exchange.getExchangeName());
        }
    }

    public static boolean isRegistered(Exchange exchange) {
        if (exchange == null) {
            return false;
        }
        ensureProvidersLoaded();
        return registry.containsKey(exchange) || providers.containsKey(exchange);
    }

    public static ExchangeCapabilities getCapabilities(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        ensureProvidersLoaded();
        ExchangeCapabilities capabilities = registry.get(exchange);
        if (capabilities != null) {
            return capabilities;
        }
        ExchangeCapabilitiesProvider provider = providers.get(exchange);
        if (provider == null) {
            throw new IllegalStateException(
                    "No ExchangeCapabilities registered for exchange " + exchange.getExchangeName());
        }
        synchronized (ExchangeCapabilitiesRegistry.class) {
            capabilities = registry.get(exchange);
            if (capabilities != null) {
                return capabilities;
            }
            ExchangeCapabilities created = provider.getCapabilities();
            if (created == null) {
                throw new IllegalStateException(
                        "ExchangeCapabilities provider returned null for exchange " + exchange.getExchangeName());
            }
            registerCapabilities(exchange, created);
            return created;
        }
    }

    private static void ensureProvidersLoaded() {
        if (providersLoaded) {
            return;
        }
        synchronized (ExchangeCapabilitiesRegistry.class) {
            if (providersLoaded) {
                return;
            }
            ServiceLoader<ExchangeCapabilitiesProvider> loader = ServiceLoader.load(ExchangeCapabilitiesProvider.class);
            for (ExchangeCapabilitiesProvider provider : loader) {
                try {
                    Exchange exchange = provider.getExchange();
                    if (exchange == null) {
                        logger.warn("ExchangeCapabilitiesProvider {} returned null exchange",
                                provider.getClass().getName());
                        continue;
                    }
                    providers.putIfAbsent(exchange, provider);
                } catch (RuntimeException e) {
                    logger.warn("Failed to register ExchangeCapabilities provider: {}", provider.getClass().getName(),
                            e);
                }
            }
            providersLoaded = true;
        }
    }
}
