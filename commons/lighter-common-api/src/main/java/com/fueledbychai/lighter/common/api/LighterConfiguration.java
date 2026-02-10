/**
 * MIT License
 *
 * Copyright (c) 2015  FueledByChai Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.fueledbychai.lighter.common.api;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.websocket.ProxyConfig;

/**
 * Centralized configuration management for Lighter API settings. Supports
 * loading configuration from properties files, system properties, environment
 * variables, or defaults.
 */
public class LighterConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(LighterConfiguration.class);

    private static volatile LighterConfiguration instance;
    private static final Object lock = new Object();

    public static final String LIGHTER_ENVIRONMENT = "lighter.environment";
    public static final String LIGHTER_MAINNET_REST_URL = "lighter.mainnet.rest.url";
    public static final String LIGHTER_TESTNET_REST_URL = "lighter.testnet.rest.url";
    public static final String LIGHTER_MAINNET_WS_URL = "lighter.mainnet.ws.url";
    public static final String LIGHTER_TESTNET_WS_URL = "lighter.testnet.ws.url";
    public static final String LIGHTER_WEBSOCKET_READONLY = "lighter.websocket.readonly";
    public static final String LIGHTER_RUN_PROXY = "lighter.run.proxy";
    public static final String LIGHTER_PROXY_HOST = "lighter.proxy.host";
    public static final String LIGHTER_PROXY_PORT = "lighter.proxy.port";
    public static final String LIGHTER_ACCOUNT_ADDRESS = "lighter.account.address";
    public static final String LIGHTER_PRIVATE_KEY = "lighter.private.key";

    private static final String DEFAULT_ENVIRONMENT = "prod";
    private static final String DEFAULT_MAINNET_REST_URL = "https://mainnet.zklighter.elliot.ai/api/v1";
    private static final String DEFAULT_MAINNET_WS_URL = "wss://mainnet.zklighter.elliot.ai/stream";
    private static final boolean DEFAULT_RUN_PROXY = false;
    private static final String DEFAULT_PROXY_HOST = "127.0.0.1";
    private static final int DEFAULT_PROXY_PORT = 1080;
    private static final boolean DEFAULT_WEBSOCKET_READONLY = true;

    private final Properties properties;
    private final String environment;

    private LighterConfiguration() {
        this.properties = new Properties();
        this.environment = loadConfiguration();
    }

    public static LighterConfiguration getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new LighterConfiguration();
                }
            }
        }
        return instance;
    }

    public static void reset() {
        synchronized (lock) {
            instance = null;
        }
    }

    private String loadConfiguration() {
        loadFromPropertiesFile();
        loadFromEnvironmentVariables();
        loadFromSystemProperties();

        String env = properties.getProperty(LIGHTER_ENVIRONMENT, DEFAULT_ENVIRONMENT);
        setEnvironmentDefaults(env);
        setProxySetting();

        logger.info("Lighter configuration loaded for environment: {}", env);
        return env;
    }

    private void loadFromPropertiesFile() {
        String configFile = System.getProperty("lighter.config.file", "lighter.properties");
        logger.info("Attempting to load config file: {}", configFile);
        try (InputStream is = new FileInputStream(configFile)) {
            properties.load(is);
            logger.info("Loaded configuration from file: {}", configFile);
            return;
        } catch (IOException e) {
            logger.debug("External config file not found: {}", configFile);
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("lighter.properties")) {
            if (is != null) {
                properties.load(is);
                logger.info("Loaded configuration from classpath: lighter.properties");
            }
        } catch (IOException e) {
            logger.debug("Could not load lighter.properties from classpath", e);
        }
    }

    private void loadFromEnvironmentVariables() {
        setIfPresent(LIGHTER_MAINNET_REST_URL, System.getenv("LIGHTER_MAINNET_REST_URL"));
        setIfPresent(LIGHTER_TESTNET_REST_URL, System.getenv("LIGHTER_TESTNET_REST_URL"));
        setIfPresent(LIGHTER_MAINNET_WS_URL, System.getenv("LIGHTER_MAINNET_WS_URL"));
        setIfPresent(LIGHTER_TESTNET_WS_URL, System.getenv("LIGHTER_TESTNET_WS_URL"));
        setIfPresent(LIGHTER_WEBSOCKET_READONLY, System.getenv("LIGHTER_WEBSOCKET_READONLY"));
        setIfPresent(LIGHTER_RUN_PROXY, System.getenv("LIGHTER_RUN_PROXY"));
        setIfPresent(LIGHTER_PROXY_HOST, System.getenv("LIGHTER_PROXY_HOST"));
        setIfPresent(LIGHTER_PROXY_PORT, System.getenv("LIGHTER_PROXY_PORT"));
        setIfPresent(LIGHTER_ACCOUNT_ADDRESS, System.getenv("LIGHTER_ACCOUNT_ADDRESS"));
        setIfPresent(LIGHTER_PRIVATE_KEY, System.getenv("LIGHTER_PRIVATE_KEY"));
        setIfPresent(LIGHTER_ENVIRONMENT, System.getenv("LIGHTER_ENVIRONMENT"));
    }

    private void loadFromSystemProperties() {
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("lighter.")) {
                properties.setProperty(key, System.getProperty(key));
            }
        }
    }

    private void setIfPresent(String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            properties.setProperty(key, value);
        }
    }

    private void setEnvironmentDefaults(String env) {
        if (!properties.containsKey(LIGHTER_MAINNET_REST_URL)) {
            properties.setProperty(LIGHTER_MAINNET_REST_URL, DEFAULT_MAINNET_REST_URL);
        }
        if (!properties.containsKey(LIGHTER_MAINNET_WS_URL)) {
            properties.setProperty(LIGHTER_MAINNET_WS_URL, DEFAULT_MAINNET_WS_URL);
        }
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isProductionEnvironment() {
        return "prod".equalsIgnoreCase(environment) || "production".equalsIgnoreCase(environment);
    }

    public boolean hasPrivateKeyConfiguration() {
        String address = getAccountAddress();
        String privateKey = getPrivateKey();
        return address != null && !address.isBlank() && privateKey != null && !privateKey.isBlank();
    }

    public String getRestUrl() {
        if (isProductionEnvironment()) {
            return properties.getProperty(LIGHTER_MAINNET_REST_URL);
        }
        String testnetUrl = properties.getProperty(LIGHTER_TESTNET_REST_URL);
        if (testnetUrl == null || testnetUrl.isBlank()) {
            throw new IllegalStateException(
                    "Testnet environment configured but no " + LIGHTER_TESTNET_REST_URL + " provided.");
        }
        return testnetUrl;
    }

    public String getWebSocketUrl() {
        String webSocketUrl;
        if (isProductionEnvironment()) {
            webSocketUrl = properties.getProperty(LIGHTER_MAINNET_WS_URL);
        } else {
            String testnetUrl = properties.getProperty(LIGHTER_TESTNET_WS_URL);
            if (testnetUrl == null || testnetUrl.isBlank()) {
                throw new IllegalStateException(
                        "Testnet environment configured but no " + LIGHTER_TESTNET_WS_URL + " provided.");
            }
            webSocketUrl = testnetUrl;
        }

        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            throw new IllegalStateException("No websocket URL is configured.");
        }

        if (!isWebSocketReadonlyEnabled()) {
            return webSocketUrl;
        }

        return appendReadonlyQueryParam(webSocketUrl);
    }

    public String getAccountAddress() {
        return properties.getProperty(LIGHTER_ACCOUNT_ADDRESS);
    }

    public String getPrivateKey() {
        return properties.getProperty(LIGHTER_PRIVATE_KEY);
    }

    public boolean isWebSocketReadonlyEnabled() {
        String explicit = properties.getProperty(LIGHTER_WEBSOCKET_READONLY);
        if (explicit != null && !explicit.isBlank()) {
            return Boolean.parseBoolean(explicit);
        }
        if (isProxyEnabled()) {
            return false;
        }
        return Boolean.parseBoolean(properties.getProperty(LIGHTER_WEBSOCKET_READONLY,
                Boolean.toString(DEFAULT_WEBSOCKET_READONLY)));
    }

    public boolean isProxyEnabled() {
        return Boolean.parseBoolean(properties.getProperty(LIGHTER_RUN_PROXY, Boolean.toString(DEFAULT_RUN_PROXY)));
    }

    public String getProxyHost() {
        return properties.getProperty(LIGHTER_PROXY_HOST, DEFAULT_PROXY_HOST);
    }

    public int getProxyPort() {
        String portText = properties.getProperty(LIGHTER_PROXY_PORT);
        return parseProxyPort(portText);
    }

    private int parseProxyPort(String portText) {
        if (portText == null || portText.isBlank()) {
            return DEFAULT_PROXY_PORT;
        }
        try {
            int parsed = Integer.parseInt(portText.trim());
            if (parsed > 0 && parsed <= 65535) {
                return parsed;
            }
            logger.warn("Invalid proxy port '{}', falling back to {}", portText, DEFAULT_PROXY_PORT);
        } catch (NumberFormatException ex) {
            logger.warn("Unable to parse proxy port '{}', falling back to {}", portText, DEFAULT_PROXY_PORT);
        }
        return DEFAULT_PROXY_PORT;
    }

    private void setProxySetting() {
        if (isProxyEnabled()) {
            ProxyConfig.getInstance().setSocksProxy(getProxyHost(), getProxyPort());
            logger.info("Lighter proxy enabled: {}:{}", getProxyHost(), getProxyPort());
            return;
        }

        ProxyConfig.getInstance().setRunningLocally(false);
        logger.info("Lighter proxy disabled");
    }

    private String appendReadonlyQueryParam(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("readonly=")) {
            return url;
        }
        if (url.endsWith("?") || url.endsWith("&")) {
            return url + "readonly=true";
        }
        return url.contains("?") ? url + "&readonly=true" : url + "?readonly=true";
    }
}
