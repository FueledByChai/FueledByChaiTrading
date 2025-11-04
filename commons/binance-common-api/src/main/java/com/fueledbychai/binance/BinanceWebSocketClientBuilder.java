package com.fueledbychai.binance;

import java.util.Map;

import com.fueledbychai.websocket.IWebSocketProcessor;

public class BinanceWebSocketClientBuilder {

    public static final String WS_TYPE_ORDER_UPDATES = "orderUpdates";
    public static final String WS_TYPE_ORDER_BOOK_UPDATES = "l2Book";
    public static final String WS_TYPE_BBO = "bbo";
    public static final String WS_TYPE_ACTIVE_ASSET_CTX = "activeAssetCtx";
    public static final String WS_TYPE_TRADES = "trades";
    public static final String WS_TYPE_ACCOUNT_INFO = "clearinghouseState";
    public static final String WS_TYPE_USER_FILLS = "userFills";

    public static BinanceWebSocketClient buildOrderUpdateClient(String url, String userAddress,
            IWebSocketProcessor processor) throws Exception {
        return new BinanceWebSocketClient(url, WS_TYPE_ORDER_UPDATES, processor);
    }

}
