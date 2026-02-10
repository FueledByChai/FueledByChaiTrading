package com.fueledbychai.lighter.common.api.ws;

import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWSClientBuilder {

    public static final String WS_TYPE_MARKET_STATS = "market_stats";
    public static final String WS_TYPE_MARKET_STATS_ALL = WS_TYPE_MARKET_STATS + "/all";

    public static LighterWebSocketClient buildClient(String url, String channel, IWebSocketProcessor processor)
            throws Exception {
        return new LighterWebSocketClient(url, channel, processor);
    }

    public static LighterWebSocketClient buildMarketStatsClient(String url, int marketId, IWebSocketProcessor processor)
            throws Exception {
        return buildClient(url, getMarketStatsChannel(marketId), processor);
    }

    public static LighterWebSocketClient buildAllMarketStatsClient(String url, IWebSocketProcessor processor)
            throws Exception {
        return buildClient(url, WS_TYPE_MARKET_STATS_ALL, processor);
    }

    public static String getMarketStatsChannel(int marketId) {
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }
        return WS_TYPE_MARKET_STATS + "/" + marketId;
    }
}

