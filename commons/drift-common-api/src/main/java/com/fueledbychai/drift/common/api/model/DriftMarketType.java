package com.fueledbychai.drift.common.api.model;

import com.fueledbychai.data.InstrumentType;

public enum DriftMarketType {
    PERP("perp", InstrumentType.PERPETUAL_FUTURES),
    SPOT("spot", InstrumentType.CRYPTO_SPOT);

    private final String apiValue;
    private final InstrumentType instrumentType;

    DriftMarketType(String apiValue, InstrumentType instrumentType) {
        this.apiValue = apiValue;
        this.instrumentType = instrumentType;
    }

    public String getApiValue() {
        return apiValue;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public static DriftMarketType fromString(String value) {
        if (value == null) {
            return null;
        }
        for (DriftMarketType marketType : values()) {
            if (marketType.apiValue.equalsIgnoreCase(value)) {
                return marketType;
            }
        }
        throw new IllegalArgumentException("Unknown Drift market type: " + value);
    }
}
