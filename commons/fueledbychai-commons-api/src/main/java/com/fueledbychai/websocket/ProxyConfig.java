package com.fueledbychai.websocket;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class ProxyConfig {

    protected boolean runningLocally = false;
    protected static ProxyConfig instance;
    protected String proxyHost = "127.0.0.1";
    protected int proxyPort = 1080;

    public static ProxyConfig getInstance() {
        if (instance == null) {
            instance = new ProxyConfig();
        }
        return instance;
    }

    public synchronized void setRunningLocally(boolean runningLocally) {
        if (!runningLocally) {
            this.runningLocally = false;
            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");
            return;
        }

        String systemHost = System.getProperty("socksProxyHost");
        String systemPort = System.getProperty("socksProxyPort");
        String host = isBlank(systemHost) ? proxyHost : systemHost.trim();
        int port = parsePort(systemPort, proxyPort);
        setSocksProxy(host, port);
    }

    public synchronized void setSocksProxy(String host, int port) {
        if (isBlank(host)) {
            throw new IllegalArgumentException("Proxy host is required");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Proxy port must be in range 1-65535");
        }
        this.proxyHost = host.trim();
        this.proxyPort = port;
        this.runningLocally = true;
        System.setProperty("socksProxyHost", this.proxyHost);
        System.setProperty("socksProxyPort", String.valueOf(this.proxyPort));
    }

    public boolean isRunningLocally() {
        return runningLocally;
    }

    public synchronized Proxy getProxy() {
        if (!runningLocally) {
            return Proxy.NO_PROXY;
        } else {
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));
        }
    }

    private int parsePort(String portText, int defaultPort) {
        if (isBlank(portText)) {
            return defaultPort;
        }
        try {
            int parsed = Integer.parseInt(portText.trim());
            if (parsed > 0 && parsed <= 65535) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        return defaultPort;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
