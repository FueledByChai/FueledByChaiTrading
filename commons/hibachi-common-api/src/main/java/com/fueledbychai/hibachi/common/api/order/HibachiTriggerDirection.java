package com.fueledbychai.hibachi.common.api.order;

/**
 * Which way the mark price must cross {@code triggerPrice} for a Hibachi
 * conditional order to activate. HIGH fires when price rises to/through the
 * trigger, LOW when it falls to/through it. JSON-only field — never part of
 * the signed payload.
 */
public enum HibachiTriggerDirection {

    HIGH("HIGH"),
    LOW("LOW");

    private final String wireValue;

    HibachiTriggerDirection(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }
}
