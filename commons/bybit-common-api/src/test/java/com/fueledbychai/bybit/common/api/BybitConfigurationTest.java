package com.fueledbychai.bybit.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.Proxy;

import org.junit.jupiter.api.Test;

import com.fueledbychai.websocket.ProxyConfig;

class BybitConfigurationTest {

    @Test
    void readsConfiguredProperties() {
        String environmentKey = "bybit.environment";
        String restUrlKey = "bybit.rest.url";
        String wsSpotKey = "bybit.ws.spot.url";
        String apiKeyKey = "bybit.api.key";
        String apiSecretKey = "bybit.api.secret";

        String previousEnvironment = System.getProperty(environmentKey);
        String previousRestUrl = System.getProperty(restUrlKey);
        String previousSpotUrl = System.getProperty(wsSpotKey);
        String previousApiKey = System.getProperty(apiKeyKey);
        String previousApiSecret = System.getProperty(apiSecretKey);

        try {
            System.setProperty(environmentKey, "test");
            System.setProperty(restUrlKey, "https://api.unit.test/v5");
            System.setProperty(wsSpotKey, "wss://stream.unit.test/spot");
            System.setProperty(apiKeyKey, "unit-api-key");
            System.setProperty(apiSecretKey, "unit-api-secret");

            BybitConfiguration.reset();
            BybitConfiguration config = BybitConfiguration.getInstance();

            assertEquals("test", config.getEnvironment());
            assertEquals("https://api.unit.test/v5", config.getRestUrl());
            assertEquals("wss://stream.unit.test/spot", config.getWebSocketUrl(BybitWsCategory.SPOT));
            assertEquals("unit-api-key", config.getApiKey());
            assertEquals("unit-api-secret", config.getApiSecret());
            assertTrue(config.hasPrivateKeyConfiguration());
        } finally {
            restoreProperty(environmentKey, previousEnvironment);
            restoreProperty(restUrlKey, previousRestUrl);
            restoreProperty(wsSpotKey, previousSpotUrl);
            restoreProperty(apiKeyKey, previousApiKey);
            restoreProperty(apiSecretKey, previousApiSecret);
            BybitConfiguration.reset();
        }
    }

    @Test
    void enablesSocksProxyWhenConfigured() {
        String runProxyKey = BybitConfiguration.BYBIT_RUN_PROXY;
        String proxyHostKey = BybitConfiguration.BYBIT_PROXY_HOST;
        String proxyPortKey = BybitConfiguration.BYBIT_PROXY_PORT;

        String previousRunProxy = System.getProperty(runProxyKey);
        String previousProxyHost = System.getProperty(proxyHostKey);
        String previousProxyPort = System.getProperty(proxyPortKey);

        try {
            ProxyConfig.getInstance().setRunningLocally(false);

            System.setProperty(runProxyKey, "true");
            System.setProperty(proxyHostKey, "127.0.0.1");
            System.setProperty(proxyPortKey, "1081");

            BybitConfiguration.reset();
            BybitConfiguration.getInstance();

            assertTrue(ProxyConfig.getInstance().isRunningLocally());
            Proxy proxy = ProxyConfig.getInstance().getProxy();
            assertNotNull(proxy);
            assertEquals(Proxy.Type.SOCKS, proxy.type());
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            assertEquals("127.0.0.1", address.getHostString());
            assertEquals(1081, address.getPort());
        } finally {
            restoreProperty(runProxyKey, previousRunProxy);
            restoreProperty(proxyHostKey, previousProxyHost);
            restoreProperty(proxyPortKey, previousProxyPort);
            ProxyConfig.getInstance().setRunningLocally(false);
            BybitConfiguration.reset();
        }
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, previousValue);
    }
}
