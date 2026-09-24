package com.fueledbychai.qfex.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QfexConfigurationTest {

    @AfterEach
    void clear() {
        for (String k : new String[] { QfexConfiguration.QFEX_ENVIRONMENT, QfexConfiguration.QFEX_REST_URL,
                QfexConfiguration.QFEX_API_PUBLIC_KEY, QfexConfiguration.QFEX_API_SECRET_KEY }) {
            System.clearProperty(k);
        }
        QfexConfiguration.reset();
    }

    @Test
    void defaultsToProductionHosts() {
        QfexConfiguration c = QfexConfiguration.getInstance();
        assertTrue(c.isProduction());
        assertEquals("https://api.qfex.com", c.getRestUrl());
        assertEquals("wss://mds.qfex.com", c.getMarketDataWebSocketUrl());
        assertEquals("wss://trade.qfex.com", c.getTradeWebSocketUrl());
        assertFalse(c.isCancelOnDisconnect());
    }

    @Test
    void uatUsesQfexIoHosts() {
        System.setProperty(QfexConfiguration.QFEX_ENVIRONMENT, "uat");
        QfexConfiguration c = QfexConfiguration.getInstance();
        assertFalse(c.isProduction());
        assertEquals("https://api.qfex.io", c.getRestUrl());
        assertEquals("wss://trade.qfex.io", c.getTradeWebSocketUrl());
    }

    @Test
    void explicitUrlAndKeysWin() {
        System.setProperty(QfexConfiguration.QFEX_REST_URL, "https://rest.unit.test");
        System.setProperty(QfexConfiguration.QFEX_API_PUBLIC_KEY, "qfex_pub_x");
        QfexConfiguration c = QfexConfiguration.getInstance();
        assertEquals("https://rest.unit.test", c.getRestUrl());
        assertFalse(c.hasPrivateApiConfiguration());
        System.setProperty(QfexConfiguration.QFEX_API_SECRET_KEY, "qfex_secret_x");
        assertTrue(c.hasPrivateApiConfiguration());
    }
}
