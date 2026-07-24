package com.fueledbychai.grvt.common.api;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;
import com.fueledbychai.websocket.ExchangeWebSocketLifecycle;

/**
 * WebSocket contract for GRVT. The market-data socket carries public streams; the trade-data socket
 * (authenticated with the session cookie) carries private streams and JSON-RPC order entry.
 */
public interface IGrvtWebSocketApi extends ExchangeWebSocketLifecycle {

    void connectMarketData();

    void connectTradeData();

    boolean isMarketDataConnected();

    boolean isTradeDataConnected();

    /**
     * Replaces only the public market-data connection and replays its desired subscriptions.
     * The authenticated trade-data socket is deliberately left untouched.
     */
    default void forceReconnectMarketData() {
        throw new UnsupportedOperationException("Forced market-data reconnect is not supported");
    }

    /** Ends only the public market-data session. Implementations must remain restartable. */
    default void disconnectMarketData() {
        disconnectAll();
    }

    /** Ends only the authenticated trade-data session. Implementations must remain restartable. */
    default void disconnectTradeData() {
        disconnectAll();
    }

    /** Subscribes to a public market-data stream. {@code stream} is unversioned, e.g. {@code book.s}. */
    void subscribeMarketData(String stream, String selector, Consumer<JsonNode> listener);

    /** Subscribes to a private trade-data stream, e.g. {@code order}/{@code fill}/{@code position}. */
    void subscribeTradeData(String stream, String selector, Consumer<JsonNode> listener);

    /** Signs and submits an order over the trade-data JSON-RPC channel ({@code v1/create_order}). */
    CompletableFuture<JsonNode> rpcCreateOrder(GrvtOrder order);

    CompletableFuture<JsonNode> rpcCancelOrder(String orderId, String clientOrderId);

    CompletableFuture<JsonNode> rpcCancelAllOrders();

    @Override
    void disconnectAll();
}
