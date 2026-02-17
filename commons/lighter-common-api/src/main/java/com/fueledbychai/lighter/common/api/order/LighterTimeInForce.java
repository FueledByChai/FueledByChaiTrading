package com.fueledbychai.lighter.common.api.order;

/**
 * Lighter time-in-force constants as defined by the signer SDK.
 */
public enum LighterTimeInForce {
    IOC(0),
    GTT(1),
    POST_ONLY(2);

    private final int code;

    LighterTimeInForce(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
