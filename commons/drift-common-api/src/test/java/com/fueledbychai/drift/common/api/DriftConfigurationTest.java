package com.fueledbychai.drift.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DriftConfigurationTest {

    @Test
    void readsConfiguredProperties() {
        String environmentKey = "drift.environment";
        String dataRestUrlKey = "drift.data.rest.url";
        String dataWsUrlKey = "drift.data.ws.url";
        String dlobRestUrlKey = "drift.dlob.rest.url";
        String dlobWsUrlKey = "drift.dlob.ws.url";
        String gatewayRestUrlKey = "drift.gateway.rest.url";
        String gatewayWsUrlKey = "drift.gateway.ws.url";
        String subAccountIdKey = "drift.sub.account.id";

        String previousEnvironment = System.getProperty(environmentKey);
        String previousDataRestUrl = System.getProperty(dataRestUrlKey);
        String previousDataWsUrl = System.getProperty(dataWsUrlKey);
        String previousDlobRestUrl = System.getProperty(dlobRestUrlKey);
        String previousDlobWsUrl = System.getProperty(dlobWsUrlKey);
        String previousGatewayRestUrl = System.getProperty(gatewayRestUrlKey);
        String previousGatewayWsUrl = System.getProperty(gatewayWsUrlKey);
        String previousSubAccountId = System.getProperty(subAccountIdKey);

        try {
            System.setProperty(environmentKey, "test");
            System.setProperty(dataRestUrlKey, "https://data.unit.test");
            System.setProperty(dataWsUrlKey, "wss://data.unit.test/ws");
            System.setProperty(dlobRestUrlKey, "https://dlob.unit.test");
            System.setProperty(dlobWsUrlKey, "wss://dlob.unit.test/ws");
            System.setProperty(gatewayRestUrlKey, "http://gateway.unit.test:8080");
            System.setProperty(gatewayWsUrlKey, "ws://gateway.unit.test:1337");
            System.setProperty(subAccountIdKey, "7");

            DriftConfiguration.reset();
            DriftConfiguration config = DriftConfiguration.getInstance();

            assertEquals("test", config.getEnvironment());
            assertEquals("https://data.unit.test", config.getDataRestUrl());
            assertEquals("wss://data.unit.test/ws", config.getDataWebSocketUrl());
            assertEquals("https://dlob.unit.test", config.getDlobRestUrl());
            assertEquals("wss://dlob.unit.test/ws", config.getDlobWebSocketUrl());
            assertEquals("http://gateway.unit.test:8080", config.getGatewayRestUrl());
            assertEquals("ws://gateway.unit.test:1337", config.getGatewayWebSocketUrl());
            assertEquals(7, config.getSubAccountId());
            assertTrue(config.hasGatewayConfiguration());
        } finally {
            restoreProperty(environmentKey, previousEnvironment);
            restoreProperty(dataRestUrlKey, previousDataRestUrl);
            restoreProperty(dataWsUrlKey, previousDataWsUrl);
            restoreProperty(dlobRestUrlKey, previousDlobRestUrl);
            restoreProperty(dlobWsUrlKey, previousDlobWsUrl);
            restoreProperty(gatewayRestUrlKey, previousGatewayRestUrl);
            restoreProperty(gatewayWsUrlKey, previousGatewayWsUrl);
            restoreProperty(subAccountIdKey, previousSubAccountId);
            DriftConfiguration.reset();
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
