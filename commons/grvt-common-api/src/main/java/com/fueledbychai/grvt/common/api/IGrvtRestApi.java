package com.fueledbychai.grvt.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;

/**
 * REST contract for the GRVT exchange. Market-data calls are public; trade-data and account calls
 * require the session cookie obtained from {@link #login()} and an EVM private key for order
 * signing.
 */
public interface IGrvtRestApi {

    // ----- auth / session -----

    /** Authenticates with the configured api key and caches the session cookie. */
    void login();

    /** Refreshes the session cookie if it is missing or near expiry. */
    void refreshCookieIfNeeded();

    /** The current {@code gravity} session cookie value, or {@code null} if not authenticated. */
    String getSessionCookie();

    /** The GRVT account id returned by the login response ({@code X-Grvt-Account-Id}). */
    String getAccountId();

    boolean isPublicApiOnly();

    // ----- instruments / registry -----

    InstrumentDescriptor[] getAllInstrumentsForTypes(InstrumentType[] instrumentTypes);

    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    // ----- market data -----

    JsonNode getInstrument(String instrument);

    JsonNode getTicker(String instrument);

    JsonNode getMiniTicker(String instrument);

    JsonNode getOrderBook(String instrument, int depth);

    JsonNode getRecentTrades(String instrument, int limit);

    JsonNode getFundingRateHistory(String instrument, long startTimeNanos, long endTimeNanos, int limit);

    JsonNode getCandlestick(String instrument, String interval, String type, long startTimeNanos,
            long endTimeNanos, int limit);

    // ----- order entry (REST fallback for WS-RPC) -----

    /**
     * Signs the order with EIP-712 and POSTs it to {@code create_order}. Returns the response
     * {@code result} node, or an empty/absent node on failure.
     */
    JsonNode createOrder(GrvtOrder order);

    JsonNode cancelOrder(String orderId, String clientOrderId);

    JsonNode cancelAllOrders();

    // ----- account / positions / history -----

    JsonNode getOpenOrders();

    JsonNode getOrder(String orderId, String clientOrderId);

    JsonNode getPositions();

    JsonNode getAccountSummary();

    JsonNode getFillHistory(long startTimeNanos, long endTimeNanos, int limit);

    /**
     * Signs the given order and returns the request body GRVT expects for {@code create_order}
     * ({@code {"order": {...}}}). Shared with the WebSocket API so order entry over either transport
     * produces an identical signed payload.
     */
    JsonNode buildSignedOrderRequest(GrvtOrder order);
}
