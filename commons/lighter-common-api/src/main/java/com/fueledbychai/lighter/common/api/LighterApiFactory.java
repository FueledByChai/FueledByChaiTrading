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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton factory for LighterRestApi instances with centralized configuration
 * management. Provides thread-safe access to configured API instances without
 * requiring users to know configuration details.
 *
 * @deprecated Use {@link com.fueledbychai.util.ExchangeRestApiFactory} with
 *             {@code Exchange.LIGHTER} instead.
 */
@Deprecated(since = "0.2.0", forRemoval = false)
public class LighterApiFactory {
    private static final Logger logger = LoggerFactory.getLogger(LighterApiFactory.class);

    private static volatile ILighterRestApi publicApiInstance;
    private static volatile ILighterRestApi privateApiInstance;
    private static final Object lock = new Object();

    private LighterApiFactory() {
    }

    public static ILighterRestApi getPublicApi() {
        if (publicApiInstance == null) {
            synchronized (lock) {
                if (publicApiInstance == null) {
                    LighterConfiguration config = LighterConfiguration.getInstance();
                    publicApiInstance = new LighterRestApi(config.getRestUrl(), !config.isProductionEnvironment());
                    logger.info("Created public LighterRestApi instance for URL: {}", config.getRestUrl());
                }
            }
        }
        return publicApiInstance;
    }

    public static ILighterRestApi getPrivateApi() {
        if (privateApiInstance == null) {
            synchronized (lock) {
                if (privateApiInstance == null) {
                    LighterConfiguration config = LighterConfiguration.getInstance();
                    if (!config.hasPrivateKeyConfiguration()) {
                        throw new IllegalStateException(
                                "Private key configuration not available. Please set "
                                        + LighterConfiguration.LIGHTER_ACCOUNT_ADDRESS + " and "
                                        + LighterConfiguration.LIGHTER_PRIVATE_KEY + " properties.");
                    }
                    privateApiInstance = new LighterRestApi(config.getRestUrl(), config.getAccountAddress(),
                            config.getPrivateKey(), !config.isProductionEnvironment());
                    logger.info("Created private LighterRestApi instance for URL: {} and address: {}",
                            config.getRestUrl(), config.getAccountAddress());
                }
            }
        }
        return privateApiInstance;
    }

    public static ILighterRestApi getApi() {
        LighterConfiguration config = LighterConfiguration.getInstance();
        if (config.hasPrivateKeyConfiguration()) {
            return getPrivateApi();
        }
        logger.info("Private key not configured, returning public API instance");
        return getPublicApi();
    }

    public static boolean isPrivateApiAvailable() {
        return LighterConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    public static String getEnvironment() {
        return LighterConfiguration.getInstance().getEnvironment();
    }

    public static void reset() {
        synchronized (lock) {
            publicApiInstance = null;
            privateApiInstance = null;
            LighterConfiguration.reset();
            logger.info("Reset all LighterApi instances and configuration");
        }
    }
}
