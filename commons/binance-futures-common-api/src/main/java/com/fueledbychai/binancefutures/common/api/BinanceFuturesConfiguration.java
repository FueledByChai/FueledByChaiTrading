package com.fueledbychai.binancefutures.common.api;

/**
 * Centralized configuration for Binance futures and options market data.
 */
public class BinanceFuturesConfiguration {

    public static final String BINANCE_FUTURES_ENVIRONMENT = "binance.futures.environment";
    public static final String BINANCE_FUTURES_MAINNET_REST_URL = "binance.futures.mainnet.rest.url";
    public static final String BINANCE_FUTURES_MAINNET_WS_URL = "binance.futures.mainnet.ws.url";
    public static final String BINANCE_FUTURES_TESTNET_REST_URL = "binance.futures.testnet.rest.url";
    public static final String BINANCE_FUTURES_TESTNET_WS_URL = "binance.futures.testnet.ws.url";
    public static final String BINANCE_FUTURES_API_KEY = "binance.futures.api.key";
    public static final String BINANCE_FUTURES_API_SECRET = "binance.futures.api.secret";
    public static final String BINANCE_OPTIONS_MAINNET_REST_URL = "binance.options.mainnet.rest.url";
    public static final String BINANCE_OPTIONS_MAINNET_WS_URL = "binance.options.mainnet.ws.url";
    public static final String BINANCE_OPTIONS_TESTNET_REST_URL = "binance.options.testnet.rest.url";
    public static final String BINANCE_OPTIONS_TESTNET_WS_URL = "binance.options.testnet.ws.url";

    private static final String DEFAULT_ENVIRONMENT = "prod";
    private static final String DEFAULT_MAINNET_REST_URL = "https://fapi.binance.com";
    private static final String DEFAULT_MAINNET_WS_URL = "wss://fstream.binance.com/ws";
    private static final String DEFAULT_TESTNET_REST_URL = "https://testnet.binancefuture.com";
    private static final String DEFAULT_TESTNET_WS_URL = "wss://stream.binancefuture.com/ws";
    private static final String DEFAULT_OPTIONS_MAINNET_REST_URL = "https://eapi.binance.com";
    private static final String DEFAULT_OPTIONS_MAINNET_WS_URL = "wss://fstream.binance.com/public/ws";
    private static final String DEFAULT_OPTIONS_TESTNET_REST_URL = DEFAULT_OPTIONS_MAINNET_REST_URL;
    private static final String DEFAULT_OPTIONS_TESTNET_WS_URL = "wss://stream.binancefuture.com/public/ws";

    private static volatile BinanceFuturesConfiguration instance;
    private static final Object LOCK = new Object();

    private final String environment;
    private final String restUrl;
    private final String webSocketUrl;
    private final String optionsRestUrl;
    private final String optionsWebSocketUrl;
    private final String apiKey;
    private final String apiSecret;

    public static BinanceFuturesConfiguration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new BinanceFuturesConfiguration();
                }
            }
        }
        return instance;
    }

    public static void reset() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    private BinanceFuturesConfiguration() {
        this.environment = read(BINANCE_FUTURES_ENVIRONMENT, DEFAULT_ENVIRONMENT);
        boolean production = isProductionEnvironment(this.environment);
        this.restUrl = production
                ? read(BINANCE_FUTURES_MAINNET_REST_URL, DEFAULT_MAINNET_REST_URL)
                : read(BINANCE_FUTURES_TESTNET_REST_URL, DEFAULT_TESTNET_REST_URL);
        this.webSocketUrl = production
                ? read(BINANCE_FUTURES_MAINNET_WS_URL, DEFAULT_MAINNET_WS_URL)
                : read(BINANCE_FUTURES_TESTNET_WS_URL, DEFAULT_TESTNET_WS_URL);
        this.optionsRestUrl = production
                ? read(BINANCE_OPTIONS_MAINNET_REST_URL, DEFAULT_OPTIONS_MAINNET_REST_URL)
                : read(BINANCE_OPTIONS_TESTNET_REST_URL, DEFAULT_OPTIONS_TESTNET_REST_URL);
        this.optionsWebSocketUrl = production
                ? read(BINANCE_OPTIONS_MAINNET_WS_URL, DEFAULT_OPTIONS_MAINNET_WS_URL)
                : read(BINANCE_OPTIONS_TESTNET_WS_URL, DEFAULT_OPTIONS_TESTNET_WS_URL);
        this.apiKey = read(BINANCE_FUTURES_API_KEY, null);
        this.apiSecret = read(BINANCE_FUTURES_API_SECRET, null);
    }

    private static String read(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            String envKey = key.toUpperCase().replace('.', '_');
            value = System.getenv(envKey);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getRestUrl() {
        return restUrl;
    }

    public String getWebSocketUrl() {
        return webSocketUrl;
    }

    public String getOptionsRestUrl() {
        return optionsRestUrl;
    }

    public String getOptionsWebSocketUrl() {
        return optionsWebSocketUrl;
    }

    public String getAccountAddress() {
        return apiKey;
    }

    public String getPrivateKey() {
        return apiSecret;
    }

    public boolean hasPrivateKeyConfiguration() {
        return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }

    public boolean isProductionEnvironment() {
        return isProductionEnvironment(environment);
    }

    private static boolean isProductionEnvironment(String environment) {
        return environment == null || environment.isBlank() || "prod".equalsIgnoreCase(environment)
                || "production".equalsIgnoreCase(environment) || "mainnet".equalsIgnoreCase(environment);
    }
}
