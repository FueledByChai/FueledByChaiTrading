package com.fueledbychai.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fueledbychai.diagnostics.SecretRedactor;
import com.fueledbychai.diagnostics.WireTap;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * OkHttp interceptor that publishes request/response pairs to {@link WireTap}.
 * No-op when no listener is attached, so production overhead is one volatile
 * read per request.
 */
final class WireTapInterceptor implements Interceptor {

    private static final long MAX_BODY_BYTES = 64 * 1024;

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        if (!WireTap.isEnabled()) {
            return chain.proceed(request);
        }

        long start = System.currentTimeMillis();
        String exchange = exchangeFromHost(request.url().host());
        String reqHeaders = formatHeaders(request.headers());
        String reqBody = SecretRedactor.redactBody(readRequestBody(request));

        WireTap.publishRest(new WireTap.RestEvent(
                start,
                WireTap.Direction.OUT,
                exchange,
                request.method(),
                request.url().toString(),
                0,
                reqHeaders,
                reqBody,
                0L));

        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException e) {
            WireTap.publishRest(new WireTap.RestEvent(
                    System.currentTimeMillis(),
                    WireTap.Direction.IN,
                    exchange,
                    request.method(),
                    request.url().toString(),
                    -1,
                    "",
                    "ERROR: " + e.getMessage(),
                    System.currentTimeMillis() - start));
            throw e;
        }

        long elapsed = System.currentTimeMillis() - start;
        String respHeaders = formatHeaders(response.headers());
        String respBody = SecretRedactor.redactBody(peekResponseBody(response));

        WireTap.publishRest(new WireTap.RestEvent(
                System.currentTimeMillis(),
                WireTap.Direction.IN,
                exchange,
                request.method(),
                request.url().toString(),
                response.code(),
                respHeaders,
                respBody,
                elapsed));

        return response;
    }

    private static String formatHeaders(Headers headers) {
        if (headers == null || headers.size() == 0) {
            return "";
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String name : headers.names()) {
            String value = headers.values(name).stream().reduce((a, b) -> a + ", " + b).orElse("");
            ordered.put(name, SecretRedactor.redactHeaderValue(name, value));
        }
        StringBuilder sb = new StringBuilder();
        ordered.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    private static String readRequestBody(Request request) {
        RequestBody body = request.body();
        if (body == null) {
            return "";
        }
        try {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            long size = buffer.size();
            long take = Math.min(size, MAX_BODY_BYTES);
            String result = buffer.readString(take, charsetOf(body.contentType()));
            if (size > take) {
                result = result + "\n... [truncated " + (size - take) + " bytes]";
            }
            return result;
        } catch (Exception e) {
            return "[unreadable request body: " + e.getMessage() + "]";
        }
    }

    private static String peekResponseBody(Response response) {
        ResponseBody body = response.body();
        if (body == null) {
            return "";
        }
        try {
            okhttp3.ResponseBody peeked = response.peekBody(MAX_BODY_BYTES);
            return peeked.string();
        } catch (Exception e) {
            return "[unreadable response body: " + e.getMessage() + "]";
        }
    }

    private static java.nio.charset.Charset charsetOf(MediaType type) {
        if (type == null) {
            return StandardCharsets.UTF_8;
        }
        java.nio.charset.Charset c = type.charset();
        return c != null ? c : StandardCharsets.UTF_8;
    }

    private static String exchangeFromHost(String host) {
        if (host == null) {
            return "unknown";
        }
        String h = host.toLowerCase();
        if (h.contains("hyperliquid")) return "HYPERLIQUID";
        if (h.contains("paradex")) return "PARADEX";
        if (h.contains("lighter")) return "LIGHTER";
        if (h.contains("hibachi")) return "HIBACHI";
        if (h.contains("aster")) return "ASTER";
        if (h.contains("bybit")) return "BYBIT";
        if (h.contains("okx") || h.contains("okex")) return "OKX";
        if (h.contains("drift")) return "DRIFT";
        if (h.contains("deribit")) return "DERIBIT";
        if (h.contains("binance")) return "BINANCE";
        if (h.contains("dydx")) return "DYDX";
        return host;
    }
}
