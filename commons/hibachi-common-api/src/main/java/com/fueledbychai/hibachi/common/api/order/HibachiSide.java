package com.fueledbychai.hibachi.common.api.order;

/**
 * Hibachi order side.
 *
 * <p>Wire values BID/ASK — BUY normalizes to BID, SELL normalizes to ASK (matches the
 * Python SDK behaviour at api.py:1286-1289, 1388-1391, 1657-1660).
 *
 * <p>Byte encoding in signed payloads is u32 big-endian: ASK=0, BID=1.
 */
public enum HibachiSide {

    BID("BID", 1),
    ASK("ASK", 0);

    private final String wireValue;
    private final int byteValue;

    HibachiSide(String wireValue, int byteValue) {
        this.wireValue = wireValue;
        this.byteValue = byteValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public int getByteValue() {
        return byteValue;
    }

    public static HibachiSide fromWireValue(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire value is null");
        }
        switch (wire.trim().toUpperCase()) {
            case "BID":
            case "BUY":
                return BID;
            case "ASK":
            case "SELL":
                return ASK;
            default:
                throw new IllegalArgumentException("Unknown Hibachi side: " + wire);
        }
    }
}
