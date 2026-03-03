package com.fueledbychai.deribit.common.api;

/**
 * Centralized configuration holder for Deribit public API access.
 */
public class DeribitConfiguration {

    protected static final String PROD_ENVIRONMENT = "prod";
    protected static final String TEST_ENVIRONMENT = "test";
    protected static final String PROD_REST_URL = "https://www.deribit.com/api/v2";
    protected static final String TEST_REST_URL = "https://test.deribit.com/api/v2";
    protected static final String PROD_WS_URL = "wss://www.deribit.com/ws/api/v2";
    protected static final String TEST_WS_URL = "wss://test.deribit.com/ws/api/v2";

    private static volatile DeribitConfiguration instance;
    private static final Object LOCK = new Object();

    public static DeribitConfiguration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new DeribitConfiguration();
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
        return System.getProperty("deribit.environment", PROD_ENVIRONMENT).trim().toLowerCase();
    }

    public String getRestUrl() {
        String configured = System.getProperty("deribit.rest.url");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (TEST_ENVIRONMENT.equals(getEnvironment())) {
            return TEST_REST_URL;
        }
        return PROD_REST_URL;
    }

    public String getWebSocketUrl() {
        String configured = System.getProperty("deribit.ws.url");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (TEST_ENVIRONMENT.equals(getEnvironment())) {
            return TEST_WS_URL;
        }
        return PROD_WS_URL;
    }
}
