package com.fueledbychai.broker.hibachi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Listener for parsed events arriving on the Hibachi account WebSocket.
 *
 * <p>Push-frame schemas for Hibachi's account stream are not exhaustively documented.
 * Snapshot parsing (sent in the {@code stream.start} response) is reliable; unsolicited
 * push frames are dispatched best-effort and unrecognized topics fall through to
 * {@link #onUnknownFrame(String, JsonNode)} so callers can iterate against real traffic.
 */
public interface HibachiAccountEventListener {

    /** Called once on connect with the {@code accountSnapshot} from {@code stream.start}. */
    default void onAccountSnapshot(JsonNode snapshot) {}

    /** Order lifecycle update (NEW / PARTIAL_FILL / FILLED / CANCELED / REJECTED). */
    default void onOrderUpdate(JsonNode frame) {}

    /**
     * Risk-engine rejection arriving as {@code event="order_request_rejected"}. The trade WS
     * gateway returns 200 + orderId before the risk engine validates, so this is the only
     * authoritative signal for rejections like {@code TooSmallNotionalValue}.
     */
    default void onOrderRejected(String orderId, String reason, JsonNode frame) {}

    /** A new fill / trade execution for the account. */
    default void onFill(JsonNode frame) {}

    /** Position-level update (size / averagePrice / mark / unrealized PnL changed). */
    default void onPositionUpdate(JsonNode frame) {}

    /** Account-wide balance / equity / margin update. */
    default void onBalanceUpdate(JsonNode frame) {}

    /** Catch-all for frames that don't match the recognized topics above. */
    default void onUnknownFrame(String topic, JsonNode frame) {}
}
