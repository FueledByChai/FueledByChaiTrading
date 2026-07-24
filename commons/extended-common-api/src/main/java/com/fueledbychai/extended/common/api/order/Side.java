package com.fueledbychai.extended.common.api.order;

/** Order side as used by the Extended API ({@code BUY} / {@code SELL}). */
public enum Side {
    BUY, SELL;

    public boolean isBuy() {
        return this == BUY;
    }
}
