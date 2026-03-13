package com.fueledbychai.hyperliquid.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.InetSocketAddress;
import java.net.Proxy;

import org.junit.jupiter.api.Test;

import com.fueledbychai.websocket.ProxyConfig;

class HyperliquidRestApiProxyTest {

    @Test
    void constructorUsesGlobalSocksProxyWhenEnabled() {
        String previousRunProxy = System.getProperty(ProxyConfig.GLOBAL_RUN_PROXY);
        String previousProxyHost = System.getProperty(ProxyConfig.GLOBAL_PROXY_HOST);
        String previousProxyPort = System.getProperty(ProxyConfig.GLOBAL_PROXY_PORT);

        try {
            System.setProperty(ProxyConfig.GLOBAL_RUN_PROXY, "true");
            System.setProperty(ProxyConfig.GLOBAL_PROXY_HOST, "127.0.0.12");
            System.setProperty(ProxyConfig.GLOBAL_PROXY_PORT, "1102");
            ProxyConfig.getInstance().reset();

            HyperliquidRestApi api = new HyperliquidRestApi("https://api.hyperliquid.xyz");
            Proxy proxy = api.client.proxy();

            assertNotNull(proxy);
            assertEquals(Proxy.Type.SOCKS, proxy.type());
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            assertEquals("127.0.0.12", address.getHostString());
            assertEquals(1102, address.getPort());
        } finally {
            restoreProperty(ProxyConfig.GLOBAL_RUN_PROXY, previousRunProxy);
            restoreProperty(ProxyConfig.GLOBAL_PROXY_HOST, previousProxyHost);
            restoreProperty(ProxyConfig.GLOBAL_PROXY_PORT, previousProxyPort);
            ProxyConfig.getInstance().reset();
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
