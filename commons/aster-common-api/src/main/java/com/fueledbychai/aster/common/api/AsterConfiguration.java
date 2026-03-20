package com.fueledbychai.aster.common.api;

public class AsterConfiguration {

    public static final String ASTER_ENVIRONMENT = "aster.environment";
    public static final String ASTER_MAINNET_REST_URL = "aster.mainnet.rest.url";
    public static final String ASTER_MAINNET_WS_URL = "aster.mainnet.ws.url";
    public static final String ASTER_TESTNET_REST_URL = "aster.testnet.rest.url";
    public static final String ASTER_TESTNET_WS_URL = "aster.testnet.ws.url";
    public static final String ASTER_SPOT_MAINNET_REST_URL = "aster.spot.mainnet.rest.url";
    public static final String ASTER_SPOT_MAINNET_WS_URL = "aster.spot.mainnet.ws.url";
    public static final String ASTER_SPOT_TESTNET_REST_URL = "aster.spot.testnet.rest.url";
    public static final String ASTER_SPOT_TESTNET_WS_URL = "aster.spot.testnet.ws.url";
    public static final String ASTER_API_KEY = "aster.api.key";
    public static final String ASTER_API_SECRET = "aster.api.secret";
    public static final String ASTER_RECV_WINDOW = "aster.recv.window";

    private static final String DEFAULT_ENVIRONMENT = "prod";
    private static final String DEFAULT_MAINNET_REST_URL = "https://fapi.asterdex.com";
    private static final String DEFAULT_MAINNET_WS_URL = "wss://fstream.asterdex.com/ws";
    private static final String DEFAULT_TESTNET_REST_URL = DEFAULT_MAINNET_REST_URL;
    private static final String DEFAULT_TESTNET_WS_URL = DEFAULT_MAINNET_WS_URL;
    private static final String DEFAULT_SPOT_MAINNET_REST_URL = "https://sapi.asterdex.com";
    private static final String DEFAULT_SPOT_MAINNET_WS_URL = "wss://sstream.asterdex.com/ws";
    private static final String DEFAULT_SPOT_TESTNET_REST_URL = DEFAULT_SPOT_MAINNET_REST_URL;
    private static final String DEFAULT_SPOT_TESTNET_WS_URL = DEFAULT_SPOT_MAINNET_WS_URL;
    private static final long DEFAULT_RECV_WINDOW = 5_000L;

    private static volatile AsterConfiguration instance;
    private static final Object LOCK = new Object();

    private final String environment;
    private final String restUrl;
    private final String webSocketUrl;
    private final String spotRestUrl;
    private final String spotWebSocketUrl;
    private final String apiKey;
    private final String apiSecret;
    private final long recvWindow;

    public static AsterConfiguration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new AsterConfiguration();
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

    private AsterConfiguration() {
        this.environment = read(ASTER_ENVIRONMENT, DEFAULT_ENVIRONMENT);
        boolean production = isProductionEnvironment(this.environment);
        this.restUrl = production ? read(ASTER_MAINNET_REST_URL, DEFAULT_MAINNET_REST_URL)
                : read(ASTER_TESTNET_REST_URL, DEFAULT_TESTNET_REST_URL);
        this.webSocketUrl = production ? read(ASTER_MAINNET_WS_URL, DEFAULT_MAINNET_WS_URL)
                : read(ASTER_TESTNET_WS_URL, DEFAULT_TESTNET_WS_URL);
        this.spotRestUrl = production ? read(ASTER_SPOT_MAINNET_REST_URL, DEFAULT_SPOT_MAINNET_REST_URL)
                : read(ASTER_SPOT_TESTNET_REST_URL, DEFAULT_SPOT_TESTNET_REST_URL);
        this.spotWebSocketUrl = production ? read(ASTER_SPOT_MAINNET_WS_URL, DEFAULT_SPOT_MAINNET_WS_URL)
                : read(ASTER_SPOT_TESTNET_WS_URL, DEFAULT_SPOT_TESTNET_WS_URL);
        this.apiKey = read(ASTER_API_KEY, null);
        this.apiSecret = read(ASTER_API_SECRET, null);
        this.recvWindow = readLong(ASTER_RECV_WINDOW, DEFAULT_RECV_WINDOW);
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

    public String getEnvironment() {
        return environment;
    }

    public String getRestUrl() {
        return restUrl;
    }

    public String getWebSocketUrl() {
        return webSocketUrl;
    }

    public String getSpotRestUrl() {
        return spotRestUrl;
    }

    public String getSpotWebSocketUrl() {
        return spotWebSocketUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public long getRecvWindow() {
        return recvWindow;
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
