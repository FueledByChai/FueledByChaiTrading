package com.fueledbychai.extended.common.api;

/** Lightweight wrapper around an HTTP status code and response body. */
public class RestResponse {
    private final int httpCode;
    private final String body;

    public RestResponse(int httpCode, String body) {
        this.httpCode = httpCode;
        this.body = body;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public String getBody() {
        return body;
    }

    public boolean isSuccessful() {
        return httpCode >= 200 && httpCode < 300;
    }

    @Override
    public String toString() {
        return "RestResponse [httpCode=" + httpCode + ", body=" + body + "]";
    }
}
