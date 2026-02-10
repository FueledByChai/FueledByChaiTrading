package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fueledbychai.websocket.ProxyConfig;

public class LighterConfigurationTest {

    private static final List<String> CONFIG_KEYS = List.of(
            "lighter.config.file",
            LighterConfiguration.LIGHTER_ENVIRONMENT,
            LighterConfiguration.LIGHTER_MAINNET_WS_URL,
            LighterConfiguration.LIGHTER_TESTNET_WS_URL,
            LighterConfiguration.LIGHTER_RUN_PROXY,
            LighterConfiguration.LIGHTER_PROXY_HOST,
            LighterConfiguration.LIGHTER_PROXY_PORT,
            LighterConfiguration.LIGHTER_WEBSOCKET_READONLY);

    @BeforeEach
    void setup() {
        clearProperties();
        ProxyConfig.getInstance().setRunningLocally(false);
        LighterConfiguration.reset();
    }

    @AfterEach
    void cleanup() {
        clearProperties();
        ProxyConfig.getInstance().setRunningLocally(false);
        LighterConfiguration.reset();
    }

    @Test
    void websocketUrlDefaultsToReadonlyWhenUnset() {
        System.setProperty(LighterConfiguration.LIGHTER_ENVIRONMENT, "prod");
        System.setProperty(LighterConfiguration.LIGHTER_MAINNET_WS_URL, "wss://example.test/stream");

        LighterConfiguration configuration = LighterConfiguration.getInstance();

        assertTrue(configuration.isWebSocketReadonlyEnabled());
        assertEquals("wss://example.test/stream?readonly=true", configuration.getWebSocketUrl());
    }

    @Test
    void websocketUrlDoesNotAppendReadonlyWhenDisabled() {
        System.setProperty(LighterConfiguration.LIGHTER_ENVIRONMENT, "prod");
        System.setProperty(LighterConfiguration.LIGHTER_MAINNET_WS_URL, "wss://example.test/stream");
        System.setProperty(LighterConfiguration.LIGHTER_WEBSOCKET_READONLY, "false");

        LighterConfiguration configuration = LighterConfiguration.getInstance();

        assertFalse(configuration.isWebSocketReadonlyEnabled());
        assertEquals("wss://example.test/stream", configuration.getWebSocketUrl());
    }

    @Test
    void websocketUrlAppendsReadonlyToExistingQuery() {
        System.setProperty(LighterConfiguration.LIGHTER_ENVIRONMENT, "prod");
        System.setProperty(LighterConfiguration.LIGHTER_MAINNET_WS_URL, "wss://example.test/stream?foo=bar");

        LighterConfiguration configuration = LighterConfiguration.getInstance();

        assertEquals("wss://example.test/stream?foo=bar&readonly=true", configuration.getWebSocketUrl());
    }

    @Test
    void websocketUrlDoesNotDuplicateReadonlyParameter() {
        System.setProperty(LighterConfiguration.LIGHTER_ENVIRONMENT, "prod");
        System.setProperty(LighterConfiguration.LIGHTER_MAINNET_WS_URL, "wss://example.test/stream?readonly=false");

        LighterConfiguration configuration = LighterConfiguration.getInstance();

        assertEquals("wss://example.test/stream?readonly=false", configuration.getWebSocketUrl());
    }

    @Test
    void proxyEnabledRoutesThroughConfiguredSocksProxy() {
        System.setProperty(LighterConfiguration.LIGHTER_ENVIRONMENT, "prod");
        System.setProperty(LighterConfiguration.LIGHTER_RUN_PROXY, "true");
        System.setProperty(LighterConfiguration.LIGHTER_PROXY_HOST, "127.0.0.1");
        System.setProperty(LighterConfiguration.LIGHTER_PROXY_PORT, "1080");

        LighterConfiguration configuration = LighterConfiguration.getInstance();

        assertTrue(configuration.isProxyEnabled());
        assertEquals("127.0.0.1", configuration.getProxyHost());
        assertEquals(1080, configuration.getProxyPort());
        assertTrue(ProxyConfig.getInstance().isRunningLocally());

        Proxy proxy = ProxyConfig.getInstance().getProxy();
        assertEquals(Proxy.Type.SOCKS, proxy.type());
        InetSocketAddress address = (InetSocketAddress) proxy.address();
        assertEquals("127.0.0.1", address.getHostString());
        assertEquals(1080, address.getPort());
    }

    @Test
    void proxyEnabledDisablesReadonlyByDefault() {
        System.setProperty(LighterConfiguration.LIGHTER_ENVIRONMENT, "prod");
        System.setProperty(LighterConfiguration.LIGHTER_MAINNET_WS_URL, "wss://example.test/stream");
        System.setProperty(LighterConfiguration.LIGHTER_RUN_PROXY, "true");

        LighterConfiguration configuration = LighterConfiguration.getInstance();

        assertFalse(configuration.isWebSocketReadonlyEnabled());
        assertEquals("wss://example.test/stream", configuration.getWebSocketUrl());
    }

    @Test
    void explicitReadonlyOverridesProxyDefault() {
        System.setProperty(LighterConfiguration.LIGHTER_ENVIRONMENT, "prod");
        System.setProperty(LighterConfiguration.LIGHTER_MAINNET_WS_URL, "wss://example.test/stream");
        System.setProperty(LighterConfiguration.LIGHTER_RUN_PROXY, "true");
        System.setProperty(LighterConfiguration.LIGHTER_WEBSOCKET_READONLY, "true");

        LighterConfiguration configuration = LighterConfiguration.getInstance();

        assertTrue(configuration.isWebSocketReadonlyEnabled());
        assertEquals("wss://example.test/stream?readonly=true", configuration.getWebSocketUrl());
    }

    private void clearProperties() {
        for (String key : CONFIG_KEYS) {
            System.clearProperty(key);
        }
    }
}
