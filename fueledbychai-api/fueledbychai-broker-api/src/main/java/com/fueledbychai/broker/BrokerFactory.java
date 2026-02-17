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
package com.fueledbychai.broker;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;

public final class BrokerFactory {

    private static final Logger logger = LoggerFactory.getLogger(BrokerFactory.class);
    private static final Map<Exchange, IBroker> registry = new ConcurrentHashMap<>();
    private static final Map<Exchange, BrokerProvider> providers = new ConcurrentHashMap<>();
    private static volatile boolean providersLoaded = false;

    private BrokerFactory() {
    }

    public static void registerBroker(Exchange exchange, IBroker broker) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        if (broker == null) {
            throw new IllegalArgumentException("Broker is required");
        }
        IBroker existing = registry.putIfAbsent(exchange, broker);
        if (existing != null && !existing.equals(broker)) {
            throw new IllegalStateException("Broker already registered for " + exchange.getExchangeName());
        }
    }

    public static boolean isRegistered(Exchange exchange) {
        if (exchange == null) {
            return false;
        }
        ensureProvidersLoaded();
        return registry.containsKey(exchange) || providers.containsKey(exchange);
    }

    public static IBroker getInstance(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        ensureProvidersLoaded();
        IBroker broker = registry.get(exchange);
        if (broker != null) {
            return broker;
        }
        BrokerProvider provider = providers.get(exchange);
        if (provider == null) {
            throw new IllegalStateException("No Broker registered for exchange " + exchange.getExchangeName());
        }
        synchronized (BrokerFactory.class) {
            broker = registry.get(exchange);
            if (broker != null) {
                return broker;
            }
            IBroker created = provider.getBroker();
            if (created == null) {
                throw new IllegalStateException(
                        "Broker provider returned null for exchange " + exchange.getExchangeName());
            }
            registerBroker(exchange, created);
            return created;
        }
    }

    private static void ensureProvidersLoaded() {
        if (providersLoaded) {
            return;
        }
        synchronized (BrokerFactory.class) {
            if (providersLoaded) {
                return;
            }
            ServiceLoader<BrokerProvider> loader = ServiceLoader.load(BrokerProvider.class);
            for (BrokerProvider provider : loader) {
                try {
                    Exchange exchange = provider.getExchange();
                    if (exchange == null) {
                        logger.warn("BrokerProvider {} returned null exchange", provider.getClass().getName());
                        continue;
                    }
                    providers.putIfAbsent(exchange, provider);
                } catch (RuntimeException e) {
                    logger.warn("Failed to register Broker provider: {}", provider.getClass().getName(), e);
                }
            }
            providersLoaded = true;
        }
    }
}
