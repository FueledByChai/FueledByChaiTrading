package com.fueledbychai.deribit.common.api;

import com.fueledbychai.deribit.common.api.ws.listener.IDeribitOrderBookListener;
import com.fueledbychai.deribit.common.api.ws.listener.IDeribitTickerListener;
import com.fueledbychai.deribit.common.api.ws.listener.IDeribitTradeListener;

/**
 * Public websocket contract for the Deribit exchange integration.
 */
public interface IDeribitWebSocketApi {

    /**
     * Opens the shared websocket session if it is not already connected.
     */
    void connect();

    /**
     * Subscribes to level-1 style ticker updates for a single instrument.
     *
     * @param instrumentName the Deribit instrument name
     * @param listener the listener that receives ticker updates
     */
    void subscribeTicker(String instrumentName, IDeribitTickerListener listener);

    /**
     * Subscribes to the incremental order book for a single instrument.
     *
     * @param instrumentName the Deribit instrument name
     * @param listener the listener that receives book updates
     */
    void subscribeOrderBook(String instrumentName, IDeribitOrderBookListener listener);

    /**
     * Subscribes to trade updates for a single instrument.
     *
     * @param instrumentName the Deribit instrument name
     * @param listener the listener that receives trade batches
     */
    void subscribeTrades(String instrumentName, IDeribitTradeListener listener);

    /**
     * Closes all managed websocket connections and cancels reconnect work.
     */
    void disconnectAll();
}
