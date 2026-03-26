package com.fueledbychai.lighter.common.api;

import com.fueledbychai.lighter.common.api.ws.listener.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterOrderBookListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterTickerListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterAccountAllTradesListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterAccountOrdersListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterAccountStatsListener;
import com.fueledbychai.lighter.common.api.ws.listener.ILighterTradeListener;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.signer.LighterSignedTransaction;
import com.fueledbychai.lighter.common.api.ws.model.LighterSendTxResponse;
import com.fueledbychai.lighter.common.api.ws.client.LighterWebSocketClient;
import org.json.JSONObject;

/**
 * Public websocket contract for the Lighter exchange integration.
 *
 * Implementations are expected to manage long-lived websocket connections,
 * expose one method per logical stream, and provide deterministic shutdown via
 * {@link #disconnectAll()}. Concrete implementations may also provide optional
 * dedicated transaction sockets for lower-latency order entry.
 */
public interface ILighterWebSocketApi {

    /**
     * Subscribes to market stats for a single market.
     *
     * @param marketId the exchange market identifier
     * @param listener the listener that receives updates
     * @return the websocket client managing that subscription
     */
    LighterWebSocketClient subscribeMarketStats(int marketId, ILighterMarketStatsListener listener);

    /**
     * Subscribes to market stats for all markets supported by the exchange stream.
     *
     * @param listener the listener that receives updates
     * @return the websocket client managing that subscription
     */
    LighterWebSocketClient subscribeAllMarketStats(ILighterMarketStatsListener listener);

    /**
     * Subscribes to BBO (best bid/offer) ticker updates for a single market.
     *
     * @param marketId the exchange market identifier
     * @param listener the listener that receives updates
     * @return the websocket client managing that subscription
     */
    LighterWebSocketClient subscribeTicker(int marketId, ILighterTickerListener listener);

    /**
     * Subscribes to order book updates for a single market.
     *
     * @param marketId the exchange market identifier
     * @param listener the listener that receives updates
     * @return the websocket client managing that subscription
     */
    LighterWebSocketClient subscribeOrderBook(int marketId, ILighterOrderBookListener listener);

    /**
     * Subscribes to trade updates for a single market.
     *
     * @param marketId the exchange market identifier
     * @param listener the listener that receives updates
     * @return the websocket client managing that subscription
     */
    LighterWebSocketClient subscribeTrades(int marketId, ILighterTradeListener listener);

    /**
     * Subscribes to private account trade updates for all markets.
     *
     * @param accountIndex the exchange account index
     * @param authToken the auth token used for the subscription handshake
     * @param listener the listener that receives updates
     * @return the websocket client managing that subscription
     */
    LighterWebSocketClient subscribeAccountAllTrades(long accountIndex, String authToken,
            ILighterAccountAllTradesListener listener);

    /**
     * Subscribes to private account order updates.
     *
     * @param marketIndex the market index requested by the caller
     * @param accountIndex the exchange account index
     * @param authToken the auth token used for the subscription handshake
     * @param listener the listener that receives updates
     * @return the websocket client managing that subscription
     */
    LighterWebSocketClient subscribeAccountOrders(int marketIndex, long accountIndex, String authToken,
            ILighterAccountOrdersListener listener);

    /**
     * Subscribes to account-level statistics updates.
     *
     * @param accountIndex the exchange account index
     * @param listener the listener that receives updates
     * @return the websocket client managing that subscription
     */
    LighterWebSocketClient subscribeAccountStats(long accountIndex, ILighterAccountStatsListener listener);

    /**
     * Signs a create-order request without transmitting it.
     *
     * @param orderRequest the order request to sign
     * @return the signed transaction payload
     */
    LighterSignedTransaction signOrder(LighterCreateOrderRequest orderRequest);

    /**
     * Signs and submits a create-order request over the tx websocket.
     *
     * @param orderRequest the order request to submit
     * @return the exchange acknowledgement
     */
    LighterSendTxResponse submitOrder(LighterCreateOrderRequest orderRequest);

    /**
     * Signs a cancel-order request without transmitting it.
     *
     * @param cancelRequest the cancel request to sign
     * @return the signed transaction payload
     */
    LighterSignedTransaction signCancelOrder(LighterCancelOrderRequest cancelRequest);

    /**
     * Signs and submits a cancel-order request over the tx websocket.
     *
     * @param cancelRequest the cancel request to submit
     * @return the exchange acknowledgement
     */
    LighterSendTxResponse cancelOrder(LighterCancelOrderRequest cancelRequest);

    /**
     * Signs a modify-order request without transmitting it.
     *
     * @param modifyRequest the modify request to sign
     * @return the signed transaction payload
     */
    LighterSignedTransaction signModifyOrder(LighterModifyOrderRequest modifyRequest);

    /**
     * Signs and submits a modify-order request over the tx websocket.
     *
     * @param modifyRequest the modify request to submit
     * @return the exchange acknowledgement
     */
    LighterSendTxResponse modifyOrder(LighterModifyOrderRequest modifyRequest);

    /**
     * Sends a pre-signed transaction over the tx websocket.
     *
     * @param txType the exchange transaction type identifier
     * @param txInfo the transaction payload
     * @return the exchange acknowledgement
     */
    LighterSendTxResponse sendSignedTransaction(int txType, JSONObject txInfo);

    /**
     * Optionally pre-connects and keeps the transaction websocket warm.
     *
     * Implementations that do not expose a dedicated tx websocket may no-op.
     */
    default void connectTxWebSocket() {
        // Optional capability. Implementations may no-op.
    }

    /**
     * Closes all managed websocket connections and cancels any reconnect work.
     */
    void disconnectAll();
}
