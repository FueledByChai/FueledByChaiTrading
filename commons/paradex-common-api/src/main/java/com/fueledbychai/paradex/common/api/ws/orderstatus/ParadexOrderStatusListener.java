package com.fueledbychai.paradex.common.api.ws.orderstatus;

import com.fueledbychai.websocket.IWebSocketEventListener;

/**
 * Listener for order status updates, compatible with
 * AbstractWebSocketProcessor.
 */
public interface ParadexOrderStatusListener extends IWebSocketEventListener<IParadexOrderStatusUpdate> {

    /**
     * Called when an order status update is received.
     */
    void orderStatusUpdated(IParadexOrderStatusUpdate orderStatus);

    /**
     * Default implementation for generic event notification.
     */
    @Override
    default void onWebSocketEvent(IParadexOrderStatusUpdate event) {
        orderStatusUpdated(event);
    }
}
