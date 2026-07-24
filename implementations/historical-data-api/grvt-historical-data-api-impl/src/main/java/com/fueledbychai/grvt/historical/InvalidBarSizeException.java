package com.fueledbychai.grvt.historical;

/** Thrown when a requested bar size / unit has no corresponding GRVT candlestick interval. */
public class InvalidBarSizeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidBarSizeException(String message) {
        super(message);
    }
}
