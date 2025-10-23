/*
 * The MIT License
 *

 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.fueledbychai.data;

/**
 * Base Exception for Exceptions thrown by the library.
 * 
 * 
 */
public class ResponseException extends FueledByChaiException {

    enum HttpResponseErrorType {
        // 1xx Informational
        CONTINUE(100, "Continue"), SWITCHING_PROTOCOLS(101, "Switching Protocols"), PROCESSING(102, "Processing"),

        // 2xx Success
        OK(200, "OK"), CREATED(201, "Created"), ACCEPTED(202, "Accepted"), NO_CONTENT(204, "No Content"),

        // 3xx Redirection
        MOVED_PERMANENTLY(301, "Moved Permanently"), FOUND(302, "Found"), NOT_MODIFIED(304, "Not Modified"),
        TEMPORARY_REDIRECT(307, "Temporary Redirect"), PERMANENT_REDIRECT(308, "Permanent Redirect"),

        // 4xx Client Error
        BAD_REQUEST(400, "Bad Request"), UNAUTHORIZED(401, "Unauthorized"), PAYMENT_REQUIRED(402, "Payment Required"),
        FORBIDDEN(403, "Forbidden"), NOT_FOUND(404, "Not Found"), METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
        NOT_ACCEPTABLE(406, "Not Acceptable"), PROXY_AUTHENTICATION_REQUIRED(407, "Proxy Authentication Required"),
        REQUEST_TIMEOUT(408, "Request Timeout"), CONFLICT(409, "Conflict"), GONE(410, "Gone"),
        LENGTH_REQUIRED(411, "Length Required"), PRECONDITION_FAILED(412, "Precondition Failed"),
        PAYLOAD_TOO_LARGE(413, "Payload Too Large"), URI_TOO_LONG(414, "URI Too Long"),
        UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"), RANGE_NOT_SATISFIABLE(416, "Range Not Satisfiable"),
        EXPECTATION_FAILED(417, "Expectation Failed"), UNPROCESSABLE_ENTITY(422, "Unprocessable Entity"),
        TOO_MANY_REQUESTS(429, "Too Many Requests"),

        // 5xx Server Error
        INTERNAL_SERVER_ERROR(500, "Internal Server Error"), NOT_IMPLEMENTED(501, "Not Implemented"),
        BAD_GATEWAY(502, "Bad Gateway"), SERVICE_UNAVAILABLE(503, "Service Unavailable"),
        GATEWAY_TIMEOUT(504, "Gateway Timeout"), HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported"),
        INSUFFICIENT_STORAGE(507, "Insufficient Storage"),

        // Unknown/Default
        UNKNOWN(-1, "Unknown");

        private final int statusCode;
        private final String reasonPhrase;

        HttpResponseErrorType(int statusCode, String reasonPhrase) {
            this.statusCode = statusCode;
            this.reasonPhrase = reasonPhrase;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getReasonPhrase() {
            return reasonPhrase;
        }

        public static HttpResponseErrorType fromStatusCode(int statusCode) {
            for (HttpResponseErrorType type : values()) {
                if (type.statusCode == statusCode) {
                    return type;
                }
            }
            return UNKNOWN;
        }

        public boolean isInformational() {
            return statusCode >= 100 && statusCode < 200;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        public boolean isRedirection() {
            return statusCode >= 300 && statusCode < 400;
        }

        public boolean isClientError() {
            return statusCode >= 400 && statusCode < 500;
        }

        public boolean isServerError() {
            return statusCode >= 500 && statusCode < 600;
        }

        @Override
        public String toString() {
            return statusCode + " " + reasonPhrase;
        }
    }

    protected HttpResponseErrorType errorType;

    public ResponseException() {
    }

    public ResponseException(String message, HttpResponseErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public ResponseException(String message, int httpStatusCode) {
        super(message);
        this.errorType = HttpResponseErrorType.fromStatusCode(httpStatusCode);
    }

    public ResponseException(String message, String errorTypeCode) {
        super(message);
        try {
            // First try to parse as an integer status code
            int statusCode = Integer.parseInt(errorTypeCode);
            this.errorType = HttpResponseErrorType.fromStatusCode(statusCode);
        } catch (NumberFormatException e) {
            // If not a number, try to parse as enum name
            try {
                this.errorType = HttpResponseErrorType.valueOf(errorTypeCode.toUpperCase());
            } catch (IllegalArgumentException ex) {
                // If neither works, check for legacy category names
                this.errorType = HttpResponseErrorType.UNKNOWN;
            }
        }
    }

    public ResponseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResponseException(Throwable cause) {
        super(cause);
    }

    public HttpResponseErrorType getErrorType() {
        return errorType;
    }

    public int getStatusCode() {
        return errorType != null ? errorType.getStatusCode() : -1;
    }

    public String getReasonPhrase() {
        return errorType != null ? errorType.getReasonPhrase() : "Unknown";
    }

}
