package com.fueledbychai.drift.common.api;

/**
 * Thrown when the Drift Gateway cannot be started, reached, or managed.
 */
public class DriftGatewayException extends RuntimeException {

    public DriftGatewayException(String message) {
        super(message);
    }

    public DriftGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
