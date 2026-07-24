package com.fueledbychai.extended.common.api.order;

/**
 * Order lifecycle status as reported by the Extended API (REST {@code status}
 * field and the order WebSocket stream).
 */
public enum ExtendedOrderStatus {
    NEW, UNTRIGGERED, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED, EXPIRED, UNKNOWN;

    public static ExtendedOrderStatus fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        switch (value.trim().toUpperCase()) {
        case "NEW":
        case "OPEN":
        case "PLACED":
            return NEW;
        case "UNTRIGGERED":
            return UNTRIGGERED;
        case "PARTIALLY_FILLED":
        case "PARTIALLYFILLED":
            return PARTIALLY_FILLED;
        case "FILLED":
            return FILLED;
        case "CANCELLED":
        case "CANCELED":
            return CANCELLED;
        case "REJECTED":
            return REJECTED;
        case "EXPIRED":
            return EXPIRED;
        default:
            return UNKNOWN;
        }
    }
}
