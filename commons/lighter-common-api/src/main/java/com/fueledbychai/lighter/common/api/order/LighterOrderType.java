package com.fueledbychai.lighter.common.api.order;

/**
 * Lighter order type constants as defined by the signer SDK.
 */
public enum LighterOrderType {
    LIMIT(0),
    MARKET(1),
    STOP_LOSS(2),
    STOP_LOSS_LIMIT(3),
    TAKE_PROFIT(4),
    TAKE_PROFIT_LIMIT(5),
    TWAP(6);

    private final int code;

    LighterOrderType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
