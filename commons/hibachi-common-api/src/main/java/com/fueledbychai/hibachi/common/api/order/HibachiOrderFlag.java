package com.fueledbychai.hibachi.common.api.order;

public enum HibachiOrderFlag {

    // Wire value MUST be "POST_ONLY" — Hibachi's WS gateway responds
    // with {"error":"Invalid params: unknown variant `ALO`, expected one
    // of `POST_ONLY`, `IOC`, `REDUCE_ONLY`"} otherwise. The "ALO" label
    // visible in Hibachi's account UI is the display label for an order
    // that has POST_ONLY set, not the API enum value.
    POST_ONLY("POST_ONLY"),
    IOC("IOC"),
    REDUCE_ONLY("REDUCE_ONLY");

    private final String wireValue;

    HibachiOrderFlag(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }
}
