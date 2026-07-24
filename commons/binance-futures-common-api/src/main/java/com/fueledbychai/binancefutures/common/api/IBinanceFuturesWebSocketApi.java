package com.fueledbychai.binancefutures.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.binancefutures.common.api.ws.BinanceFuturesWebSocketClient;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketEventListener;

/**
 * Public websocket contract for the BinanceFutures exchange integration.
 */
public interface IBinanceFuturesWebSocketApi extends com.fueledbychai.websocket.ExchangeWebSocketLifecycle {

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

    /**
     * Subscribes to the best bid/ask stream for the supplied symbol.
     *
     * @param ticker the ticker to subscribe
     * @param listener the listener for JSON payloads
     * @return the underlying websocket client
     */
    BinanceFuturesWebSocketClient subscribeBookTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener);

    /**
     * Subscribes to 24h rolling ticker statistics for a symbol.
     *
     * @param ticker the ticker to subscribe
     * @param listener the listener for JSON payloads
     * @return the underlying websocket client
     */
    BinanceFuturesWebSocketClient subscribeSymbolTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener);

    /**
     * Subscribes to a partial depth stream.
     *
     * @param ticker the ticker to subscribe
     * @param depth the requested depth, usually 5, 10, or 20
     * @param listener the listener for JSON payloads
     * @return the underlying websocket client
     */
    BinanceFuturesWebSocketClient subscribePartialDepth(Ticker ticker, int depth,
            IWebSocketEventListener<JsonNode> listener);

    /**
     * Subscribes to the full-depth incremental diff stream ({@code <symbol>@depth@100ms}):
     * per-event book mutations with {@code U}/{@code u}/{@code pu} update ids. Distinct from
     * {@link #subscribePartialDepth} (throttled top-N snapshot); intended for the data recorder,
     * which seeds an absolute book from a REST depth snapshot and applies these deltas.
     *
     * @param ticker the ticker to subscribe
     * @param listener the listener for JSON payloads
     * @return the underlying websocket client
     */
    BinanceFuturesWebSocketClient subscribeDiffDepth(Ticker ticker, IWebSocketEventListener<JsonNode> listener);

    /**
     * Subscribes to aggregate trade updates.
     *
     * @param ticker the ticker to subscribe
     * @param listener the listener for JSON payloads
     * @return the underlying websocket client
     */
    BinanceFuturesWebSocketClient subscribeAggTrades(Ticker ticker, IWebSocketEventListener<JsonNode> listener);

    /**
     * Subscribes to mark-price and funding updates.
     *
     * @param ticker the ticker to subscribe
     * @param listener the listener for JSON payloads
     * @return the underlying websocket client
     */
    BinanceFuturesWebSocketClient subscribeMarkPrice(Ticker ticker, IWebSocketEventListener<JsonNode> listener);

    /**
     * Closes all managed websocket connections and cancels reconnect work.
     */
    @Override
    void disconnectAll();
}
