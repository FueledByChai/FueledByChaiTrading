package com.fueledbychai.extended.common.api;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.websocket.ProxyConfig;

/**
 * Centralized configuration management for the Extended (extended.exchange) API.
 *
 * <p>
 * Loads configuration from (in increasing order of precedence): bundled
 * defaults, a classpath {@code extended.properties}, an external properties file
 * ({@code -Dextended.config.file=...}), environment variables, and system
 * properties.
 * </p>
 *
 * <p>
 * Unlike Paradex, Extended authenticates reads with an API key
 * ({@code X-Api-Key}) and signs writes with a Stark private key (there is no JWT
 * refresh). Private API access therefore requires the API key, the Stark
 * private/public keys, and the collateral vault (position) id.
 * </p>
 */
public class ExtendedConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(ExtendedConfiguration.class);

    private static volatile ExtendedConfiguration instance;
    private static final Object lock = new Object();

    // Configuration keys
    public static final String EXTENDED_ENVIRONMENT = "extended.environment";
    public static final String EXTENDED_MAINNET_REST_URL = "extended.mainnet.rest.url";
    public static final String EXTENDED_MAINNET_WS_URL = "extended.mainnet.ws.url";
    public static final String EXTENDED_TESTNET_REST_URL = "extended.testnet.rest.url";
    public static final String EXTENDED_TESTNET_WS_URL = "extended.testnet.ws.url";
    public static final String EXTENDED_API_KEY = "extended.api.key";
    public static final String EXTENDED_STARK_PRIVATE_KEY = "extended.stark.private.key";
    public static final String EXTENDED_STARK_PUBLIC_KEY = "extended.stark.public.key";
    public static final String EXTENDED_VAULT_ID = "extended.vault.id";
    public static final String EXTENDED_ACCOUNT_ID = "extended.account.id";
    public static final String EXTENDED_KEYSTORE_PATH = "extended.keystore.path";
    public static final String RUN_PROXY = "run.proxy";

    // Default values
    private static final String DEFAULT_ENVIRONMENT = "prod";
    private static final String DEFAULT_MAINNET_REST_URL = "https://api.starknet.extended.exchange/api/v1";
    private static final String DEFAULT_MAINNET_WS_URL = "wss://api.starknet.extended.exchange/stream.extended.exchange/v1";
    private static final String DEFAULT_TESTNET_REST_URL = "https://api.starknet.sepolia.extended.exchange/api/v1";
    private static final String DEFAULT_TESTNET_WS_URL = "wss://api.starknet.sepolia.extended.exchange/stream.extended.exchange/v1";

    private final Properties properties;
    private final String environment;

    protected String wsUrl;
    protected String restUrl;

    private ExtendedConfiguration() {
        this.properties = new Properties();
        this.environment = loadConfiguration();
    }

    /** Thread-safe lazy singleton (double-checked locking). */
    public static ExtendedConfiguration getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ExtendedConfiguration();
                }
            }
        }
        return instance;
    }

    /** Reset the configuration instance (useful for testing). */
    public static void reset() {
        synchronized (lock) {
            instance = null;
        }
    }

    private String loadConfiguration() {
        loadFromPropertiesFile();
        loadFromEnvironmentVariables();
        loadFromSystemProperties();

        String env = properties.getProperty(EXTENDED_ENVIRONMENT, DEFAULT_ENVIRONMENT);
        setEnvironmentDefaults(env);

        if (!properties.containsKey(EXTENDED_STARK_PRIVATE_KEY)) {
            String privateKey = readPrivateKeyFromKeystore();
            properties.setProperty(EXTENDED_STARK_PRIVATE_KEY, privateKey != null ? privateKey : "");
        }

        setProxySetting();

        logger.info("Extended configuration loaded for environment: {}", env);
        return env;
    }

    private void loadFromPropertiesFile() {
        String configFile = System.getProperty("extended.config.file", "extended.properties");
        try (InputStream is = new FileInputStream(configFile)) {
            properties.load(is);
            logger.info("Loaded configuration from file: {}", configFile);
            return;
        } catch (IOException e) {
            logger.warn("External config file not found: {}", configFile);
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("extended.properties")) {
            if (is != null) {
                properties.load(is);
                logger.info("Loaded configuration from classpath: extended.properties");
            }
        } catch (IOException e) {
            logger.warn("Could not load extended.properties from classpath", e);
        }
    }

    private void loadFromEnvironmentVariables() {
        setIfPresent(EXTENDED_ENVIRONMENT, System.getenv("EXTENDED_ENVIRONMENT"));
        setIfPresent(EXTENDED_MAINNET_REST_URL, System.getenv("EXTENDED_MAINNET_REST_URL"));
        setIfPresent(EXTENDED_MAINNET_WS_URL, System.getenv("EXTENDED_MAINNET_WS_URL"));
        setIfPresent(EXTENDED_TESTNET_REST_URL, System.getenv("EXTENDED_TESTNET_REST_URL"));
        setIfPresent(EXTENDED_TESTNET_WS_URL, System.getenv("EXTENDED_TESTNET_WS_URL"));
        setIfPresent(EXTENDED_API_KEY, System.getenv("EXTENDED_API_KEY"));
        setIfPresent(EXTENDED_STARK_PRIVATE_KEY, System.getenv("EXTENDED_STARK_PRIVATE_KEY"));
        setIfPresent(EXTENDED_STARK_PUBLIC_KEY, System.getenv("EXTENDED_STARK_PUBLIC_KEY"));
        setIfPresent(EXTENDED_VAULT_ID, System.getenv("EXTENDED_VAULT_ID"));
        setIfPresent(EXTENDED_ACCOUNT_ID, System.getenv("EXTENDED_ACCOUNT_ID"));
        setIfPresent(EXTENDED_KEYSTORE_PATH, System.getenv("EXTENDED_KEYSTORE_PATH"));
        setIfPresent(RUN_PROXY, System.getenv("EXTENDED_RUN_PROXY"));
    }

    private void loadFromSystemProperties() {
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("extended.")) {
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
        if (isProduction(env)) {
            restUrl = properties.getProperty(EXTENDED_MAINNET_REST_URL, DEFAULT_MAINNET_REST_URL);
            wsUrl = properties.getProperty(EXTENDED_MAINNET_WS_URL, DEFAULT_MAINNET_WS_URL);
        } else {
            restUrl = properties.getProperty(EXTENDED_TESTNET_REST_URL, DEFAULT_TESTNET_REST_URL);
            wsUrl = properties.getProperty(EXTENDED_TESTNET_WS_URL, DEFAULT_TESTNET_WS_URL);
        }
    }

    private static boolean isProduction(String env) {
        return "prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env) || "mainnet".equalsIgnoreCase(env);
    }

    // Getter methods
    public String getRestUrl() {
        return restUrl;
    }

    public String getWebSocketUrl() {
        return wsUrl;
    }

    public String getApiKey() {
        return properties.getProperty(EXTENDED_API_KEY);
    }

    public String getStarkPrivateKey() {
        return properties.getProperty(EXTENDED_STARK_PRIVATE_KEY);
    }

    public String getStarkPublicKey() {
        return properties.getProperty(EXTENDED_STARK_PUBLIC_KEY);
    }

    public String getVaultId() {
        return properties.getProperty(EXTENDED_VAULT_ID);
    }

    public String getAccountId() {
        return properties.getProperty(EXTENDED_ACCOUNT_ID);
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isProductionEnvironment() {
        return isProduction(environment);
    }

    /** The SNIP-12 signing domain matching the configured environment. */
    public StarknetDomain getStarknetDomain() {
        return isProductionEnvironment() ? StarknetDomain.MAINNET : StarknetDomain.TESTNET;
    }

    /** True when this instance has everything needed to sign and place orders. */
    public boolean hasPrivateKeyConfiguration() {
        return isNotBlank(getApiKey()) && isNotBlank(getStarkPrivateKey()) && isNotBlank(getStarkPublicKey())
                && isNotBlank(getVaultId());
    }

    /** True when at least an API key is configured (sufficient for private reads). */
    public boolean hasApiKey() {
        return isNotBlank(getApiKey());
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public String getKeystorePath() {
        return properties.getProperty(EXTENDED_KEYSTORE_PATH);
    }

    protected String readPrivateKeyFromKeystore() {
        String keystorePath = getKeystorePath();
        if (keystorePath != null && !keystorePath.trim().isEmpty()) {
            try (InputStream is = new FileInputStream(keystorePath)) {
                byte[] keyBytes = is.readAllBytes();
                String privateKey = new String(keyBytes).trim();
                if (!privateKey.isEmpty()) {
                    properties.setProperty(EXTENDED_STARK_PRIVATE_KEY, privateKey);
                    logger.info("Loaded Stark private key from keystore path: {}", keystorePath);
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
        if (ProxyConfig.getInstance().isGlobalProxyEnabled()) {
            ProxyConfig.getInstance().getProxy();
            logger.info("Extended proxy enabled via global settings: {}:{}",
                    ProxyConfig.getInstance().getGlobalProxyHost(), ProxyConfig.getInstance().getGlobalProxyPort());
            return;
        }

        String runProxyStr = properties.getProperty(RUN_PROXY);
        if (runProxyStr == null || runProxyStr.trim().isEmpty()) {
            return;
        }
        boolean runProxy = Boolean.parseBoolean(runProxyStr);
        ProxyConfig.getInstance().setRunningLocally(runProxy);
        logger.info("Proxy setting - runningLocally: {}", ProxyConfig.getInstance().isRunningLocally());
    }

    @Override
    public String toString() {
        return String.format("ExtendedConfiguration{environment='%s', restUrl='%s', wsUrl='%s', hasPrivateKey=%s}",
                environment, getRestUrl(), getWebSocketUrl(), hasPrivateKeyConfiguration());
    }
}
