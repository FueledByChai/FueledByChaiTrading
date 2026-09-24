package com.fueledbychai.qfex.common.api;

/**
 * Configuration for the QFEX integration.
 *
 * <p>Each key is read from the JVM system property first, then from an
 * environment variable with the key upper-cased and dots replaced by
 * underscores ({@code qfex.api.public.key} -> {@code QFEX_API_PUBLIC_KEY}).
 * Values are read on every call, so {@link #reset()} is only needed to drop
 * the singleton in tests.
 *
 * <p>Environments: {@code prod} (qfex.com) and {@code uat} (qfex.io, the
 * venue's test environment).
 */
public class QfexConfiguration {

    public static final String QFEX_ENVIRONMENT = "qfex.environment";
    public static final String QFEX_REST_URL = "qfex.rest.url";
    public static final String QFEX_MDS_URL = "qfex.mds.url";
    public static final String QFEX_TRADE_URL = "qfex.trade.url";
    public static final String QFEX_API_PUBLIC_KEY = "qfex.api.public.key";
    public static final String QFEX_API_SECRET_KEY = "qfex.api.secret.key";
    /** Optional subaccount UUID; the key's default account is used when unset. */
    public static final String QFEX_ACCOUNT_ID = "qfex.account.id";
    /**
     * When true the trade socket asks the venue to cancel ALL of the account's
     * orders if the connection drops — including stops and orders placed on
     * other connections. Off by default for that reason.
     */
    public static final String QFEX_CANCEL_ON_DISCONNECT = "qfex.cancel.on.disconnect";
    public static final String QFEX_ORDER_TIMEOUT_MS = "qfex.order.timeout.ms";

    private static volatile QfexConfiguration instance;
    private static final Object LOCK = new Object();

    public static QfexConfiguration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new QfexConfiguration();
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

    public String getEnvironment() {
        return get(QFEX_ENVIRONMENT, "prod");
    }

    public boolean isProduction() {
        String env = getEnvironment().trim().toLowerCase();
        return env.equals("prod") || env.equals("production") || env.equals("mainnet");
    }

    public String getRestUrl() {
        return get(QFEX_REST_URL, isProduction() ? "https://api.qfex.com" : "https://api.qfex.io");
    }

    public String getMarketDataWebSocketUrl() {
        return get(QFEX_MDS_URL, isProduction() ? "wss://mds.qfex.com" : "wss://mds.qfex.io");
    }

    public String getTradeWebSocketUrl() {
        return get(QFEX_TRADE_URL, isProduction() ? "wss://trade.qfex.com" : "wss://trade.qfex.io");
    }

    public String getPublicKey() {
        return get(QFEX_API_PUBLIC_KEY, null);
    }

    public String getSecretKey() {
        return get(QFEX_API_SECRET_KEY, null);
    }

    public String getAccountId() {
        return get(QFEX_ACCOUNT_ID, null);
    }

    public boolean isCancelOnDisconnect() {
        return Boolean.parseBoolean(get(QFEX_CANCEL_ON_DISCONNECT, "false"));
    }

    public long getOrderTimeoutMillis() {
        return Long.parseLong(get(QFEX_ORDER_TIMEOUT_MS, "5000"));
    }

    public boolean hasPrivateApiConfiguration() {
        return notBlank(getPublicKey()) && notBlank(getSecretKey());
    }

    private static String get(String key, String defaultValue) {
        String v = System.getProperty(key);
        if (notBlank(v)) {
            return v.trim();
        }
        v = System.getenv(key.toUpperCase().replace('.', '_'));
        return notBlank(v) ? v.trim() : defaultValue;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
