package com.fueledbychai.grvt.common.api;

/**
 * Loads GRVT connection settings and credentials, following the same precedence as the other
 * exchange configurations (system property, then environment variable, then default).
 * <p>
 * GRVT authenticates with an {@code api_key} (to obtain the session cookie) and signs orders with an
 * Ethereum {@code private_key}; orders are placed for a specific {@code sub_account_id}
 * (trading account id).
 */
public class GrvtConfiguration {

    public static final String GRVT_ENVIRONMENT = "grvt.environment";
    public static final String GRVT_API_KEY = "grvt.api.key";
    public static final String GRVT_PRIVATE_KEY = "grvt.private.key";
    public static final String GRVT_SUB_ACCOUNT_ID = "grvt.sub.account.id";
    public static final String GRVT_COOKIE_REFRESH_SECONDS = "grvt.cookie.refresh.seconds";

    private static final String DEFAULT_ENVIRONMENT = "prod";
    private static final long DEFAULT_COOKIE_REFRESH_SECONDS = 60L * 30L;

    private static volatile GrvtConfiguration instance;
    private static final Object LOCK = new Object();

    private final String environment;
    private final GrvtEnvironment grvtEnvironment;
    private final String apiKey;
    private final String privateKey;
    private final String subAccountId;
    private final long cookieRefreshSeconds;

    public static GrvtConfiguration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new GrvtConfiguration();
                }
            }
        }
        return instance;
    }

    public static void reset() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    private GrvtConfiguration() {
        this.environment = read(GRVT_ENVIRONMENT, DEFAULT_ENVIRONMENT);
        this.grvtEnvironment = GrvtEnvironment.forName(this.environment);
        this.apiKey = read(GRVT_API_KEY, null);
        this.privateKey = read(GRVT_PRIVATE_KEY, null);
        this.subAccountId = read(GRVT_SUB_ACCOUNT_ID, null);
        this.cookieRefreshSeconds = readLong(GRVT_COOKIE_REFRESH_SECONDS, DEFAULT_COOKIE_REFRESH_SECONDS);
    }

    private static String read(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static long readLong(String key, long defaultValue) {
        String value = read(key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public String getEnvironment() {
        return environment;
    }

    public GrvtEnvironment getGrvtEnvironment() {
        return grvtEnvironment;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getSubAccountId() {
        return subAccountId;
    }

    public long getCookieRefreshSeconds() {
        return cookieRefreshSeconds;
    }

    public boolean isProductionEnvironment() {
        return GrvtEnvironment.isProduction(environment);
    }

    /**
     * Whether full private-API access is configured. GRVT needs an api key (cookie auth), an EVM
     * private key (order signing) and a sub-account id (order routing).
     */
    public boolean hasPrivateKeyConfiguration() {
        return notBlank(apiKey) && notBlank(privateKey) && notBlank(subAccountId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
