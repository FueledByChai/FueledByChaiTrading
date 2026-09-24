package com.fueledbychai.qfex.common.api;

import com.fueledbychai.qfex.common.api.ws.QfexMarketDataStream;
import com.fueledbychai.qfex.common.api.ws.QfexTradeStream;

/**
 * QFEX WebSocket entry points: the public market data stream and the
 * authenticated order-entry stream.
 */
public interface IQfexWebSocketApi {

    /** Starts the market data stream. */
    void connect();

    /** Starts the trade stream; requires API keys. */
    void connectOrderEntryWebSocket();

    /** Stops both streams and cancels reconnects. */
    void disconnectAll();

    QfexMarketDataStream marketData();

    /** The trade stream, or null when no API keys are configured. */
    QfexTradeStream trade();
}
