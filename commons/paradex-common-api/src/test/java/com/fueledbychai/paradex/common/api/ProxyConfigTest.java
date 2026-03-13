package com.fueledbychai.paradex.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.InetSocketAddress;
import java.net.Proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fueledbychai.websocket.ProxyConfig;

class ProxyConfigTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(ProxyConfig.GLOBAL_RUN_PROXY);
        System.clearProperty(ProxyConfig.GLOBAL_PROXY_HOST);
        System.clearProperty(ProxyConfig.GLOBAL_PROXY_PORT);
        System.clearProperty(ProxyConfig.GLOBAL_CONFIG_FILE);
        ProxyConfig.getInstance().reset();
    }

    @Test
    void testGetProxyWhenRunningLocally() {
        // Given
        ProxyConfig.getInstance().setRunningLocally(true);

        // When
        Proxy proxy = ProxyConfig.getInstance().getProxy();

        // Then
        assertNotNull(proxy);
        assertEquals(Proxy.Type.SOCKS, proxy.type());

        InetSocketAddress address = (InetSocketAddress) proxy.address();
        assertEquals("127.0.0.1", address.getHostString());
        assertEquals(1080, address.getPort());
    }

    @Test
    void testGetProxyWhenNotRunningLocally() {
        // Given
        ProxyConfig.getInstance().setRunningLocally(false);

        // When
        Proxy proxy = ProxyConfig.getInstance().getProxy();

        // Then
        assertNotNull(proxy);
        assertEquals(Proxy.NO_PROXY, proxy);
    }

    @Test
    void testGlobalProxyOverridesDisabledLocalSetting() {
        System.setProperty(ProxyConfig.GLOBAL_RUN_PROXY, "true");
        System.setProperty(ProxyConfig.GLOBAL_PROXY_HOST, "10.0.0.1");
        System.setProperty(ProxyConfig.GLOBAL_PROXY_PORT, "1090");

        ProxyConfig.getInstance().setRunningLocally(false);

        Proxy proxy = ProxyConfig.getInstance().getProxy();
        assertNotNull(proxy);
        assertEquals(Proxy.Type.SOCKS, proxy.type());
        InetSocketAddress address = (InetSocketAddress) proxy.address();
        assertEquals("10.0.0.1", address.getHostString());
        assertEquals(1090, address.getPort());
    }

    @Test
    void testRunningLocallyDefaultValue() {
        assertEquals(false, ProxyConfig.getInstance().isRunningLocally());
    }
}
