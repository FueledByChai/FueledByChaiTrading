package com.fueledbychai.extended.common.api;

import java.util.List;

import com.fueledbychai.broker.Position;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.extended.common.api.order.ExtendedOrder;
import com.google.gson.JsonObject;

/**
 * REST client contract for the Extended (extended.exchange) API.
 *
 * <p>
 * Reads are authenticated with an {@code X-Api-Key} header; order placement is
 * additionally authorized by a Stark signature on the order
 * ({@link ExtendedStarkOrderSigner}). There is no JWT.
 * </p>
 */
public interface IExtendedRestApi {

    // ---- public market data ----

    InstrumentDescriptor getInstrumentDescriptor(String market);

    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    InstrumentDescriptor[] getAllInstrumentsForTypes(InstrumentType[] instrumentTypes);

    /** Per-market StarkEx settlement config (needed for signing). */
    ExtendedMarketConfig getMarketConfig(String market);

    /** Order book snapshot ({@code /info/markets/{market}/orderbook}). */
    JsonObject getOrderBook(String market);

    /** Recent trades ({@code /info/markets/{market}/trades}). */
    JsonObject getTrades(String market);

    /** Market stats including mark/index/last/bid/ask ({@code /info/markets/{market}/stats}). */
    JsonObject getMarketStats(String market);

    /** OHLCV candles ({@code /info/candles/{market}/{candleType}}). */
    JsonObject getCandles(String market, String candleType, int limit);

    // ---- private account / trading ----

    List<Position> getPositionInfo();

    JsonObject getBalance();

    List<ExtendedOrder> getOpenOrders();

    List<ExtendedOrder> getOpenOrders(String market);

    ExtendedOrder getOrderById(String orderId);

    ExtendedOrder getOrderByExternalId(String externalId);

    /**
     * Places an order: computes the Stark settlement signature and POSTs to
     * {@code /user/order}. Returns the exchange order id.
     */
    String placeOrder(ExtendedOrder order);

    RestResponse cancelOrder(String orderId);

    RestResponse cancelOrderByExternalId(String externalId);

    boolean isPublicApiOnly();
}
