package com.fueledbychai.hibachi.common.api.ws.trade;

/**
 * JSON-RPC method names supported by the Hibachi trade WebSocket.
 *
 * <p>See {@code hibachi_xyz/api_ws_trade.py} in the Python SDK.
 */
public final class HibachiTradeMethod {

    public static final String ORDER_PLACE = "order.place";
    public static final String ORDER_MODIFY = "order.modify";
    public static final String ORDER_CANCEL = "order.cancel";
    public static final String ORDERS_CANCEL = "orders.cancel";
    public static final String ORDERS_BATCH = "orders.batch";
    public static final String ORDER_STATUS = "order.status";
    public static final String ORDERS_STATUS = "orders.status";
    public static final String ORDERS_ENABLE_CANCEL_ON_DISCONNECT = "orders.enableCancelOnDisconnect";

    private HibachiTradeMethod() {
    }
}
