package com.fueledbychai.bybit.common.api;

import com.fueledbychai.bybit.common.api.ws.listener.IBybitOrderBookListener;
import com.fueledbychai.bybit.common.api.ws.listener.IBybitTickerListener;
import com.fueledbychai.bybit.common.api.ws.listener.IBybitTradeListener;
import com.fueledbychai.data.InstrumentType;

/**
 * Public websocket contract for Bybit market-data streams.
 */
public interface IBybitWebSocketApi {

    void connect();

    void subscribeTicker(String instrumentId, InstrumentType instrumentType, IBybitTickerListener listener);

    void subscribeOrderBook(String instrumentId, InstrumentType instrumentType, IBybitOrderBookListener listener);

    void subscribeTrades(String instrumentId, InstrumentType instrumentType, IBybitTradeListener listener);

    void disconnectAll();
}
