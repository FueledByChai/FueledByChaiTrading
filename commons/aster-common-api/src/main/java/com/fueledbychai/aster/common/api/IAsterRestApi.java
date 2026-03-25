package com.fueledbychai.aster.common.api;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

/**
 * Public REST contract for the Aster exchange integration.
 *
 * Keep this interface small and stable. Strategy code and higher-level factory
 * consumers should depend on this interface, while the concrete implementation
 * handles transport details, authentication, and payload normalization.
 */
public interface IAsterRestApi {

    /**
     * Returns known instrument descriptors for the requested instrument type.
     *
     * @param instrumentType the instrument type to load
     * @return the resolved instrument descriptors
     */
    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    /**
     * Resolves a single instrument descriptor by symbol.
     *
     * @param symbol the exchange symbol
     * @return the resolved descriptor, or {@code null} when unavailable
     */
    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    /**
     * Indicates whether this API instance was created without private
     * credentials.
     *
     * @return {@code true} when the API instance is public-only
     */
    boolean isPublicApiOnly();

    /**
     * Returns the exchange server time.
     */
    Date getServerTime();

    /**
     * Starts or refreshes the listen-key user data stream.
     */
    String startUserDataStream();

    /**
     * Keeps the listen-key user data stream alive.
     */
    void keepAliveUserDataStream(String listenKey);

    /**
     * Closes the active user data stream.
     */
    void closeUserDataStream(String listenKey);

    /**
     * Places a signed futures order.
     */
    JsonNode placeOrder(Map<String, String> params);

    /**
     * Cancels a signed futures order.
     */
    JsonNode cancelOrder(String symbol, String orderId, String origClientOrderId);

    /**
     * Cancels all open orders for a symbol.
     */
    JsonNode cancelAllOpenOrders(String symbol);

    /**
     * Queries a signed futures order.
     */
    JsonNode queryOrder(String symbol, String orderId, String origClientOrderId);

    /**
     * Returns current open orders, optionally filtered by symbol.
     */
    JsonNode getOpenOrders(String symbol);

    /**
     * Returns current position risk, optionally filtered by symbol.
     */
    JsonNode getPositionRisk(String symbol);

    /**
     * Returns the current futures account snapshot, including top-level balance and
     * margin totals.
     */
    JsonNode getAccountInformation();

    /**
     * Returns the current best bid/offer for a symbol.
     *
     * @param symbol the exchange symbol (e.g. "BTCUSDT")
     * @return JSON with bidPrice, bidQty, askPrice, askQty
     */
    JsonNode getBookTicker(String symbol);
}
