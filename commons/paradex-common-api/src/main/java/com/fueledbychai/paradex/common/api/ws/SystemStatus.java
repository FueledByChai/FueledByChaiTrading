package com.fueledbychai.paradex.common.api.ws;

public enum SystemStatus {
    OK("ok"), MAINTENANCE("maintenance"), CANCEL_ONLY("cancel_only"), POST_ONLY("post_only");

    private String statusString;

    private SystemStatus(String statusString) {
        this.statusString = statusString;
    }

    public String getStatusString() {
        return statusString;
    }

    public static SystemStatus fromString(String statusString) {
        for (SystemStatus status : SystemStatus.values()) {
            if (status.getStatusString().equalsIgnoreCase(statusString)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown SystemStatus: " + statusString);
    }
}
