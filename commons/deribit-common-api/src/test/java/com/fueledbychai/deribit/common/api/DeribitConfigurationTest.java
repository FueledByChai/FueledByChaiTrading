package com.fueledbychai.deribit.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeribitConfigurationTest {

    @Test
    void readsConfiguredProperties() {
        String environmentKey = "deribit.environment";
        String restUrlKey = "deribit.rest.url";
        String wsUrlKey = "deribit.ws.url";

        String previousEnvironment = System.getProperty(environmentKey);
        String previousRestUrl = System.getProperty(restUrlKey);
        String previousWsUrl = System.getProperty(wsUrlKey);

        try {
            System.setProperty(environmentKey, "test");
            System.setProperty(restUrlKey, "https://api.unit.test");
            System.setProperty(wsUrlKey, "wss://ws.unit.test");

            DeribitConfiguration.reset();
            DeribitConfiguration config = DeribitConfiguration.getInstance();

            assertEquals("test", config.getEnvironment());
            assertEquals("https://api.unit.test", config.getRestUrl());
            assertEquals("wss://ws.unit.test", config.getWebSocketUrl());
        } finally {
            restoreProperty(environmentKey, previousEnvironment);
            restoreProperty(restUrlKey, previousRestUrl);
            restoreProperty(wsUrlKey, previousWsUrl);
            DeribitConfiguration.reset();
        }
    }

    @Test
    void derivesDefaultUrlsFromEnvironment() {
        String environmentKey = "deribit.environment";
        String restUrlKey = "deribit.rest.url";
        String wsUrlKey = "deribit.ws.url";

        String previousEnvironment = System.getProperty(environmentKey);
        String previousRestUrl = System.getProperty(restUrlKey);
        String previousWsUrl = System.getProperty(wsUrlKey);

        try {
            System.setProperty(environmentKey, "test");
            System.clearProperty(restUrlKey);
            System.clearProperty(wsUrlKey);

            DeribitConfiguration.reset();
            DeribitConfiguration config = DeribitConfiguration.getInstance();

            assertTrue(config.getRestUrl().contains("test.deribit.com"));
            assertTrue(config.getWebSocketUrl().contains("test.deribit.com"));
        } finally {
            restoreProperty(environmentKey, previousEnvironment);
            restoreProperty(restUrlKey, previousRestUrl);
            restoreProperty(wsUrlKey, previousWsUrl);
            DeribitConfiguration.reset();
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
