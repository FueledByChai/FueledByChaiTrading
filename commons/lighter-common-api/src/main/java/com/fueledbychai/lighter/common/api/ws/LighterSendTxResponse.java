package com.fueledbychai.lighter.common.api.ws;

public class LighterSendTxResponse {

    private final String requestId;
    private final Integer code;
    private final String message;
    private final String rawMessage;

    public LighterSendTxResponse(String requestId, Integer code, String message, String rawMessage) {
        this.requestId = requestId;
        this.code = code;
        this.message = message;
        this.rawMessage = rawMessage;
    }

    public String getRequestId() {
        return requestId;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public boolean isSuccess() {
        return code != null && code == 200;
    }
}
