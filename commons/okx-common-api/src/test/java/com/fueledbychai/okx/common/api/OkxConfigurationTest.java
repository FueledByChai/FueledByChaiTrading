package com.fueledbychai.okx.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OkxConfigurationTest {

    @Test
    void readsConfiguredProperties() {
        String environmentKey = "okx.environment";
        String restUrlKey = "okx.rest.url";
        String wsUrlKey = "okx.ws.url";
        String accountKey = "okx.account.address";
        String privateKey = "okx.private.key";

        String previousEnvironment = System.getProperty(environmentKey);
        String previousRestUrl = System.getProperty(restUrlKey);
        String previousWsUrl = System.getProperty(wsUrlKey);
        String previousAccount = System.getProperty(accountKey);
        String previousPrivateKey = System.getProperty(privateKey);

        try {
            System.setProperty(environmentKey, "test");
            System.setProperty(restUrlKey, "https://api.unit.test");
            System.setProperty(wsUrlKey, "wss://ws.unit.test");
            System.setProperty(accountKey, "unit-account");
            System.setProperty(privateKey, "unit-private-key");

            OkxConfiguration.reset();
            OkxConfiguration config = OkxConfiguration.getInstance();

            assertEquals("test", config.getEnvironment());
            assertEquals("https://api.unit.test", config.getRestUrl());
            assertEquals("wss://ws.unit.test", config.getWebSocketUrl());
            assertEquals("unit-account", config.getAccountAddress());
            assertEquals("unit-private-key", config.getPrivateKey());
            assertTrue(config.hasPrivateKeyConfiguration());
        } finally {
            restoreProperty(environmentKey, previousEnvironment);
            restoreProperty(restUrlKey, previousRestUrl);
            restoreProperty(wsUrlKey, previousWsUrl);
            restoreProperty(accountKey, previousAccount);
            restoreProperty(privateKey, previousPrivateKey);
            OkxConfiguration.reset();
        }
    }

    @Test
    void derivesDefaultUrlsFromEnvironment() {
        String environmentKey = "okx.environment";
        String restUrlKey = "okx.rest.url";
        String wsUrlKey = "okx.ws.url";

        String previousEnvironment = System.getProperty(environmentKey);
        String previousRestUrl = System.getProperty(restUrlKey);
        String previousWsUrl = System.getProperty(wsUrlKey);

        try {
            System.setProperty(environmentKey, "test");
            System.clearProperty(restUrlKey);
            System.clearProperty(wsUrlKey);

            OkxConfiguration.reset();
            OkxConfiguration config = OkxConfiguration.getInstance();

            assertEquals("https://www.okx.com/api/v5", config.getRestUrl());
            assertTrue(config.getWebSocketUrl().contains("wspap.okx.com"));
        } finally {
            restoreProperty(environmentKey, previousEnvironment);
            restoreProperty(restUrlKey, previousRestUrl);
            restoreProperty(wsUrlKey, previousWsUrl);
            OkxConfiguration.reset();
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
