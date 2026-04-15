package com.fueledbychai.hibachi.common.api.order;

public enum HibachiOrderFlag {

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
