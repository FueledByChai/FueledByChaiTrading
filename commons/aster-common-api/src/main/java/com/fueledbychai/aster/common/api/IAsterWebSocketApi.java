package com.fueledbychai.aster.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.aster.common.api.ws.AsterWebSocketClient;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketEventListener;

/**
 * Public websocket contract for the Aster exchange integration.
 *
 * Aster follows the Binance-style futures stream model for public market data
 * and private listen-key user streams.
 */
public interface IAsterWebSocketApi {

    /**
     * Opens the main websocket session when the exchange requires an explicit
     * bootstrap step.
     */
    void connect();

    /**
     * Optionally pre-connects a dedicated order-entry websocket.
     *
     * Implementations that do not require a separate order-entry socket may
     * keep the default no-op behavior.
     */
    default void connectOrderEntryWebSocket() {
        // Optional capability.
    }

    AsterWebSocketClient subscribeBookTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener);

    AsterWebSocketClient subscribeSymbolTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener);

    AsterWebSocketClient subscribePartialDepth(Ticker ticker, int depth, IWebSocketEventListener<JsonNode> listener);

    AsterWebSocketClient subscribeAggTrades(Ticker ticker, IWebSocketEventListener<JsonNode> listener);

    AsterWebSocketClient subscribeMarkPrice(Ticker ticker, IWebSocketEventListener<JsonNode> listener);

    AsterWebSocketClient subscribeUserData(String listenKey, IWebSocketEventListener<JsonNode> listener);

    /**
     * Closes all managed websocket connections and cancels reconnect work.
     */
    void disconnectAll();
}
