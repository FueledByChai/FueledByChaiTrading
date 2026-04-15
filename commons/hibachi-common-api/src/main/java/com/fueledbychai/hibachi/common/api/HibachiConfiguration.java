package com.fueledbychai.hibachi.common.api;

public class HibachiConfiguration {

    public static final String HIBACHI_ENVIRONMENT = "hibachi.environment";
    public static final String HIBACHI_PROD_REST_URL = "hibachi.prod.rest.url";
    public static final String HIBACHI_PROD_DATA_REST_URL = "hibachi.prod.data.rest.url";
    public static final String HIBACHI_PROD_WS_MARKET_URL = "hibachi.prod.ws.market.url";
    public static final String HIBACHI_PROD_WS_ACCOUNT_URL = "hibachi.prod.ws.account.url";
    public static final String HIBACHI_PROD_WS_TRADE_URL = "hibachi.prod.ws.trade.url";
    public static final String HIBACHI_TESTNET_REST_URL = "hibachi.testnet.rest.url";
    public static final String HIBACHI_TESTNET_DATA_REST_URL = "hibachi.testnet.data.rest.url";
    public static final String HIBACHI_TESTNET_WS_MARKET_URL = "hibachi.testnet.ws.market.url";
    public static final String HIBACHI_TESTNET_WS_ACCOUNT_URL = "hibachi.testnet.ws.account.url";
    public static final String HIBACHI_TESTNET_WS_TRADE_URL = "hibachi.testnet.ws.trade.url";
    public static final String HIBACHI_API_KEY = "hibachi.api.key";
    public static final String HIBACHI_API_SECRET = "hibachi.api.secret";
    public static final String HIBACHI_ACCOUNT_ID = "hibachi.account.id";
    public static final String HIBACHI_PUBLIC_KEY = "hibachi.public.key";
    public static final String HIBACHI_PRIVATE_KEY = "hibachi.private.key";
    public static final String HIBACHI_CLIENT = "hibachi.client";
    public static final String HIBACHI_ACCOUNT_WS_PING_SECONDS = "hibachi.account.ws.ping.seconds";

    private static final String DEFAULT_ENVIRONMENT = "prod";
    private static final String DEFAULT_PROD_REST_URL = "https://api.hibachi.xyz";
    private static final String DEFAULT_PROD_DATA_REST_URL = "https://data-api.hibachi.xyz";
    private static final String DEFAULT_PROD_WS_MARKET_URL = "wss://data-api.hibachi.xyz/ws/market";
    private static final String DEFAULT_PROD_WS_ACCOUNT_URL = "wss://api.hibachi.xyz/ws/account";
    private static final String DEFAULT_PROD_WS_TRADE_URL = "wss://api.hibachi.xyz/ws/trade";
    private static final String DEFAULT_TESTNET_REST_URL = "https://api-test.hibachi.xyz";
    private static final String DEFAULT_TESTNET_DATA_REST_URL = "https://data-api-test.hibachi.xyz";
    private static final String DEFAULT_TESTNET_WS_MARKET_URL = "wss://data-api-test.hibachi.xyz/ws/market";
    private static final String DEFAULT_TESTNET_WS_ACCOUNT_URL = "wss://api-test.hibachi.xyz/ws/account";
    private static final String DEFAULT_TESTNET_WS_TRADE_URL = "wss://api-test.hibachi.xyz/ws/trade";
    private static final String DEFAULT_CLIENT = "FueledByChaiJavaSDK";
    private static final long DEFAULT_ACCOUNT_WS_PING_SECONDS = 14L;

    private static volatile HibachiConfiguration instance;
    private static final Object LOCK = new Object();

    private final String environment;
    private final String restUrl;
    private final String dataRestUrl;
    private final String marketWsUrl;
    private final String accountWsUrl;
    private final String tradeWsUrl;
    private final String apiKey;
    private final String apiSecret;
    private final String accountId;
    private final String publicKey;
    private final String privateKey;
    private final String client;
    private final long accountWsPingSeconds;

    public static HibachiConfiguration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new HibachiConfiguration();
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

    private HibachiConfiguration() {
        this.environment = read(HIBACHI_ENVIRONMENT, DEFAULT_ENVIRONMENT);
        boolean production = isProductionEnvironment(this.environment);
        this.restUrl = production ? read(HIBACHI_PROD_REST_URL, DEFAULT_PROD_REST_URL)
                : read(HIBACHI_TESTNET_REST_URL, DEFAULT_TESTNET_REST_URL);
        this.dataRestUrl = production ? read(HIBACHI_PROD_DATA_REST_URL, DEFAULT_PROD_DATA_REST_URL)
                : read(HIBACHI_TESTNET_DATA_REST_URL, DEFAULT_TESTNET_DATA_REST_URL);
        this.marketWsUrl = production ? read(HIBACHI_PROD_WS_MARKET_URL, DEFAULT_PROD_WS_MARKET_URL)
                : read(HIBACHI_TESTNET_WS_MARKET_URL, DEFAULT_TESTNET_WS_MARKET_URL);
        this.accountWsUrl = production ? read(HIBACHI_PROD_WS_ACCOUNT_URL, DEFAULT_PROD_WS_ACCOUNT_URL)
                : read(HIBACHI_TESTNET_WS_ACCOUNT_URL, DEFAULT_TESTNET_WS_ACCOUNT_URL);
        this.tradeWsUrl = production ? read(HIBACHI_PROD_WS_TRADE_URL, DEFAULT_PROD_WS_TRADE_URL)
                : read(HIBACHI_TESTNET_WS_TRADE_URL, DEFAULT_TESTNET_WS_TRADE_URL);
        this.apiKey = read(HIBACHI_API_KEY, null);
        this.apiSecret = read(HIBACHI_API_SECRET, null);
        this.accountId = read(HIBACHI_ACCOUNT_ID, null);
        this.publicKey = read(HIBACHI_PUBLIC_KEY, null);
        this.privateKey = read(HIBACHI_PRIVATE_KEY, null);
        this.client = read(HIBACHI_CLIENT, DEFAULT_CLIENT);
        this.accountWsPingSeconds = readLong(HIBACHI_ACCOUNT_WS_PING_SECONDS, DEFAULT_ACCOUNT_WS_PING_SECONDS);
    }

    private static String read(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static long readLong(String key, long defaultValue) {
        String value = read(key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public String getEnvironment() { return environment; }
    public String getRestUrl() { return restUrl; }
    public String getDataRestUrl() { return dataRestUrl; }
    public String getMarketWsUrl() { return marketWsUrl; }
    public String getAccountWsUrl() { return accountWsUrl; }
    public String getTradeWsUrl() { return tradeWsUrl; }
    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public String getAccountId() { return accountId; }
    public String getPublicKey() { return publicKey; }
    public String getPrivateKey() { return privateKey; }
    public String getClient() { return client; }
    public long getAccountWsPingSeconds() { return accountWsPingSeconds; }

    public boolean hasPrivateApiConfiguration() {
        return apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank()
                && accountId != null && !accountId.isBlank();
    }

    public boolean isProductionEnvironment() {
        return isProductionEnvironment(environment);
    }

    private static boolean isProductionEnvironment(String environment) {
        return environment == null || environment.isBlank() || "prod".equalsIgnoreCase(environment)
                || "production".equalsIgnoreCase(environment) || "mainnet".equalsIgnoreCase(environment);
    }
}
