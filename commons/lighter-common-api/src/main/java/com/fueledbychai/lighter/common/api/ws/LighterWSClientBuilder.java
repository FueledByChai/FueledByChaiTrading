package com.fueledbychai.lighter.common.api.ws;

import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWSClientBuilder {

    public static final String WS_TYPE_MARKET_STATS = "market_stats";
    public static final String WS_TYPE_MARKET_STATS_ALL = WS_TYPE_MARKET_STATS + "/all";
    public static final String WS_TYPE_ORDER_BOOK = "order_book";
    public static final String WS_TYPE_TRADE = "trade";
    public static final String WS_TYPE_ACCOUNT_ALL_TRADES = "account_all_trades";

    public static LighterWebSocketClient buildClient(String url, String channel, IWebSocketProcessor processor)
            throws Exception {
        return new LighterWebSocketClient(url, channel, processor);
    }

    public static LighterWebSocketClient buildClient(String url, String channel, String subscribeAuth,
            IWebSocketProcessor processor) throws Exception {
        return new LighterWebSocketClient(url, channel, subscribeAuth, processor);
    }

    public static LighterWebSocketClient buildMarketStatsClient(String url, int marketId, IWebSocketProcessor processor)
            throws Exception {
        return buildClient(url, getMarketStatsChannel(marketId), processor);
    }

    public static LighterWebSocketClient buildAllMarketStatsClient(String url, IWebSocketProcessor processor)
            throws Exception {
        return buildClient(url, WS_TYPE_MARKET_STATS_ALL, processor);
    }

    public static LighterWebSocketClient buildOrderBookClient(String url, int marketId, IWebSocketProcessor processor)
            throws Exception {
        return buildClient(url, getOrderBookChannel(marketId), processor);
    }

    public static LighterWebSocketClient buildTradeClient(String url, int marketId, IWebSocketProcessor processor)
            throws Exception {
        return buildClient(url, getTradeChannel(marketId), processor);
    }

    public static LighterWebSocketClient buildAccountAllTradesClient(String url, long accountIndex, String authToken,
            IWebSocketProcessor processor) throws Exception {
        return buildClient(url, getAccountAllTradesChannel(accountIndex), authToken, processor);
    }

    public static String getMarketStatsChannel(int marketId) {
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }
        return WS_TYPE_MARKET_STATS + "/" + marketId;
    }

    public static String getOrderBookChannel(int marketId) {
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }
        return WS_TYPE_ORDER_BOOK + "/" + marketId;
    }

    public static String getTradeChannel(int marketId) {
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }
        return WS_TYPE_TRADE + "/" + marketId;
    }

    public static String getAccountAllTradesChannel(long accountIndex) {
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        return WS_TYPE_ACCOUNT_ALL_TRADES + "/" + accountIndex;
    }
}
