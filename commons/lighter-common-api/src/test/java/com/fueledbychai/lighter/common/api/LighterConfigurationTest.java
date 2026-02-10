package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LighterConfigurationTest {

    private static final List<String> CONFIG_KEYS = List.of(
            "lighter.config.file",
            LighterConfiguration.LIGHTER_ENVIRONMENT,
            LighterConfiguration.LIGHTER_MAINNET_WS_URL,
            LighterConfiguration.LIGHTER_TESTNET_WS_URL,
            LighterConfiguration.LIGHTER_WEBSOCKET_READONLY);

    @BeforeEach
    void setup() {
        clearProperties();
        LighterConfiguration.reset();
    }

    @AfterEach
    void cleanup() {
        clearProperties();
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

    private void clearProperties() {
        for (String key : CONFIG_KEYS) {
            System.clearProperty(key);
        }
    }
}
