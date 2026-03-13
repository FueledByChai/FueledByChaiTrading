package com.fueledbychai.bybit.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.websocket.ProxyConfig;

/**
 * Centralized configuration holder for Bybit API access.
 */
public class BybitConfiguration {

    protected static final Logger logger = LoggerFactory.getLogger(BybitConfiguration.class);

    public static final String BYBIT_RUN_PROXY = "bybit.run.proxy";
    public static final String BYBIT_PROXY_HOST = "bybit.proxy.host";
    public static final String BYBIT_PROXY_PORT = "bybit.proxy.port";

    protected static final String PROD_ENVIRONMENT = "prod";
    protected static final String TEST_ENVIRONMENT = "test";

    protected static final String PROD_REST_URL = "https://api.bybit.com/v5";
    protected static final String TEST_REST_URL = "https://api-testnet.bybit.com/v5";

    protected static final String PROD_WS_SPOT_URL = "wss://stream.bybit.com/v5/public/spot";
    protected static final String PROD_WS_LINEAR_URL = "wss://stream.bybit.com/v5/public/linear";
    protected static final String PROD_WS_INVERSE_URL = "wss://stream.bybit.com/v5/public/inverse";
    protected static final String PROD_WS_OPTION_URL = "wss://stream.bybit.com/v5/public/option";

    protected static final String TEST_WS_SPOT_URL = "wss://stream-testnet.bybit.com/v5/public/spot";
    protected static final String TEST_WS_LINEAR_URL = "wss://stream-testnet.bybit.com/v5/public/linear";
    protected static final String TEST_WS_INVERSE_URL = "wss://stream-testnet.bybit.com/v5/public/inverse";
    protected static final String TEST_WS_OPTION_URL = "wss://stream-testnet.bybit.com/v5/public/option";

    protected static final String DEFAULT_PROXY_HOST = "127.0.0.1";
    protected static final int DEFAULT_PROXY_PORT = 1080;

    private static volatile BybitConfiguration instance;
    private static final Object LOCK = new Object();

    private BybitConfiguration() {
        configureProxySetting();
    }

    public static BybitConfiguration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new BybitConfiguration();
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
        return System.getProperty("bybit.environment", PROD_ENVIRONMENT).trim().toLowerCase();
    }

    public String getRestUrl() {
        String configured = System.getProperty("bybit.rest.url");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return TEST_ENVIRONMENT.equals(getEnvironment()) ? TEST_REST_URL : PROD_REST_URL;
    }

    public String getWebSocketUrl(BybitWsCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }

        String globalOverride = System.getProperty("bybit.ws.url");
        if (globalOverride != null && !globalOverride.isBlank()) {
            return globalOverride.trim();
        }

        String categoryOverride = switch (category) {
            case SPOT -> System.getProperty("bybit.ws.spot.url");
            case LINEAR -> System.getProperty("bybit.ws.linear.url");
            case INVERSE -> System.getProperty("bybit.ws.inverse.url");
            case OPTION -> System.getProperty("bybit.ws.option.url");
        };
        if (categoryOverride != null && !categoryOverride.isBlank()) {
            return categoryOverride.trim();
        }

        boolean test = TEST_ENVIRONMENT.equals(getEnvironment());
        if (test) {
            return switch (category) {
                case SPOT -> TEST_WS_SPOT_URL;
                case LINEAR -> TEST_WS_LINEAR_URL;
                case INVERSE -> TEST_WS_INVERSE_URL;
                case OPTION -> TEST_WS_OPTION_URL;
            };
        }
        return switch (category) {
            case SPOT -> PROD_WS_SPOT_URL;
            case LINEAR -> PROD_WS_LINEAR_URL;
            case INVERSE -> PROD_WS_INVERSE_URL;
            case OPTION -> PROD_WS_OPTION_URL;
        };
    }

    public String getApiKey() {
        return System.getProperty("bybit.api.key");
    }

    public String getApiSecret() {
        return System.getProperty("bybit.api.secret");
    }

    public boolean hasPrivateKeyConfiguration() {
        String apiKey = getApiKey();
        String apiSecret = getApiSecret();
        return apiKey != null && !apiKey.trim().isEmpty() && apiSecret != null && !apiSecret.trim().isEmpty();
    }

    protected void configureProxySetting() {
        if (ProxyConfig.getInstance().isGlobalProxyEnabled()) {
            ProxyConfig.getInstance().getProxy();
            logger.info("Bybit proxy enabled via global settings: {}:{}", ProxyConfig.getInstance().getGlobalProxyHost(),
                    ProxyConfig.getInstance().getGlobalProxyPort());
            return;
        }

        String runProxyText = System.getProperty(BYBIT_RUN_PROXY);
        if (isBlank(runProxyText)) {
            return;
        }

        boolean runProxy = Boolean.parseBoolean(runProxyText.trim());
        if (!runProxy) {
            ProxyConfig.getInstance().setRunningLocally(false);
            logger.info("Bybit proxy disabled");
            return;
        }

        String host = firstNonBlank(System.getProperty(BYBIT_PROXY_HOST), System.getProperty("socksProxyHost"),
                DEFAULT_PROXY_HOST);
        int port = parsePort(
                firstNonBlank(System.getProperty(BYBIT_PROXY_PORT), System.getProperty("socksProxyPort"),
                        String.valueOf(DEFAULT_PROXY_PORT)),
                DEFAULT_PROXY_PORT);

        ProxyConfig.getInstance().setSocksProxy(host, port);
        logger.info("Bybit proxy enabled: {}:{}", host, port);
    }

    protected int parsePort(String text, int fallback) {
        if (isBlank(text)) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(text.trim());
            if (parsed > 0 && parsed <= 65535) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        logger.warn("Invalid Bybit proxy port '{}', falling back to {}", text, fallback);
        return fallback;
    }

    protected String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (!isBlank(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
