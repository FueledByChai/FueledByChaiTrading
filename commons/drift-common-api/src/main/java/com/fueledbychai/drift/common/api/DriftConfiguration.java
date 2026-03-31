package com.fueledbychai.drift.common.api;

public class DriftConfiguration {

    public static final String DRIFT_ENVIRONMENT = "drift.environment";
    public static final String DRIFT_DATA_REST_URL = "drift.data.rest.url";
    public static final String DRIFT_DATA_WS_URL = "drift.data.ws.url";
    public static final String DRIFT_DLOB_REST_URL = "drift.dlob.rest.url";
    public static final String DRIFT_DLOB_WS_URL = "drift.dlob.ws.url";
    public static final String DRIFT_GATEWAY_REST_URL = "drift.gateway.rest.url";
    public static final String DRIFT_GATEWAY_WS_URL = "drift.gateway.ws.url";
    public static final String DRIFT_SUB_ACCOUNT_ID = "drift.sub.account.id";
    public static final String DRIFT_GATEWAY_AUTO_START = "drift.gateway.auto.start";
    public static final String DRIFT_GATEWAY_KEY = "drift.gateway.key";
    public static final String DRIFT_GATEWAY_RPC_URL = "drift.gateway.rpc.url";
    public static final String DRIFT_GATEWAY_MARKETS = "drift.gateway.markets";
    public static final String DRIFT_GATEWAY_IMAGE = "drift.gateway.image";
    public static final String DRIFT_GATEWAY_CONTAINER_NAME = "drift.gateway.container.name";
    public static final String DRIFT_GATEWAY_REST_PORT = "drift.gateway.rest.port";
    public static final String DRIFT_GATEWAY_WS_PORT = "drift.gateway.ws.port";

    public static final String DEFAULT_ENVIRONMENT = "mainnet";
    public static final String DEFAULT_DATA_REST_URL = "https://data.api.drift.trade";
    public static final String DEFAULT_DATA_WS_URL = "wss://data.api.drift.trade/ws";
    public static final String DEFAULT_DLOB_REST_URL = "https://dlob.drift.trade";
    public static final String DEFAULT_DLOB_WS_URL = "wss://dlob.drift.trade/ws";
    public static final String DEFAULT_GATEWAY_REST_URL = "http://127.0.0.1:8080";
    public static final String DEFAULT_GATEWAY_WS_URL = "ws://127.0.0.1:1337";
    public static final int DEFAULT_SUB_ACCOUNT_ID = 0;
    public static final boolean DEFAULT_GATEWAY_AUTO_START = false;
    public static final String DEFAULT_GATEWAY_IMAGE = "ghcr.io/drift-labs/gateway:latest";
    public static final String DEFAULT_GATEWAY_CONTAINER_NAME = "drift-gateway";
    public static final int DEFAULT_GATEWAY_REST_PORT = 8080;
    public static final int DEFAULT_GATEWAY_WS_PORT = 1337;

    private static volatile DriftConfiguration instance;
    private static final Object LOCK = new Object();

    public static DriftConfiguration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new DriftConfiguration();
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
        return System.getProperty(DRIFT_ENVIRONMENT, DEFAULT_ENVIRONMENT);
    }

    public String getDataRestUrl() {
        return System.getProperty(DRIFT_DATA_REST_URL, DEFAULT_DATA_REST_URL);
    }

    public String getDataWebSocketUrl() {
        return System.getProperty(DRIFT_DATA_WS_URL, DEFAULT_DATA_WS_URL);
    }

    public String getDlobRestUrl() {
        return System.getProperty(DRIFT_DLOB_REST_URL, DEFAULT_DLOB_REST_URL);
    }

    public String getDlobWebSocketUrl() {
        return System.getProperty(DRIFT_DLOB_WS_URL, DEFAULT_DLOB_WS_URL);
    }

    public String getGatewayRestUrl() {
        return System.getProperty(DRIFT_GATEWAY_REST_URL, DEFAULT_GATEWAY_REST_URL);
    }

    public String getGatewayWebSocketUrl() {
        return System.getProperty(DRIFT_GATEWAY_WS_URL, DEFAULT_GATEWAY_WS_URL);
    }

    public int getSubAccountId() {
        return Integer.parseInt(System.getProperty(DRIFT_SUB_ACCOUNT_ID, String.valueOf(DEFAULT_SUB_ACCOUNT_ID)));
    }

    public boolean hasGatewayConfiguration() {
        String restUrl = getGatewayRestUrl();
        String wsUrl = getGatewayWebSocketUrl();
        return restUrl != null && !restUrl.isBlank() && wsUrl != null && !wsUrl.isBlank();
    }

    public boolean isGatewayAutoStart() {
        return Boolean.parseBoolean(System.getProperty(DRIFT_GATEWAY_AUTO_START,
                String.valueOf(DEFAULT_GATEWAY_AUTO_START)));
    }

    public String getGatewayKey() {
        return System.getProperty(DRIFT_GATEWAY_KEY);
    }

    public String getGatewayRpcUrl() {
        return System.getProperty(DRIFT_GATEWAY_RPC_URL);
    }

    public String getGatewayMarkets() {
        return System.getProperty(DRIFT_GATEWAY_MARKETS);
    }

    public String getGatewayImage() {
        return System.getProperty(DRIFT_GATEWAY_IMAGE, DEFAULT_GATEWAY_IMAGE);
    }

    public String getGatewayContainerName() {
        return System.getProperty(DRIFT_GATEWAY_CONTAINER_NAME, DEFAULT_GATEWAY_CONTAINER_NAME);
    }

    public int getGatewayRestPort() {
        return Integer.parseInt(System.getProperty(DRIFT_GATEWAY_REST_PORT,
                String.valueOf(DEFAULT_GATEWAY_REST_PORT)));
    }

    public int getGatewayWsPort() {
        return Integer.parseInt(System.getProperty(DRIFT_GATEWAY_WS_PORT,
                String.valueOf(DEFAULT_GATEWAY_WS_PORT)));
    }
}
