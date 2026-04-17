package com.fueledbychai.hibachi.common.api.ws.trade;

/**
 * Hibachi order lifecycle status, normalized for the framework's broker layer.
 */
public enum HibachiOrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED,
    EXPIRED,
    UNKNOWN
}
