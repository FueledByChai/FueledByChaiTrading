package com.fueledbychai.binancefutures.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BinanceFuturesConfigurationTest {

    @Test
    void readsConfiguredProperties() {
        String environmentKey = BinanceFuturesConfiguration.BINANCE_FUTURES_ENVIRONMENT;
        String restUrlKey = BinanceFuturesConfiguration.BINANCE_FUTURES_TESTNET_REST_URL;
        String wsUrlKey = BinanceFuturesConfiguration.BINANCE_FUTURES_TESTNET_WS_URL;
        String accountKey = BinanceFuturesConfiguration.BINANCE_FUTURES_API_KEY;
        String privateKey = BinanceFuturesConfiguration.BINANCE_FUTURES_API_SECRET;

        String previousEnvironment = System.getProperty(environmentKey);
        String previousRestUrl = System.getProperty(restUrlKey);
        String previousWsUrl = System.getProperty(wsUrlKey);
        String previousAccount = System.getProperty(accountKey);
        String previousPrivateKey = System.getProperty(privateKey);

        try {
            System.setProperty(environmentKey, "testnet");
            System.setProperty(restUrlKey, "https://api.unit.test");
            System.setProperty(wsUrlKey, "wss://ws.unit.test");
            System.setProperty(accountKey, "unit-account");
            System.setProperty(privateKey, "unit-private-key");

            BinanceFuturesConfiguration.reset();
            BinanceFuturesConfiguration config = BinanceFuturesConfiguration.getInstance();

            assertEquals("testnet", config.getEnvironment());
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
            BinanceFuturesConfiguration.reset();
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
