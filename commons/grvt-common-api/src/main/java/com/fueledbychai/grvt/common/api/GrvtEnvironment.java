package com.fueledbychai.grvt.common.api;

/**
 * GRVT host families and chain id for a given environment, ported from {@code grvt_ccxt_env.py} /
 * {@code grvt_raw_env.py} in the GRVT Python SDK.
 * <p>
 * GRVT splits its API across three host families: an {@code edge} host (auth), a {@code trade-data}
 * host (private orders/positions/account) and a {@code market-data} host (public market data). Each
 * has REST and, for trade/market data, a {@code /ws/full} JSON-RPC WebSocket endpoint.
 */
public final class GrvtEnvironment {

    /** GRVT EIP-712 chain id for prod. */
    public static final int CHAIN_ID_PROD = 325;
    /** GRVT EIP-712 chain id for testnet. */
    public static final int CHAIN_ID_TESTNET = 326;

    private final String name;
    private final String edgeRestUrl;
    private final String tradeRestUrl;
    private final String tradeWsUrl;
    private final String marketRestUrl;
    private final String marketWsUrl;
    private final int chainId;

    private GrvtEnvironment(String name, String edgeRestUrl, String tradeRestUrl, String tradeWsUrl,
            String marketRestUrl, String marketWsUrl, int chainId) {
        this.name = name;
        this.edgeRestUrl = edgeRestUrl;
        this.tradeRestUrl = tradeRestUrl;
        this.tradeWsUrl = tradeWsUrl;
        this.marketRestUrl = marketRestUrl;
        this.marketWsUrl = marketWsUrl;
        this.chainId = chainId;
    }

    /**
     * Resolves the host configuration for the named environment. Recognizes {@code prod}/
     * {@code mainnet}/{@code production} and {@code testnet}; anything else defaults to testnet.
     */
    public static GrvtEnvironment forName(String environment) {
        if (isProduction(environment)) {
            return new GrvtEnvironment("prod",
                    "https://edge.grvt.io",
                    "https://trades.grvt.io",
                    "wss://trades.grvt.io/ws/full",
                    "https://market-data.grvt.io",
                    "wss://market-data.grvt.io/ws/full",
                    CHAIN_ID_PROD);
        }
        String env = "testnet";
        return new GrvtEnvironment(env,
                "https://edge." + env + ".grvt.io",
                "https://trades." + env + ".grvt.io",
                "wss://trades." + env + ".grvt.io/ws/full",
                "https://market-data." + env + ".grvt.io",
                "wss://market-data." + env + ".grvt.io/ws/full",
                CHAIN_ID_TESTNET);
    }

    public static boolean isProduction(String environment) {
        return environment != null
                && ("prod".equalsIgnoreCase(environment)
                        || "production".equalsIgnoreCase(environment)
                        || "mainnet".equalsIgnoreCase(environment));
    }

    public String getName() {
        return name;
    }

    /** Edge auth login URL ({@code POST {api_key}} returns the session cookie). */
    public String getAuthLoginUrl() {
        return edgeRestUrl + "/auth/api_key/login";
    }

    public String getEdgeRestUrl() {
        return edgeRestUrl;
    }

    public String getTradeRestUrl() {
        return tradeRestUrl;
    }

    public String getTradeWsUrl() {
        return tradeWsUrl;
    }

    public String getMarketRestUrl() {
        return marketRestUrl;
    }

    public String getMarketWsUrl() {
        return marketWsUrl;
    }

    public int getChainId() {
        return chainId;
    }
}
