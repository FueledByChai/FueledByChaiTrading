package com.fueledbychai.broker;

public enum BrokerStatus {

    OK, MAINTENANCE, CANCEL_ONLY_MODE, POST_ONLY_MODE, DOWN, UNKNOWN;

    public boolean isFullyOperational() {
        return this == OK;
    }
}
