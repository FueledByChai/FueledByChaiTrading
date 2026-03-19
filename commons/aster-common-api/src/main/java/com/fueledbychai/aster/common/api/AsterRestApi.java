package com.fueledbychai.aster.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.http.BaseRestApi;
import com.fueledbychai.http.OkHttpClientFactory;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * REST client for Aster perpetual futures.
 */
public class AsterRestApi extends BaseRestApi implements IAsterRestApi {

    static final int DEFAULT_FUNDING_PERIOD_HOURS = 8;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);
    static final String API_KEY_HEADER = "X-MBX-APIKEY";
    static final MediaType FORM_MEDIA_TYPE = MediaType.parse("application/x-www-form-urlencoded");

    protected final String baseUrl;
    protected final String apiKey;
    protected final String apiSecret;
    protected final boolean publicApiOnly;
    protected final long recvWindow;
    protected final OkHttpClient client;
    protected final ObjectMapper objectMapper;
    protected final Map<InstrumentType, InstrumentDescriptor[]> descriptorsByType = new EnumMap<>(InstrumentType.class);
    protected final Map<String, InstrumentDescriptor> descriptorByExchangeSymbol = new ConcurrentHashMap<>();
    protected final Map<String, InstrumentDescriptor> descriptorByCommonSymbol = new ConcurrentHashMap<>();

    public AsterRestApi(String baseUrl) {
        this(baseUrl, null, null, AsterConfiguration.getInstance().getRecvWindow());
    }

    public AsterRestApi(String baseUrl, String apiKey, String apiSecret) {
        this(baseUrl, apiKey, apiSecret, AsterConfiguration.getInstance().getRecvWindow());
    }

    public AsterRestApi(String baseUrl, String apiKey, String apiSecret, long recvWindow) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.recvWindow = recvWindow <= 0 ? AsterConfiguration.getInstance().getRecvWindow() : recvWindow;
        this.publicApiOnly = apiKey == null || apiSecret == null || apiKey.isBlank() || apiSecret.isBlank();
        this.client = OkHttpClientFactory.create(REQUEST_TIMEOUT);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        validateSupportedType(instrumentType);
        synchronized (descriptorsByType) {
            InstrumentDescriptor[] cached = descriptorsByType.get(instrumentType);
            if (cached != null) {
                return cached;
            }

            InstrumentDescriptor[] loaded = loadInstrumentsForType(instrumentType);
            descriptorsByType.put(instrumentType, loaded);
            return loaded;
        }
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }

        String normalized = symbol.trim().toUpperCase(Locale.US);
        InstrumentDescriptor byExchangeSymbol = descriptorByExchangeSymbol.get(normalized);
        if (byExchangeSymbol != null) {
            return byExchangeSymbol;
        }

        InstrumentDescriptor byCommonSymbol = descriptorByCommonSymbol.get(normalized);
        if (byCommonSymbol != null) {
            return byCommonSymbol;
        }

        for (InstrumentDescriptor descriptor : getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)) {
            if (normalized.equalsIgnoreCase(descriptor.getExchangeSymbol())
                    || normalized.equalsIgnoreCase(descriptor.getCommonSymbol())) {
                return descriptor;
            }
        }
        return null;
    }

    @Override
    public boolean isPublicApiOnly() {
        return publicApiOnly;
    }

    @Override
    public Date getServerTime() {
        JsonNode root = publicRequest("GET", "/fapi/v1/time", null);
        return new Date(root.path("serverTime").asLong(System.currentTimeMillis()));
    }

    @Override
    public String startUserDataStream() {
        requirePrivateApi();
        JsonNode root = apiKeyRequest("POST", "/fapi/v1/listenKey");
        return root.path("listenKey").asText("");
    }

    @Override
    public void keepAliveUserDataStream(String listenKey) {
        requirePrivateApi();
        apiKeyRequest("PUT", "/fapi/v1/listenKey");
    }

    @Override
    public void closeUserDataStream(String listenKey) {
        requirePrivateApi();
        apiKeyRequest("DELETE", "/fapi/v1/listenKey");
    }

    @Override
    public JsonNode placeOrder(Map<String, String> params) {
        requirePrivateApi();
        return signedRequest("POST", "/fapi/v1/order", params);
    }

    @Override
    public JsonNode cancelOrder(String symbol, String orderId, String origClientOrderId) {
        requirePrivateApi();
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("symbol", required(symbol, "symbol"));
        if (orderId != null && !orderId.isBlank()) {
            params.put("orderId", orderId);
        }
        if (origClientOrderId != null && !origClientOrderId.isBlank()) {
            params.put("origClientOrderId", origClientOrderId);
        }
        if (!params.containsKey("orderId") && !params.containsKey("origClientOrderId")) {
            throw new IllegalArgumentException("orderId or origClientOrderId is required");
        }
        return signedRequest("DELETE", "/fapi/v1/order", params);
    }

    @Override
    public JsonNode cancelAllOpenOrders(String symbol) {
        requirePrivateApi();
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("symbol", required(symbol, "symbol"));
        return signedRequest("DELETE", "/fapi/v1/allOpenOrders", params);
    }

    @Override
    public JsonNode queryOrder(String symbol, String orderId, String origClientOrderId) {
        requirePrivateApi();
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("symbol", required(symbol, "symbol"));
        if (orderId != null && !orderId.isBlank()) {
            params.put("orderId", orderId);
        }
        if (origClientOrderId != null && !origClientOrderId.isBlank()) {
            params.put("origClientOrderId", origClientOrderId);
        }
        if (!params.containsKey("orderId") && !params.containsKey("origClientOrderId")) {
            throw new IllegalArgumentException("orderId or origClientOrderId is required");
        }
        return signedRequest("GET", "/fapi/v1/order", params);
    }

    @Override
    public JsonNode getOpenOrders(String symbol) {
        requirePrivateApi();
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        if (symbol != null && !symbol.isBlank()) {
            params.put("symbol", symbol);
        }
        return signedRequest("GET", "/fapi/v1/openOrders", params);
    }

    @Override
    public JsonNode getPositionRisk(String symbol) {
        requirePrivateApi();
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        if (symbol != null && !symbol.isBlank()) {
            params.put("symbol", symbol);
        }
        return signedRequest("GET", "/fapi/v2/positionRisk", params);
    }

    protected InstrumentDescriptor[] loadInstrumentsForType(InstrumentType instrumentType) {
        JsonNode root = publicRequest("GET", "/fapi/v1/exchangeInfo", null);
        List<InstrumentDescriptor> descriptors = parseInstrumentDescriptors(root);
        return descriptors.toArray(new InstrumentDescriptor[0]);
    }

    protected List<InstrumentDescriptor> parseInstrumentDescriptors(JsonNode root) {
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        if (root == null || !root.has("symbols")) {
            return descriptors;
        }

        for (JsonNode symbolNode : root.path("symbols")) {
            if (!isTradablePerpetual(symbolNode)) {
                continue;
            }
            InstrumentDescriptor descriptor = toInstrumentDescriptor(symbolNode);
            if (descriptor == null) {
                continue;
            }
            descriptors.add(descriptor);
            descriptorByExchangeSymbol.put(descriptor.getExchangeSymbol().toUpperCase(Locale.US), descriptor);
            descriptorByCommonSymbol.put(descriptor.getCommonSymbol().toUpperCase(Locale.US), descriptor);
        }
        return descriptors;
    }

    protected boolean isTradablePerpetual(JsonNode symbolNode) {
        if (symbolNode == null) {
            return false;
        }
        String contractType = symbolNode.path("contractType").asText("");
        String status = symbolNode.path("status").asText("");
        return "PERPETUAL".equalsIgnoreCase(contractType) && "TRADING".equalsIgnoreCase(status);
    }

    protected InstrumentDescriptor toInstrumentDescriptor(JsonNode symbolNode) {
        String exchangeSymbol = symbolNode.path("symbol").asText("");
        String baseAsset = symbolNode.path("baseAsset").asText("");
        String quoteAsset = symbolNode.path("quoteAsset").asText("");
        if (exchangeSymbol.isBlank() || baseAsset.isBlank() || quoteAsset.isBlank()) {
            return null;
        }

        JsonNode priceFilter = findFilter(symbolNode, "PRICE_FILTER");
        JsonNode lotSizeFilter = findFilter(symbolNode, "LOT_SIZE");
        JsonNode notionalFilter = firstNonNull(findFilter(symbolNode, "MIN_NOTIONAL"), findFilter(symbolNode, "NOTIONAL"));

        BigDecimal priceTickSize = decimalOrDefault(priceFilter, "tickSize", BigDecimal.ONE);
        BigDecimal orderSizeIncrement = decimalOrDefault(lotSizeFilter, "stepSize", BigDecimal.ONE);
        BigDecimal minOrderSize = decimalOrDefault(lotSizeFilter, "minQty", BigDecimal.ONE);
        int minNotionalOrderSize = toMinNotional(notionalFilter);

        return new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES, Exchange.ASTER, baseAsset + "/" + quoteAsset,
                exchangeSymbol, baseAsset, quoteAsset, orderSizeIncrement, priceTickSize, minNotionalOrderSize,
                minOrderSize, DEFAULT_FUNDING_PERIOD_HOURS, BigDecimal.ONE, 1, exchangeSymbol);
    }

    protected JsonNode findFilter(JsonNode symbolNode, String filterType) {
        if (symbolNode == null || filterType == null || !symbolNode.has("filters")) {
            return null;
        }
        for (JsonNode filterNode : symbolNode.path("filters")) {
            if (filterType.equalsIgnoreCase(filterNode.path("filterType").asText(""))) {
                return filterNode;
            }
        }
        return null;
    }

    protected JsonNode firstNonNull(JsonNode left, JsonNode right) {
        return left != null ? left : right;
    }

    protected BigDecimal decimalOrDefault(JsonNode node, String field, BigDecimal defaultValue) {
        if (node == null || field == null) {
            return defaultValue;
        }
        String raw = node.path(field).asText("");
        if (raw.isBlank()) {
            return defaultValue;
        }
        return new BigDecimal(raw).stripTrailingZeros();
    }

    protected int toMinNotional(JsonNode notionalFilter) {
        if (notionalFilter == null) {
            return 1;
        }
        BigDecimal value = decimalOrDefault(notionalFilter, "notional",
                decimalOrDefault(notionalFilter, "minNotional", BigDecimal.ONE));
        return value.max(BigDecimal.ONE).intValue();
    }

    protected JsonNode publicRequest(String method, String path, Map<String, String> params) {
        HttpUrl url = buildUrl(path, params);
        Request.Builder builder = new Request.Builder().url(url);
        applyMethod(builder, method);
        return execute(builder.build());
    }

    protected JsonNode apiKeyRequest(String method, String path) {
        Request.Builder builder = new Request.Builder()
                .url(buildUrl(path, null))
                .addHeader(API_KEY_HEADER, apiKey);
        applyMethod(builder, method);
        return execute(builder.build());
    }

    protected JsonNode signedRequest(String method, String path, Map<String, String> params) {
        LinkedHashMap<String, String> signedParams = new LinkedHashMap<>();
        if (params != null) {
            signedParams.putAll(params);
        }
        if (!signedParams.containsKey("recvWindow")) {
            signedParams.put("recvWindow", Long.toString(recvWindow));
        }
        signedParams.put("timestamp", Long.toString(System.currentTimeMillis()));

        String queryString = toQueryString(signedParams);
        String signature = sign(queryString);
        HttpUrl.Builder urlBuilder = buildUrl(path, null).newBuilder();
        if (!queryString.isBlank()) {
            urlBuilder.encodedQuery(queryString + "&signature=" + signature);
        } else {
            urlBuilder.addQueryParameter("signature", signature);
        }

        Request.Builder builder = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader(API_KEY_HEADER, apiKey);
        applyMethod(builder, method);
        return execute(builder.build());
    }

    protected HttpUrl buildUrl(String path, Map<String, String> params) {
        HttpUrl.Builder builder = HttpUrl.parse(baseUrl + path).newBuilder();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                builder.addQueryParameter(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    protected void applyMethod(Request.Builder builder, String method) {
        String normalizedMethod = method == null ? "GET" : method.toUpperCase(Locale.US);
        switch (normalizedMethod) {
            case "GET" -> builder.get();
            case "POST" -> builder.post(RequestBody.create(new byte[0], FORM_MEDIA_TYPE));
            case "PUT" -> builder.put(RequestBody.create(new byte[0], FORM_MEDIA_TYPE));
            case "DELETE" -> builder.delete(RequestBody.create(new byte[0], FORM_MEDIA_TYPE));
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }

    protected JsonNode execute(Request request) {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Aster request failed with HTTP " + response.code() + ": " + body);
            }
            if (body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new RuntimeException("Aster request failed: " + e.getMessage(), e);
        }
    }

    protected String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(signatureBytes.length * 2);
            for (byte b : signatureBytes) {
                builder.append(String.format(Locale.US, "%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign Aster request", e);
        }
    }

    protected String toQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (!first) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return builder.toString();
    }

    protected void validateSupportedType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES) {
            throw new IllegalArgumentException("Unsupported Aster instrument type: " + instrumentType);
        }
    }

    protected void requirePrivateApi() {
        if (publicApiOnly) {
            throw new IllegalStateException("Aster private API requires apiKey/apiSecret configuration.");
        }
    }

    protected String normalizeBaseUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    protected String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
