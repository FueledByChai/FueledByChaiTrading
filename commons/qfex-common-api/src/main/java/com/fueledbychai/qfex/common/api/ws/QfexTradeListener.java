package com.fueledbychai.qfex.common.api.ws;

import com.fasterxml.jackson.databind.JsonNode;

/** Callbacks from the authenticated trade socket. All run on the socket thread. */
public interface QfexTradeListener {

    /** Authenticated and subscribed; also fires after every reconnect. */
    default void onAuthenticated() {
    }

    default void onDisconnected() {
    }

    /** Body of an {@code order_response} (every order state change). */
    default void onOrderResponse(JsonNode order) {
    }

    /** Body of a {@code fill_response}. */
    default void onFill(JsonNode fill) {
    }

    /** Body of a {@code position_response}. */
    default void onPosition(JsonNode position) {
    }

    /** Body of a {@code balance_response}. */
    default void onBalance(JsonNode balance) {
    }

    /** An {@code err} that could not be matched to a pending request. */
    default void onError(JsonNode error) {
    }
}
