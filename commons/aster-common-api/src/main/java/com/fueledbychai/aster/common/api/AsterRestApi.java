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
 * REST client for Aster public spot and perpetual-futures APIs, plus the
 * existing futures private endpoints used by the broker implementation.
 */
public class AsterRestApi extends BaseRestApi implements IAsterRestApi {

    static final int DEFAULT_FUNDING_PERIOD_HOURS = 8;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);
    static final MediaType FORM_MEDIA_TYPE = MediaType.parse("application/x-www-form-urlencoded");

    protected final String futuresBaseUrl;
    protected final String spotBaseUrl;
    protected final String user;
    protected final String signer;
    protected final String privateKey;
    protected final boolean publicApiOnly;
    protected final AsterEip712Signer eip712Signer;
    protected final OkHttpClient client;
    protected final ObjectMapper objectMapper;
    protected final Map<InstrumentType, InstrumentDescriptor[]> descriptorsByType = new EnumMap<>(InstrumentType.class);
    protected final Map<InstrumentType, Map<String, InstrumentDescriptor>> descriptorByExchangeSymbolByType = new EnumMap<>(
            InstrumentType.class);
    protected final Map<InstrumentType, Map<String, InstrumentDescriptor>> descriptorByCommonSymbolByType = new EnumMap<>(
            InstrumentType.class);

    public AsterRestApi(String baseUrl) {
        this(baseUrl, deriveSpotBaseUrl(baseUrl), null, null, null);
    }

    public AsterRestApi(String futuresBaseUrl, String spotBaseUrl) {
        this(futuresBaseUrl, spotBaseUrl, null, null, null);
    }

    public AsterRestApi(String baseUrl, String user, String signer, String privateKey) {
        this(baseUrl, deriveSpotBaseUrl(baseUrl), user, signer, privateKey);
    }

    public AsterRestApi(String futuresBaseUrl, String spotBaseUrl, String user, String signer, String privateKey) {
        if (futuresBaseUrl == null || futuresBaseUrl.isBlank()) {
            throw new IllegalArgumentException("futuresBaseUrl is required");
        }
        if (spotBaseUrl == null || spotBaseUrl.isBlank()) {
            throw new IllegalArgumentException("spotBaseUrl is required");
        }
        this.futuresBaseUrl = normalizeBaseUrl(futuresBaseUrl);
        this.spotBaseUrl = normalizeBaseUrl(spotBaseUrl);
        this.user = user;
        this.signer = signer;
        this.privateKey = privateKey;
        this.publicApiOnly = user == null || signer == null || privateKey == null
                || user.isBlank() || signer.isBlank() || privateKey.isBlank();
        this.eip712Signer = publicApiOnly ? null : new AsterEip712Signer(privateKey);
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
        InstrumentDescriptor cached = lookupCachedDescriptor(normalized);
        if (cached != null) {
            return cached;
        }

        for (InstrumentType instrumentType : supportedInstrumentTypes()) {
            for (InstrumentDescriptor descriptor : getAllInstrumentsForType(instrumentType)) {
                if (normalized.equalsIgnoreCase(descriptor.getExchangeSymbol())
                        || normalized.equalsIgnoreCase(descriptor.getCommonSymbol())) {
                    return descriptor;
                }
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
        JsonNode root = publicRequest(futuresBaseUrl, "GET", "/fapi/v1/time", null);
        return new Date(root.path("serverTime").asLong(System.currentTimeMillis()));
    }

    @Override
    public String startUserDataStream() {
        requirePrivateApi();
        JsonNode root = apiKeyRequest(futuresBaseUrl, "POST", "/fapi/v3/listenKey");
        return root.path("listenKey").asText("");
    }

    @Override
    public void keepAliveUserDataStream(String listenKey) {
        requirePrivateApi();
        apiKeyRequest(futuresBaseUrl, "PUT", "/fapi/v3/listenKey");
    }

    @Override
    public void closeUserDataStream(String listenKey) {
        requirePrivateApi();
        apiKeyRequest(futuresBaseUrl, "DELETE", "/fapi/v3/listenKey");
    }

    @Override
    public JsonNode placeOrder(Map<String, String> params) {
        requirePrivateApi();
        return signedRequest(futuresBaseUrl, "POST", "/fapi/v3/order", params);
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
        return signedRequest(futuresBaseUrl, "DELETE", "/fapi/v3/order", params);
    }

    @Override
    public JsonNode cancelAllOpenOrders(String symbol) {
        requirePrivateApi();
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("symbol", required(symbol, "symbol"));
        return signedRequest(futuresBaseUrl, "DELETE", "/fapi/v3/allOpenOrders", params);
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
        return signedRequest(futuresBaseUrl, "GET", "/fapi/v3/order", params);
    }

    @Override
    public JsonNode getOpenOrders(String symbol) {
        requirePrivateApi();
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        if (symbol != null && !symbol.isBlank()) {
            params.put("symbol", symbol);
        }
        return signedRequest(futuresBaseUrl, "GET", "/fapi/v3/openOrders", params);
    }

    @Override
    public JsonNode getPositionRisk(String symbol) {
        requirePrivateApi();
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        if (symbol != null && !symbol.isBlank()) {
            params.put("symbol", symbol);
        }
        return signedRequest(futuresBaseUrl, "GET", "/fapi/v3/positionRisk", params);
    }

    @Override
    public JsonNode getAccountInformation() {
        requirePrivateApi();
        return signedRequest(futuresBaseUrl, "GET", "/fapi/v3/account", null);
    }

    @Override
    public JsonNode getBookTicker(String symbol) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        if (symbol != null && !symbol.isBlank()) {
            params.put("symbol", symbol);
        }
        return publicRequest(futuresBaseUrl, "GET", "/fapi/v1/ticker/bookTicker", params);
    }

    protected InstrumentDescriptor[] loadInstrumentsForType(InstrumentType instrumentType) {
        String path = instrumentType == InstrumentType.CRYPTO_SPOT ? "/api/v1/exchangeInfo" : "/fapi/v1/exchangeInfo";
        JsonNode root = publicRequest(baseUrlFor(instrumentType), "GET", path, null);
        List<InstrumentDescriptor> descriptors = parseInstrumentDescriptors(root, instrumentType);
        return descriptors.toArray(new InstrumentDescriptor[0]);
    }

    protected List<InstrumentDescriptor> parseInstrumentDescriptors(JsonNode root, InstrumentType instrumentType) {
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        if (root == null || !root.has("symbols")) {
            return descriptors;
        }

        for (JsonNode symbolNode : root.path("symbols")) {
            if (!isTradableSymbol(symbolNode, instrumentType)) {
                continue;
            }
            InstrumentDescriptor descriptor = toInstrumentDescriptor(symbolNode, instrumentType);
            if (descriptor == null) {
                continue;
            }
            descriptors.add(descriptor);
            descriptorByExchangeSymbol(instrumentType).put(descriptor.getExchangeSymbol().toUpperCase(Locale.US),
                    descriptor);
            descriptorByCommonSymbol(instrumentType).put(descriptor.getCommonSymbol().toUpperCase(Locale.US),
                    descriptor);
        }
        return descriptors;
    }

    protected boolean isTradableSymbol(JsonNode symbolNode, InstrumentType instrumentType) {
        if (symbolNode == null) {
            return false;
        }
        String status = symbolNode.path("status").asText("");
        if (!"TRADING".equalsIgnoreCase(status)) {
            return false;
        }
        if (instrumentType == InstrumentType.CRYPTO_SPOT) {
            return true;
        }
        String contractType = symbolNode.path("contractType").asText("");
        return "PERPETUAL".equalsIgnoreCase(contractType);
    }

    protected InstrumentDescriptor toInstrumentDescriptor(JsonNode symbolNode, InstrumentType instrumentType) {
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
        int fundingPeriodHours = instrumentType == InstrumentType.PERPETUAL_FUTURES ? DEFAULT_FUNDING_PERIOD_HOURS : 0;

        return new InstrumentDescriptor(instrumentType, Exchange.ASTER, baseAsset + "/" + quoteAsset, exchangeSymbol,
                baseAsset, quoteAsset, orderSizeIncrement, priceTickSize, minNotionalOrderSize, minOrderSize,
                fundingPeriodHours, BigDecimal.ONE, 1, exchangeSymbol);
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
        return publicRequest(futuresBaseUrl, method, path, params);
    }

    protected JsonNode publicRequest(String baseUrl, String method, String path, Map<String, String> params) {
        HttpUrl url = buildUrl(baseUrl, path, params);
        Request request = new Request.Builder().url(url).get().build();
        return execute(request);
    }

    protected JsonNode apiKeyRequest(String method, String path) {
        return apiKeyRequest(futuresBaseUrl, method, path);
    }

    protected JsonNode apiKeyRequest(String baseUrl, String method, String path) {
        // v3 API: listenKey endpoints are now signed requests with empty params
        return signedRequest(baseUrl, method, path, null);
    }

    protected JsonNode signedRequest(String method, String path, Map<String, String> params) {
        return signedRequest(futuresBaseUrl, method, path, params);
    }

    protected JsonNode signedRequest(String baseUrl, String method, String path, Map<String, String> params) {
        LinkedHashMap<String, String> signedParams = new LinkedHashMap<>();
        if (params != null) {
            signedParams.putAll(params);
        }
        signedParams.put("nonce", Long.toString(eip712Signer.getNonce()));
        signedParams.put("user", user);
        signedParams.put("signer", signer);

        String encodedParams = toQueryString(signedParams);
        String signature = eip712Signer.sign(encodedParams);
        signedParams.put("signature", signature);

        String normalizedMethod = method == null ? "GET" : method.toUpperCase(Locale.US);
        Request.Builder builder;
        if ("POST".equals(normalizedMethod) || "DELETE".equals(normalizedMethod)) {
            String body = toQueryString(signedParams);
            builder = new Request.Builder()
                    .url(buildUrl(baseUrl, path, null))
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("User-Agent", "FueledByChai/1.0");
            if ("POST".equals(normalizedMethod)) {
                builder.post(RequestBody.create(body.getBytes(StandardCharsets.UTF_8), FORM_MEDIA_TYPE));
            } else {
                builder.delete(RequestBody.create(body.getBytes(StandardCharsets.UTF_8), FORM_MEDIA_TYPE));
            }
        } else {
            builder = new Request.Builder()
                    .url(buildUrl(baseUrl, path, signedParams));
        }
        return execute(builder.build());
    }

    protected HttpUrl buildUrl(String path, Map<String, String> params) {
        return buildUrl(futuresBaseUrl, path, params);
    }

    protected HttpUrl buildUrl(String baseUrl, String path, Map<String, String> params) {
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
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES && instrumentType != InstrumentType.CRYPTO_SPOT) {
            throw new IllegalArgumentException("Unsupported Aster instrument type: " + instrumentType);
        }
    }

    protected void requirePrivateApi() {
        if (publicApiOnly) {
            throw new IllegalStateException("Aster private API requires user/signer/privateKey configuration.");
        }
    }

    protected String normalizeBaseUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    protected InstrumentDescriptor lookupCachedDescriptor(String normalizedSymbol) {
        for (InstrumentType instrumentType : supportedInstrumentTypes()) {
            InstrumentDescriptor byExchangeSymbol = descriptorByExchangeSymbol(instrumentType).get(normalizedSymbol);
            if (byExchangeSymbol != null) {
                return byExchangeSymbol;
            }

            InstrumentDescriptor byCommonSymbol = descriptorByCommonSymbol(instrumentType).get(normalizedSymbol);
            if (byCommonSymbol != null) {
                return byCommonSymbol;
            }
        }
        return null;
    }

    protected Map<String, InstrumentDescriptor> descriptorByExchangeSymbol(InstrumentType instrumentType) {
        return descriptorByExchangeSymbolByType.computeIfAbsent(instrumentType, ignored -> new ConcurrentHashMap<>());
    }

    protected Map<String, InstrumentDescriptor> descriptorByCommonSymbol(InstrumentType instrumentType) {
        return descriptorByCommonSymbolByType.computeIfAbsent(instrumentType, ignored -> new ConcurrentHashMap<>());
    }

    protected InstrumentType[] supportedInstrumentTypes() {
        return new InstrumentType[] { InstrumentType.PERPETUAL_FUTURES, InstrumentType.CRYPTO_SPOT };
    }

    protected String baseUrlFor(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.CRYPTO_SPOT ? spotBaseUrl : futuresBaseUrl;
    }

    protected static String deriveSpotBaseUrl(String futuresBaseUrl) {
        if (futuresBaseUrl == null || futuresBaseUrl.isBlank()) {
            throw new IllegalArgumentException("futuresBaseUrl is required");
        }

        String normalized = futuresBaseUrl.trim();
        String replaced = normalized.replace("://fapi.", "://sapi.");
        if (!replaced.equals(normalized)) {
            return replaced;
        }
        return normalized;
    }

    protected String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
