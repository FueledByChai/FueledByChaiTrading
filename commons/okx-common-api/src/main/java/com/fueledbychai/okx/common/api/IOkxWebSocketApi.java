package com.fueledbychai.okx.common.api;

import com.fueledbychai.okx.common.api.ws.listener.IOkxFundingRateListener;
import com.fueledbychai.okx.common.api.ws.listener.IOkxOrderBookListener;
import com.fueledbychai.okx.common.api.ws.listener.IOkxTickerListener;
import com.fueledbychai.okx.common.api.ws.listener.IOkxTradeListener;

/**
 * Public websocket contract for the Okx exchange integration.
 */
public interface IOkxWebSocketApi {

    /**
     * Opens the main websocket session when the exchange requires an explicit
     * bootstrap step.
     */
    void connect();

    /**
     * Subscribes to level-1 ticker updates for a single instrument.
     *
     * @param instrumentId the OKX instrument id
     * @param listener the listener that receives ticker updates
     */
    void subscribeTicker(String instrumentId, IOkxTickerListener listener);

    /**
     * Subscribes to funding-rate updates for a single instrument.
     *
     * @param instrumentId the OKX instrument id
     * @param listener the listener that receives funding-rate updates
     */
    void subscribeFundingRate(String instrumentId, IOkxFundingRateListener listener);

    /**
     * Subscribes to order-book updates for a single instrument.
     *
     * @param instrumentId the OKX instrument id
     * @param listener the listener that receives book updates
     */
    void subscribeOrderBook(String instrumentId, IOkxOrderBookListener listener);

    /**
     * Subscribes to trade prints for a single instrument.
     *
     * @param instrumentId the OKX instrument id
     * @param listener the listener that receives trade updates
     */
    void subscribeTrades(String instrumentId, IOkxTradeListener listener);

    /**
     * Closes all managed websocket connections and cancels reconnect work.
     */
    void disconnectAll();
}
