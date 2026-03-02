package com.fueledbychai.binance;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.websocket.ProxyConfig;

/**
 * Centralized configuration management for Paradex API settings. Supports
 * loading configuration from properties files, system properties, environment
 * variables, or programmatic configuration.
 */
public class BinanceConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(BinanceConfiguration.class);

    private static volatile BinanceConfiguration instance;
    private static final Object lock = new Object();

    // Configuration keys
    public static final String BINANCE_TESTNET_REST_URL = "binance.testnet.rest.url";
    public static final String BINANCE_TESTNET_WS_URL = "binance.testnet.ws.url";
    public static final String BINANCE_MAINNET_REST_URL = "binance.mainnet.rest.url";
    public static final String BINANCE_MAINNET_WS_URL = "binance.mainnet.ws.url";
    public static final String BINANCE_ACCOUNT_ADDRESS = "binance.account.address";
    public static final String BINANCE_PRIVATE_KEY = "binance.private.key";
    public static final String BINANCE_ENVIRONMENT = "binance.environment";
    public static final String BINANCE_JWT_REFRESH_SECONDS = "binance.jwt.refresh.seconds";
    public static final String BINANCE_KEYSTORE_PATH = "binance.keystore.path";
    public static final String RUN_PROXY = "binance.run.proxy";

    // Default values
    // private static final String DEFAULT_ENVIRONMENT = "testnet";
    private static final String DEFAULT_ENVIRONMENT = "prod";
    private static final String DEFAULT_TESTNET_REST_URL = "https://testnet.binance.vision/api/v3";
    private static final String DEFAULT_TESTNET_WS_URL = "wss://ws-api.testnet.binance.vision/ws-api/v3";
    private static final String DEFAULT_PROD_REST_URL = "https://api.binance.com/api/v3";
    private static final String DEFAULT_PROD_WS_URL = "wss://stream.binance.com:443/ws";
    private static final int DEFAULT_JWT_REFRESH_SECONDS = 60;
    private static final boolean DEFAULT_RUN_PROXY = false;

    private final Properties properties;
    private final String environment;

    protected String wsUrl;
    protected String restUrl;

    private BinanceConfiguration() {
        this.properties = new Properties();
        this.environment = loadConfiguration();
    }

    /**
     * Get the singleton instance of ParadexConfiguration. Thread-safe lazy
     * initialization using double-checked locking.
     */
    public static BinanceConfiguration getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new BinanceConfiguration();
                }
            }
        }
        return instance;
    }

    /**
     * Reset the configuration instance (useful for testing).
     */
    public static void reset() {
        synchronized (lock) {
            instance = null;
        }
    }

    /**
     * Load configuration from multiple sources in order of precedence: 1. System
     * properties 2. Environment variables 3. Properties file (paradex.properties)
     * 4. Classpath resource (paradex.properties) 5. Default values
     */
    private String loadConfiguration() {
        // Try to load from properties file first
        loadFromPropertiesFile();

        // Override with environment variables
        loadFromEnvironmentVariables();

        // Override with system properties (highest precedence)
        loadFromSystemProperties();

        // Determine environment and set defaults
        String env = properties.getProperty(BINANCE_ENVIRONMENT, DEFAULT_ENVIRONMENT);
        setEnvironmentDefaults(env);

        if (!properties.containsKey(BINANCE_PRIVATE_KEY)) {
            String privateKey = readPrivateKeyFromKeystore();
            properties.setProperty(BINANCE_PRIVATE_KEY, privateKey != null ? privateKey : "");
        }

        setProxySetting();

        logger.info("Binance configuration loaded for environment: {}", env);
        return env;
    }

    private void loadFromPropertiesFile() {
        // Try external properties file first
        String configFile = System.getProperty("binance.config.file", "binance.properties");
        try (InputStream is = new FileInputStream(configFile)) {
            properties.load(is);
            logger.info("Loaded configuration from file: {}", configFile);
            return;
        } catch (IOException e) {
            logger.warn("External config file not found: {}", configFile);
        }

        // Try classpath resource
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("paradex.properties")) {
            if (is != null) {
                properties.load(is);
                logger.info("Loaded configuration from classpath: paradex.properties");
            }
        } catch (IOException e) {
            logger.warn("Could not load paradex.properties from classpath", e);
        }
    }

    private void loadFromEnvironmentVariables() {
        // Convert property keys to environment variable format
        setIfPresent(BINANCE_TESTNET_REST_URL, System.getenv("BINANCE_TESTNET_REST_URL"));
        setIfPresent(BINANCE_TESTNET_WS_URL, System.getenv("BINANCE_TESTNET_WS_URL"));
        setIfPresent(BINANCE_MAINNET_REST_URL, System.getenv("BINANCE_MAINNET_REST_URL"));
        setIfPresent(BINANCE_MAINNET_WS_URL, System.getenv("BINANCE_MAINNET_WS_URL"));
        setIfPresent(BINANCE_ACCOUNT_ADDRESS, System.getenv("BINANCE_ACCOUNT_ADDRESS"));
        setIfPresent(BINANCE_PRIVATE_KEY, System.getenv("BINANCE_PRIVATE_KEY"));
        setIfPresent(BINANCE_ENVIRONMENT, System.getenv("BINANCE_ENVIRONMENT"));
        setIfPresent(BINANCE_JWT_REFRESH_SECONDS, System.getenv("BINANCE_JWT_REFRESH_SECONDS"));
        setIfPresent(BINANCE_KEYSTORE_PATH, System.getenv("BINANCE_KEYSTORE_PATH"));
        setIfPresent(RUN_PROXY, System.getenv("BINANCE_RUN_PROXY"));
    }

    private void loadFromSystemProperties() {
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("binance.")) {
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
        boolean isProduction = "prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env);

        if (isProduction) {
            restUrl = properties.getProperty(BINANCE_MAINNET_REST_URL, DEFAULT_PROD_REST_URL);
            wsUrl = properties.getProperty(BINANCE_MAINNET_WS_URL, DEFAULT_PROD_WS_URL);
        } else {
            restUrl = properties.getProperty(BINANCE_TESTNET_REST_URL, DEFAULT_TESTNET_REST_URL);
            wsUrl = properties.getProperty(BINANCE_TESTNET_WS_URL, DEFAULT_TESTNET_WS_URL);
        }

        if (!properties.containsKey(BINANCE_JWT_REFRESH_SECONDS)) {
            properties.setProperty(BINANCE_JWT_REFRESH_SECONDS, String.valueOf(DEFAULT_JWT_REFRESH_SECONDS));
        }
    }

    // Getter methods
    public String getRestUrl() {
        return restUrl;
    }

    public String getWebSocketUrl() {
        return wsUrl;
    }

    public String getAccountAddress() {
        return properties.getProperty(BINANCE_ACCOUNT_ADDRESS);
    }

    public String getPrivateKey() {
        return properties.getProperty(BINANCE_PRIVATE_KEY);
    }

    public String getEnvironment() {
        return environment;
    }

    public int getJwtRefreshSeconds() {
        return Integer.parseInt(
                properties.getProperty(BINANCE_JWT_REFRESH_SECONDS, String.valueOf(DEFAULT_JWT_REFRESH_SECONDS)));
    }

    public boolean isProductionEnvironment() {
        return "prod".equalsIgnoreCase(environment) || "production".equalsIgnoreCase(environment);
    }

    public boolean hasPrivateKeyConfiguration() {
        return getAccountAddress() != null && getPrivateKey() != null && !getAccountAddress().trim().isEmpty()
                && !getPrivateKey().trim().isEmpty();
    }

    /**
     * Get a custom property value.
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Get a custom property value with default.
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Set a property programmatically (useful for testing or dynamic
     * configuration).
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * Get all configuration properties (for debugging).
     */
    public Properties getAllProperties() {
        return new Properties(properties);
    }

    public String getKeystorePath() {
        return properties.getProperty(BINANCE_KEYSTORE_PATH);
    }

    protected String readPrivateKeyFromKeystore() {
        String keystorePath = getKeystorePath();
        if (keystorePath != null && !keystorePath.trim().isEmpty()) {
            // the private key is the only thing in the file
            try (InputStream is = new FileInputStream(keystorePath)) {
                byte[] keyBytes = is.readAllBytes();
                String privateKey = new String(keyBytes).trim();
                if (!privateKey.isEmpty()) {
                    properties.setProperty(BINANCE_PRIVATE_KEY, privateKey);
                    logger.info("Loaded private key from keystore path: {}", keystorePath);
                } else {
                    logger.warn("Keystore file is empty: {}", keystorePath);
                }
                return privateKey;
            } catch (IOException e) {
                logger.error("Error reading keystore file: {}", keystorePath, e);
                throw new FueledByChaiException("Error reading keystore file: " + keystorePath, e);
            }
        }
        return null;
    }

    protected void setProxySetting() {
        String runProxyStr = properties.getProperty(RUN_PROXY);
        boolean runProxy = DEFAULT_RUN_PROXY;
        if (runProxyStr != null && !runProxyStr.trim().isEmpty()) {
            runProxy = Boolean.parseBoolean(runProxyStr);
        }
        ProxyConfig.getInstance().setRunningLocally(runProxy);
        logger.info("Proxy setting - runningLocally: {}", ProxyConfig.getInstance().isRunningLocally());
    }

    @Override
    public String toString() {
        return String.format("ParadexConfiguration{environment='%s', restUrl='%s', wsUrl='%s', hasPrivateKey=%s}",
                environment, getRestUrl(), getWebSocketUrl(), hasPrivateKeyConfiguration());
    }
}
