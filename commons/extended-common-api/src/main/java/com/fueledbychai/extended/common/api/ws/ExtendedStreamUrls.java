package com.fueledbychai.extended.common.api.ws;

/**
 * Builds Extended WebSocket stream URLs from the configured stream base URL
 * (e.g. {@code wss://api.starknet.extended.exchange/stream.extended.exchange/v1}).
 *
 * <p>
 * See <a href="https://api.docs.extended.exchange/#websocket-streams">the
 * Extended streams docs</a>.
 * </p>
 */
public final class ExtendedStreamUrls {

    private final String base;

    public ExtendedStreamUrls(String baseStreamUrl) {
        this.base = stripTrailingSlash(baseStreamUrl);
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Order book stream for a market (depth 1 = top-of-book deltas, null = full). */
    public String orderBook(String market, Integer depth) {
        String url = base + "/orderbooks/" + market;
        if (depth != null) {
            url += "?depth=" + depth;
        }
        return url;
    }

    /** Public trades stream for a market. */
    public String publicTrades(String market) {
        return base + "/publicTrades/" + market;
    }

    /** Funding-rate stream for a market. */
    public String funding(String market) {
        return base + "/funding/" + market;
    }

    /** Candle stream for a market. */
    public String candles(String market, String candleType, String interval) {
        return base + "/candles/" + market + "/" + candleType + "?interval=" + interval;
    }

    /** Private account-updates stream (orders, trades, balances, positions). */
    public String account() {
        return base + "/account";
    }
}
