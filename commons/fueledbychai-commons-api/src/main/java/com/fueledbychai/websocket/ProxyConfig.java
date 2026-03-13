package com.fueledbychai.websocket;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyConfig {

    public static final String GLOBAL_RUN_PROXY = "fueledbychai.run.proxy";
    public static final String GLOBAL_PROXY_HOST = "fueledbychai.proxy.host";
    public static final String GLOBAL_PROXY_PORT = "fueledbychai.proxy.port";
    public static final String GLOBAL_CONFIG_FILE = "fueledbychai.config.file";

    protected static final Logger logger = LoggerFactory.getLogger(ProxyConfig.class);
    protected static final String DEFAULT_PROXY_HOST = "127.0.0.1";
    protected static final int DEFAULT_PROXY_PORT = 1080;
    protected static final String DEFAULT_CONFIG_FILE = "fueledbychai.properties";

    protected boolean runningLocally = false;
    protected static ProxyConfig instance;
    protected String proxyHost = DEFAULT_PROXY_HOST;
    protected int proxyPort = DEFAULT_PROXY_PORT;

    public static ProxyConfig getInstance() {
        if (instance == null) {
            instance = new ProxyConfig();
        }
        return instance;
    }

    public synchronized void setRunningLocally(boolean runningLocally) {
        this.runningLocally = runningLocally;
        if (runningLocally) {
            String systemHost = System.getProperty("socksProxyHost");
            String systemPort = System.getProperty("socksProxyPort");
            proxyHost = isBlank(systemHost) ? proxyHost : systemHost.trim();
            proxyPort = parsePort(systemPort, proxyPort);
        }
        syncSystemProxyProperties(resolveActiveSettings());
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
        syncSystemProxyProperties(resolveActiveSettings());
    }

    public boolean isRunningLocally() {
        return resolveActiveSettings().enabled;
    }

    public synchronized Proxy getProxy() {
        ProxySettings settings = resolveActiveSettings();
        if (!settings.enabled) {
            syncSystemProxyProperties(settings);
            return Proxy.NO_PROXY;
        }
        syncSystemProxyProperties(settings);
        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(settings.host, settings.port));
    }

    public synchronized ProxySelector getProxySelector() {
        Proxy proxy = getProxy();
        if (proxy == null || proxy == Proxy.NO_PROXY) {
            return null;
        }
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(proxy);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                logger.warn("Proxy connection failed uri={} proxy={}", uri, sa, ioe);
            }
        };
    }

    public synchronized boolean isGlobalProxyEnabled() {
        return resolveGlobalSettings().enabled;
    }

    public synchronized String getGlobalProxyHost() {
        return resolveGlobalSettings().host;
    }

    public synchronized int getGlobalProxyPort() {
        return resolveGlobalSettings().port;
    }

    public synchronized void reset() {
        runningLocally = false;
        proxyHost = DEFAULT_PROXY_HOST;
        proxyPort = DEFAULT_PROXY_PORT;
        syncSystemProxyProperties(resolveActiveSettings());
    }

    protected ProxySettings resolveActiveSettings() {
        ProxySettings globalSettings = resolveGlobalSettings();
        if (globalSettings.enabled) {
            return globalSettings;
        }
        if (runningLocally) {
            return new ProxySettings(true, proxyHost, proxyPort);
        }
        return ProxySettings.disabled();
    }

    protected ProxySettings resolveGlobalSettings() {
        Properties globalProperties = loadGlobalProperties();
        String runProxy = firstNonBlank(
                System.getProperty(GLOBAL_RUN_PROXY),
                System.getenv("FUELEDBYCHAI_RUN_PROXY"),
                globalProperties.getProperty(GLOBAL_RUN_PROXY));
        if (!Boolean.parseBoolean(runProxy)) {
            return ProxySettings.disabled();
        }

        String host = firstNonBlank(
                System.getProperty(GLOBAL_PROXY_HOST),
                System.getenv("FUELEDBYCHAI_PROXY_HOST"),
                globalProperties.getProperty(GLOBAL_PROXY_HOST),
                DEFAULT_PROXY_HOST);
        int port = parsePort(firstNonBlank(
                System.getProperty(GLOBAL_PROXY_PORT),
                System.getenv("FUELEDBYCHAI_PROXY_PORT"),
                globalProperties.getProperty(GLOBAL_PROXY_PORT)),
                DEFAULT_PROXY_PORT);
        return new ProxySettings(true, host, port);
    }

    protected Properties loadGlobalProperties() {
        Properties properties = new Properties();
        String configFile = firstNonBlank(System.getProperty(GLOBAL_CONFIG_FILE), System.getenv("FUELEDBYCHAI_CONFIG_FILE"),
                DEFAULT_CONFIG_FILE);
        try (InputStream is = new FileInputStream(configFile)) {
            properties.load(is);
            return properties;
        } catch (IOException ignored) {
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException ignored) {
        }
        return properties;
    }

    protected void syncSystemProxyProperties(ProxySettings settings) {
        if (!settings.enabled) {
            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");
            return;
        }
        System.setProperty("socksProxyHost", settings.host);
        System.setProperty("socksProxyPort", String.valueOf(settings.port));
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    protected static class ProxySettings {
        protected final boolean enabled;
        protected final String host;
        protected final int port;

        protected ProxySettings(boolean enabled, String host, int port) {
            this.enabled = enabled;
            this.host = host;
            this.port = port;
        }

        protected static ProxySettings disabled() {
            return new ProxySettings(false, DEFAULT_PROXY_HOST, DEFAULT_PROXY_PORT);
        }
    }
}
