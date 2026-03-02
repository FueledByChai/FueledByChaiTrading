package com.fueledbychai.binance.model;

/**
 * Enumeration of Binance order types.
 */
enum BinanceOrderType {
    LIMIT("LIMIT"), LIMIT_MAKER("LIMIT_MAKER"), MARKET("MARKET"), STOP_LOSS("STOP_LOSS"),
    STOP_LOSS_LIMIT("STOP_LOSS_LIMIT"), TAKE_PROFIT("TAKE_PROFIT"), TAKE_PROFIT_LIMIT("TAKE_PROFIT_LIMIT");

    private final String value;

    BinanceOrderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Converts a string value to the corresponding enum.
     * 
     * @param value The string value
     * @return The corresponding enum value
     * @throws IllegalArgumentException if no matching enum is found
     */
    public static BinanceOrderType fromValue(String value) {
        for (BinanceOrderType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown order type: " + value);
    }
}

/**
 * Enumeration of Binance symbol status values.
 */
enum BinanceSymbolStatus {
    PRE_TRADING("PRE_TRADING"), TRADING("TRADING"), POST_TRADING("POST_TRADING"), END_OF_DAY("END_OF_DAY"),
    HALT("HALT"), AUCTION_MATCH("AUCTION_MATCH"), BREAK("BREAK");

    private final String value;

    BinanceSymbolStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static BinanceSymbolStatus fromValue(String value) {
        for (BinanceSymbolStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown symbol status: " + value);
    }
}

/**
 * Enumeration of Binance rate limit types.
 */
enum BinanceRateLimitType {
    REQUEST_WEIGHT("REQUEST_WEIGHT"), ORDERS("ORDERS"), RAW_REQUESTS("RAW_REQUESTS");

    private final String value;

    BinanceRateLimitType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static BinanceRateLimitType fromValue(String value) {
        for (BinanceRateLimitType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown rate limit type: " + value);
    }
}

/**
 * Enumeration of Binance rate limit intervals.
 */
enum BinanceRateLimitInterval {
    SECOND("SECOND"), MINUTE("MINUTE"), DAY("DAY");

    private final String value;

    BinanceRateLimitInterval(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static BinanceRateLimitInterval fromValue(String value) {
        for (BinanceRateLimitInterval interval : values()) {
            if (interval.value.equals(value)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("Unknown rate limit interval: " + value);
    }
}