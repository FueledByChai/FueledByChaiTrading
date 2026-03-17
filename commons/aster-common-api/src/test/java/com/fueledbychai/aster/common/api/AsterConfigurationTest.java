package com.fueledbychai.aster.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AsterConfigurationTest {

    @Test
    void readsConfiguredProperties() {
        String environmentKey = AsterConfiguration.ASTER_ENVIRONMENT;
        String restUrlKey = AsterConfiguration.ASTER_TESTNET_REST_URL;
        String wsUrlKey = AsterConfiguration.ASTER_TESTNET_WS_URL;
        String apiKey = AsterConfiguration.ASTER_API_KEY;
        String apiSecret = AsterConfiguration.ASTER_API_SECRET;
        String recvWindowKey = AsterConfiguration.ASTER_RECV_WINDOW;

        String previousEnvironment = System.getProperty(environmentKey);
        String previousRestUrl = System.getProperty(restUrlKey);
        String previousWsUrl = System.getProperty(wsUrlKey);
        String previousApiKey = System.getProperty(apiKey);
        String previousApiSecret = System.getProperty(apiSecret);
        String previousRecvWindow = System.getProperty(recvWindowKey);

        try {
            System.setProperty(environmentKey, "testnet");
            System.setProperty(restUrlKey, "https://api.unit.test");
            System.setProperty(wsUrlKey, "wss://ws.unit.test");
            System.setProperty(apiKey, "unit-api-key");
            System.setProperty(apiSecret, "unit-api-secret");
            System.setProperty(recvWindowKey, "12345");

            AsterConfiguration.reset();
            AsterConfiguration config = AsterConfiguration.getInstance();

            assertEquals("testnet", config.getEnvironment());
            assertEquals("https://api.unit.test", config.getRestUrl());
            assertEquals("wss://ws.unit.test", config.getWebSocketUrl());
            assertEquals("unit-api-key", config.getApiKey());
            assertEquals("unit-api-secret", config.getApiSecret());
            assertEquals(12345L, config.getRecvWindow());
            assertTrue(config.hasPrivateKeyConfiguration());
        } finally {
            restoreProperty(environmentKey, previousEnvironment);
            restoreProperty(restUrlKey, previousRestUrl);
            restoreProperty(wsUrlKey, previousWsUrl);
            restoreProperty(apiKey, previousApiKey);
            restoreProperty(apiSecret, previousApiSecret);
            restoreProperty(recvWindowKey, previousRecvWindow);
            AsterConfiguration.reset();
        }
    }

    @Test
    void productionDefaultsUseOfficialMainnetEndpoints() {
        AsterConfiguration.reset();
        AsterConfiguration config = AsterConfiguration.getInstance();
        assertEquals("https://fapi.asterdex.com", config.getRestUrl());
        assertEquals("wss://fstream.asterdex.com/ws", config.getWebSocketUrl());
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, previousValue);
    }
}
