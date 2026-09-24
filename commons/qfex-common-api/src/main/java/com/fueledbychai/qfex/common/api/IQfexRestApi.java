package com.fueledbychai.qfex.common.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

/**
 * QFEX REST API. Order entry is WebSocket-only on QFEX; see
 * {@link com.fueledbychai.qfex.common.api.ws.QfexTradeStream}.
 */
public interface IQfexRestApi {

    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    /** All contracts from {@code /refdata}, any status. */
    List<QfexContract> getContracts();

    /** One contract, or null if the symbol is unknown. */
    QfexContract getContract(String symbol);

    /**
     * Candles in {@code [from, to]}, oldest first. Resolutions: 1MIN, 5MINS,
     * 15MINS, 30MINS, 1HOUR, 4HOURS, 1DAY. Returns the raw
     * {@code {"candles":[...]}} document re-ordered oldest first.
     */
    JsonNode getCandles(String symbol, String resolution, Instant from, Instant to);

    /** Top-of-book snapshot from {@code /md/orderbook/{symbol}}. */
    JsonNode getOrderBook(String symbol);

    /** Private: {@code {balance:{...}, positions:[...]}} from {@code /user/positions}. */
    JsonNode getPositions();

    boolean isPublicApiOnly();
}
