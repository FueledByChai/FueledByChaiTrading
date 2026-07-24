package com.fueledbychai.grvt.common.api.model;

/**
 * GRVT time-in-force values. The {@code signValue} is the {@code uint8} encoding used in the
 * EIP-712 order signature (see {@code SignTimeInForce} in the GRVT Python SDK), while
 * {@link #name()} is the string sent over REST / WebSocket payloads.
 */
public enum GrvtTimeInForce {

    GOOD_TILL_TIME(1),
    ALL_OR_NONE(2),
    IMMEDIATE_OR_CANCEL(3),
    FILL_OR_KILL(4),
    RETAIL_PRICE_IMPROVEMENT(5);

    private final int signValue;

    GrvtTimeInForce(int signValue) {
        this.signValue = signValue;
    }

    public int getSignValue() {
        return signValue;
    }
}
