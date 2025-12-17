package com.fueledbychai.broker;

public class BrokerRequestResult {

    public enum FailureType {
        NONE, ORDER_NOT_FOUND, ORDER_ALREADY_FILLED, ORDER_ALREADY_CANCELED, ORDER_ALREADY_COMPLETE, VALIDATION_FAILED,
        UNKNOWN
    }

    protected boolean success;
    protected boolean shouldRefresh = false;
    protected String message = "";
    protected FailureType failureType = FailureType.NONE;

    public BrokerRequestResult() {
        this.success = true;
        this.failureType = FailureType.NONE;
    }

    public BrokerRequestResult(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.failureType = success ? FailureType.NONE : FailureType.UNKNOWN;
    }

    public BrokerRequestResult(boolean success, boolean shouldRefresh, String message) {
        this.success = success;
        this.shouldRefresh = shouldRefresh;
        this.message = message;
        this.failureType = success ? FailureType.NONE : FailureType.UNKNOWN;
    }

    public BrokerRequestResult(boolean success, boolean shouldRefresh, String message, FailureType failureType) {
        this.success = success;
        this.shouldRefresh = shouldRefresh;
        this.message = message;
        this.failureType = failureType == null ? (success ? FailureType.NONE : FailureType.UNKNOWN) : failureType;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (success ? 1231 : 1237);
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        result = prime * result + ((failureType == null) ? 0 : failureType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BrokerRequestResult other = (BrokerRequestResult) obj;
        if (success != other.success)
            return false;
        if (message == null) {
            if (other.message != null)
                return false;
        } else if (!message.equals(other.message))
            return false;
        if (failureType != other.failureType)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "BrokerRequestResult [success=" + success + ", failureType=" + failureType + ", message=" + message
                + "]";
    }

}
