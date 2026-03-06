package com.fueledbychai.okx.common.api;

/**
 * Centralized configuration holder for OKX API access.
 */
public class OkxConfiguration {

    protected static final String PROD_ENVIRONMENT = "prod";
    protected static final String TEST_ENVIRONMENT = "test";
    protected static final String PROD_REST_URL = "https://www.okx.com/api/v5";
    protected static final String TEST_REST_URL = "https://www.okx.com/api/v5";
    protected static final String PROD_WS_URL = "wss://ws.okx.com:8443/ws/v5/public";
    protected static final String TEST_WS_URL = "wss://wspap.okx.com:8443/ws/v5/public";

    private static volatile OkxConfiguration instance;
    private static final Object LOCK = new Object();

    public static OkxConfiguration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new OkxConfiguration();
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

    public String getEnvironment() {
        return System.getProperty("okx.environment", PROD_ENVIRONMENT).trim().toLowerCase();
    }

    public String getRestUrl() {
        String configured = System.getProperty("okx.rest.url");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (TEST_ENVIRONMENT.equals(getEnvironment())) {
            return TEST_REST_URL;
        }
        return PROD_REST_URL;
    }

    public String getWebSocketUrl() {
        String configured = System.getProperty("okx.ws.url");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (TEST_ENVIRONMENT.equals(getEnvironment())) {
            return TEST_WS_URL;
        }
        return PROD_WS_URL;
    }

    public String getAccountAddress() {
        return System.getProperty("okx.account.address");
    }

    public String getPrivateKey() {
        return System.getProperty("okx.private.key");
    }

    public boolean hasPrivateKeyConfiguration() {
        String account = getAccountAddress();
        String key = getPrivateKey();
        return account != null && !account.trim().isEmpty() && key != null && !key.trim().isEmpty();
    }
}
