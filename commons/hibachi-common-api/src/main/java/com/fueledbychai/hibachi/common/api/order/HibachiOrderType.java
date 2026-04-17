package com.fueledbychai.hibachi.common.api.order;

public enum HibachiOrderType {

    LIMIT("LIMIT"),
    MARKET("MARKET");

    private final String wireValue;

    HibachiOrderType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }
}
