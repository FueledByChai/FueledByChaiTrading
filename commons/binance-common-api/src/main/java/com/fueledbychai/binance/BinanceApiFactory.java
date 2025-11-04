package com.fueledbychai.binance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton factory for ParadexRestApi instances with centralized configuration
 * management. Provides thread-safe access to configured API instances without
 * requiring users to know configuration details.
 */
public class BinanceApiFactory {
    private static final Logger logger = LoggerFactory.getLogger(BinanceApiFactory.class);

    private static volatile IBinanceRestApi publicApiInstance;
    private static volatile IBinanceRestApi privateApiInstance;
    private static final Object lock = new Object();

    private BinanceApiFactory() {
        // Prevent instantiation
    }

    /**
     * Get a public API instance that can only access public endpoints. Uses
     * centralized configuration for URL settings.
     * 
     * @return BinanceRestApi instance for public endpoints
     */
    public static IBinanceRestApi getPublicApi() {
        if (publicApiInstance == null) {
            synchronized (lock) {
                if (publicApiInstance == null) {
                    BinanceConfiguration config = BinanceConfiguration.getInstance();
                    publicApiInstance = BinanceRestApi.getPublicOnlyApi(config.getRestUrl());

                    logger.info("Created public BinanceRestApi instance for URL: {}", config.getRestUrl());
                }
            }
        }
        return publicApiInstance;
    }

    /**
     * Get a private API instance that can access both public and private endpoints.
     * Uses centralized configuration for URL, account address, and private key.
     * 
     * @return ParadexRestApi instance for private endpoints
     * @throws IllegalStateException if private key configuration is not available
     */
    public static IBinanceRestApi getPrivateApi() {
        if (privateApiInstance == null) {
            synchronized (lock) {
                if (privateApiInstance == null) {
                    BinanceConfiguration config = BinanceConfiguration.getInstance();

                    if (!config.hasPrivateKeyConfiguration()) {
                        throw new IllegalStateException("Private key configuration not available. Please set "
                                + BinanceConfiguration.BINANCE_ACCOUNT_ADDRESS + " and "
                                + BinanceConfiguration.BINANCE_PRIVATE_KEY + " properties.");
                    }

                    privateApiInstance = BinanceRestApi.getPrivateApi(config.getRestUrl(), config.getAccountAddress(),
                            config.getPrivateKey());

                    logger.info("Created private BinanceRestApi instance for URL: {} and address: {}",
                            config.getRestUrl(), config.getAccountAddress());
                }
            }
        }
        return privateApiInstance;
    }

    /**
     * Get the appropriate API instance based on available configuration. Returns
     * private API if credentials are configured, otherwise returns public API.
     * 
     * @return BinanceRestApi instance (private if configured, otherwise public)
     */
    public static IBinanceRestApi getApi() {
        BinanceConfiguration config = BinanceConfiguration.getInstance();
        if (config.hasPrivateKeyConfiguration()) {
            return getPrivateApi();
        } else {
            logger.info("Private key not configured, returning public API instance");
            return getPublicApi();
        }
    }

    /**
     * Check if private API configuration is available.
     * 
     * @return true if private key and account address are configured
     */
    public static boolean isPrivateApiAvailable() {
        return BinanceConfiguration.getInstance().hasPrivateKeyConfiguration();
    }

    /**
     * Get the WebSocket URL from configuration.
     * 
     * @return WebSocket URL for the current environment
     */
    public static String getWebSocketUrl() {
        return BinanceConfiguration.getInstance().getWebSocketUrl();
    }

    /**
     * Get the current environment (testnet/production).
     * 
     * @return environment name
     */
    public static String getEnvironment() {
        return BinanceConfiguration.getInstance().getEnvironment();
    }

    /**
     * Reset all cached instances (useful for testing or configuration changes).
     * Note: This will force recreation of instances on next access.
     */
    public static void reset() {
        synchronized (lock) {
            publicApiInstance = null;
            privateApiInstance = null;
            BinanceConfiguration.reset();
            logger.info("Reset all BinanceApi instances and configuration");
        }
    }

    /**
     * Get configuration details for debugging.
     * 
     * @return configuration string (without sensitive information)
     */
    public static String getConfigurationInfo() {
        BinanceConfiguration config = BinanceConfiguration.getInstance();
        return String.format("Environment: %s, REST URL: %s, WebSocket URL: %s, Private API Available: %s",
                config.getEnvironment(), config.getRestUrl(), config.getWebSocketUrl(),
                config.hasPrivateKeyConfiguration());
    }
}
