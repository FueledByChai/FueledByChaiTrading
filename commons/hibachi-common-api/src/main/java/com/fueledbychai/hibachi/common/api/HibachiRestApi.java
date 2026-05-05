package com.fueledbychai.hibachi.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
 * REST client for Hibachi.
 *
 * <p>Public market endpoints (e.g. {@code /market/exchange-info}) hit the data-api host;
 * private trading endpoints (e.g. {@code /trade/account/info}) hit the trading host. The
 * private auth header is the raw {@code apiKey} value (NOT "Bearer ..."), matching the
 * Python SDK at {@code executors/httpx.py:130-135}.
 *
 * <p>Order place/modify/cancel are intentionally not exposed here — they live on the
 * trade WebSocket (see the broker module).
 */
public class HibachiRestApi extends BaseRestApi implements IHibachiRestApi {

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);

    protected final String tradingBaseUrl;
    protected final String dataBaseUrl;
    protected final String apiKey;
    // Hibachi requires accountId as a query parameter on every authenticated
    // /trade/* and /capital/* endpoint. Without it the server returns
    // {"errorCode":4,"message":"Missing accountId"} even when the
    // Authorization header is correct. Captured at construction so
    // privateRequest can inject it into every call.
    protected final String accountId;
    protected final String hibachiClient;
    protected final boolean publicApiOnly;
    protected final OkHttpClient client;
    protected final ObjectMapper objectMapper;
    protected final Map<String, HibachiContract> contractsBySymbol = new ConcurrentHashMap<>();
    protected final Map<InstrumentType, InstrumentDescriptor[]> descriptorsByType = new ConcurrentHashMap<>();
    protected volatile boolean exchangeInfoLoaded;

    public HibachiRestApi(String tradingBaseUrl, String dataBaseUrl, String hibachiClient) {
        this(tradingBaseUrl, dataBaseUrl, hibachiClient, null, null);
    }

    public HibachiRestApi(String tradingBaseUrl, String dataBaseUrl, String hibachiClient, String apiKey) {
        this(tradingBaseUrl, dataBaseUrl, hibachiClient, apiKey, null);
    }

    public HibachiRestApi(String tradingBaseUrl, String dataBaseUrl, String hibachiClient, String apiKey,
            String accountId) {
        if (tradingBaseUrl == null || tradingBaseUrl.isBlank()) {
            throw new IllegalArgumentException("tradingBaseUrl is required");
        }
        if (dataBaseUrl == null || dataBaseUrl.isBlank()) {
            throw new IllegalArgumentException("dataBaseUrl is required");
        }
        this.tradingBaseUrl = normalizeBaseUrl(tradingBaseUrl);
        this.dataBaseUrl = normalizeBaseUrl(dataBaseUrl);
        this.apiKey = apiKey;
        this.accountId = accountId;
        this.hibachiClient = hibachiClient == null || hibachiClient.isBlank()
                ? "FueledByChaiJavaSDK"
                : hibachiClient;
        this.publicApiOnly = apiKey == null || apiKey.isBlank();
        this.client = OkHttpClientFactory.create(REQUEST_TIMEOUT);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES) {
            return new InstrumentDescriptor[0];
        }
        ensureExchangeInfoLoaded();
        InstrumentDescriptor[] cached = descriptorsByType.get(instrumentType);
        return cached == null ? new InstrumentDescriptor[0] : cached;
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        ensureExchangeInfoLoaded();
        String normalized = symbol.trim().toUpperCase(Locale.US);
        for (InstrumentDescriptor descriptor : getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)) {
            if (normalized.equalsIgnoreCase(descriptor.getExchangeSymbol())
                    || normalized.equalsIgnoreCase(descriptor.getCommonSymbol())) {
                return descriptor;
            }
        }
        return null;
    }

    @Override
    public HibachiContract getContract(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        ensureExchangeInfoLoaded();
        return contractsBySymbol.get(symbol.trim().toUpperCase(Locale.US));
    }

    @Override
    public Map<String, HibachiContract> getAllContracts() {
        ensureExchangeInfoLoaded();
        return Collections.unmodifiableMap(contractsBySymbol);
    }

    @Override
    public boolean isPublicApiOnly() {
        return publicApiOnly;
    }

    @Override
    public Date getServerTime() {
        JsonNode root = publicRequest(tradingBaseUrl, "/exchange/utc-timestamp", null);
        long ts = root.path("utcTimestamp").asLong(System.currentTimeMillis());
        // Field may be in seconds or millis depending on API version; treat values < 1e12 as seconds.
        return new Date(ts < 1_000_000_000_000L ? ts * 1000L : ts);
    }

    @Override
    public JsonNode getMarketStats() {
        return publicRequest(dataBaseUrl, "/market/data/stats", null);
    }

    @Override
    public JsonNode getMarketStats(String symbol) {
        return publicRequest(dataBaseUrl, "/market/data/stats", symbolParams(symbol));
    }

    @Override
    public JsonNode getRecentTrades(String symbol) {
        return publicRequest(dataBaseUrl, "/market/data/trades", symbolParams(symbol));
    }

    @Override
    public JsonNode getOrderBookSnapshot(String symbol) {
        return publicRequest(dataBaseUrl, "/market/data/orderbook", symbolParams(symbol));
    }

    @Override
    public JsonNode getKlines(String symbol, String interval, Integer limit) {
        Map<String, String> params = new LinkedHashMap<>();
        if (symbol != null) params.put("symbol", symbol);
        if (interval != null) params.put("interval", interval);
        if (limit != null) params.put("limit", limit.toString());
        return publicRequest(dataBaseUrl, "/market/data/klines", params);
    }

    @Override
    public JsonNode getOpenInterest(String symbol) {
        return publicRequest(dataBaseUrl, "/market/data/open-interest", symbolParams(symbol));
    }

    @Override
    public JsonNode getFundingRates(String symbol) {
        return publicRequest(dataBaseUrl, "/market/data/funding-rates", symbolParams(symbol));
    }

    @Override
    public JsonNode getPrices() {
        return publicRequest(dataBaseUrl, "/market/data/prices", null);
    }

    @Override
    public JsonNode getTradeAccountInfo() {
        requirePrivateApi();
        return privateRequest(tradingBaseUrl, "/trade/account/info", null);
    }

    @Override
    public JsonNode getAccountTrades() {
        requirePrivateApi();
        return privateRequest(tradingBaseUrl, "/trade/account/trades", null);
    }

    @Override
    public JsonNode getOpenOrders() {
        requirePrivateApi();
        return privateRequest(tradingBaseUrl, "/trade/orders", null);
    }

    @Override
    public JsonNode getOrder(String orderId) {
        requirePrivateApi();
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderId", orderId);
        return privateRequest(tradingBaseUrl, "/trade/order", params);
    }

    @Override
    public JsonNode getOrderByClientId(String clientId) {
        requirePrivateApi();
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId is required");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("clientId", clientId);
        return privateRequest(tradingBaseUrl, "/trade/order", params);
    }

    /**
     * Modify an existing order via Hibachi's documented PUT /trade/order endpoint.
     *
     * <p>The trade WebSocket has an `order.modify` method but in practice the venue
     * does not respond — the request times out at the WS-await layer with no
     * acknowledgement. Modifies must go through this REST endpoint instead.
     *
     * <p>The body is sent as JSON; {@code accountId} is injected automatically (Hibachi
     * requires it on every private call). Per the API doc the caller's body should
     * already contain: {@code nonce}, {@code quantity}, {@code price}, {@code signature},
     * {@code maxFeesPercent}, and exactly one of {@code orderId} / {@code clientId} /
     * the original placement nonce.
     */
    @Override
    public JsonNode modifyOrder(Map<String, Object> body) {
        requirePrivateApi();
        if (body == null) {
            throw new IllegalArgumentException("body is required");
        }
        Map<String, Object> effective = new LinkedHashMap<>(body);
        if (accountId != null && !accountId.isBlank()) {
            effective.putIfAbsent("accountId", accountId);
        }
        return privatePutJson(tradingBaseUrl, "/trade/order", effective);
    }

    @Override
    public JsonNode getCapitalBalance() {
        requirePrivateApi();
        return privateRequest(tradingBaseUrl, "/capital/balance", null);
    }

    // ---------- internals ----------

    protected void ensureExchangeInfoLoaded() {
        if (exchangeInfoLoaded) {
            return;
        }
        synchronized (contractsBySymbol) {
            if (exchangeInfoLoaded) {
                return;
            }
            JsonNode root = publicRequest(dataBaseUrl, "/market/exchange-info", null);
            List<InstrumentDescriptor> descriptors = new ArrayList<>();
            JsonNode contractsNode = root.has("futureContracts") ? root.path("futureContracts") : root.path("contracts");
            for (JsonNode node : contractsNode) {
                HibachiContract contract = parseContract(node);
                if (contract == null) {
                    continue;
                }
                contractsBySymbol.put(contract.getSymbol().toUpperCase(Locale.US), contract);
                InstrumentDescriptor descriptor = toDescriptor(contract);
                if (descriptor != null) {
                    descriptors.add(descriptor);
                }
            }
            descriptorsByType.put(InstrumentType.PERPETUAL_FUTURES,
                    descriptors.toArray(new InstrumentDescriptor[0]));
            exchangeInfoLoaded = true;
        }
    }

    protected HibachiContract parseContract(JsonNode node) {
        if (node == null || !node.has("symbol") || !node.has("id")) {
            return null;
        }
        HibachiContract.Builder b = HibachiContract.builder()
                .id(node.path("id").asInt())
                .symbol(node.path("symbol").asText())
                .displayName(node.path("displayName").asText(null))
                .underlyingSymbol(node.path("underlyingSymbol").asText(null))
                .settlementSymbol(node.path("settlementSymbol").asText(null))
                .underlyingDecimals(node.path("underlyingDecimals").asInt())
                .settlementDecimals(node.path("settlementDecimals").asInt())
                .tickSize(decimal(node, "tickSize"))
                .stepSize(decimal(node, "stepSize"))
                .minOrderSize(decimal(node, "minOrderSize"))
                .minNotional(decimal(node, "minNotional"))
                .initialMarginRate(decimal(node, "initialMarginRate"))
                .maintenanceMarginRate(decimal(node, "maintenanceMarginRate"))
                .status(node.path("status").asText(null));
        if (node.has("orderbookGranularities")) {
            List<String> granularities = new ArrayList<>();
            node.path("orderbookGranularities").forEach(g -> granularities.add(g.asText()));
            b.orderbookGranularities(granularities);
        }
        b.marketOpenTimestamp(longOrNull(node, "marketOpenTimestamp"));
        b.marketCloseTimestamp(longOrNull(node, "marketCloseTimestamp"));
        b.marketCreationTimestamp(longOrNull(node, "marketCreationTimestamp"));
        return b.build();
    }

    protected InstrumentDescriptor toDescriptor(HibachiContract contract) {
        if (contract == null) {
            return null;
        }
        // Hibachi symbol "BTC/USDT-P" — splits into base "BTC" / quote "USDT".
        String symbol = contract.getSymbol();
        String base = contract.getUnderlyingSymbol();
        String quote = contract.getSettlementSymbol();
        if (base == null || quote == null) {
            int slash = symbol.indexOf('/');
            int dash = symbol.indexOf('-');
            if (slash > 0 && dash > slash) {
                base = symbol.substring(0, slash);
                quote = symbol.substring(slash + 1, dash);
            } else {
                return null;
            }
        }
        // Effective quoting tick: use the finest orderbookGranularity rather than
        // the raw tickSize. Hibachi's native tick is often absurdly fine
        // (SOL/USDT-P = 0.0000001 = 1e-9 bps at $88), which collapses every
        // tick-denominated number in a market-making strategy to zero. The
        // orderbook granularities list is the exchange's own declaration of the
        // coarser grids the orderbook aggregates to and matchers display — the
        // finest of those is the natural quoting grid. We still floor at the
        // raw tickSize in case granularities is empty or somehow finer.
        BigDecimal rawTick = contract.getTickSize() != null ? contract.getTickSize() : BigDecimal.ONE;
        BigDecimal tickSize = resolveEffectiveTickSize(rawTick, contract.getOrderbookGranularities());
        BigDecimal stepSize = contract.getStepSize() != null ? contract.getStepSize() : BigDecimal.ONE;
        BigDecimal minOrderSize = contract.getMinOrderSize() != null ? contract.getMinOrderSize() : BigDecimal.ONE;
        int minNotional = contract.getMinNotional() == null
                ? 1
                : contract.getMinNotional().max(BigDecimal.ONE).intValue();
        return new InstrumentDescriptor(
                InstrumentType.PERPETUAL_FUTURES,
                Exchange.HIBACHI,
                symbol, // commonSymbol — already canonical
                symbol, // exchangeSymbol — same
                base,
                quote,
                stepSize,
                tickSize,
                minNotional,
                minOrderSize,
                8,
                BigDecimal.ONE,
                1,
                Integer.toString(contract.getId()));
    }

    /**
     * Given the contract's native tick and its {@code orderbookGranularities}
     * list (e.g. {@code ['0.001','0.01','0.1','1']}), return the finest
     * granularity ≥ the native tick. Rationale: the finest granularity is the
     * resolution at which the exchange aggregates the public orderbook — quoting
     * finer than that is invisible to counterparties, and using the native tick
     * (which can be 1e-9 bps-equivalent) breaks every tick-denominated MM
     * parameter. Falls back to the native tick when granularities are missing,
     * malformed, or all finer than the native tick.
     *
     * <p>Per-instrument by construction: each Hibachi contract publishes its own
     * granularities list, so BTC's 0.1 and SOL's 0.001 both come through
     * automatically with no per-symbol logic.
     */
    static BigDecimal resolveEffectiveTickSize(BigDecimal rawTick, List<String> granularities) {
        if (granularities == null || granularities.isEmpty()) {
            return rawTick;
        }
        BigDecimal finest = null;
        for (String g : granularities) {
            if (g == null || g.isBlank()) {
                continue;
            }
            try {
                BigDecimal parsed = new BigDecimal(g.trim());
                if (parsed.signum() <= 0) {
                    continue;
                }
                // Skip granularities finer than the native tick — the exchange
                // won't accept quotes inside the native tick anyway.
                if (rawTick != null && parsed.compareTo(rawTick) < 0) {
                    continue;
                }
                if (finest == null || parsed.compareTo(finest) < 0) {
                    finest = parsed;
                }
            } catch (NumberFormatException ignored) {
                // Malformed granularity — ignore, try the next one.
            }
        }
        return finest != null ? finest : rawTick;
    }

    protected JsonNode publicRequest(String baseUrl, String path, Map<String, String> params) {
        HttpUrl url = buildUrl(baseUrl, path, params);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Hibachi-Client", hibachiClient)
                .addHeader("Accept", "application/json");
        return execute(builder.build());
    }

    protected JsonNode privateRequest(String baseUrl, String path, Map<String, String> params) {
        // Hibachi requires accountId on every private GET. Inject it from
        // construction-time config when callers pass null/empty params, and
        // don't overwrite if the caller already supplied one.
        Map<String, String> effectiveParams = params;
        if (accountId != null && !accountId.isBlank()) {
            effectiveParams = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
            effectiveParams.putIfAbsent("accountId", accountId);
        }
        HttpUrl url = buildUrl(baseUrl, path, effectiveParams);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", apiKey)
                .addHeader("Hibachi-Client", hibachiClient)
                .addHeader("Accept", "application/json");
        return execute(builder.build());
    }

    /**
     * PUT a JSON body to a private trading endpoint. Used by {@link #modifyOrder(Map)};
     * mirrors {@link #privateRequest} for header / auth handling but with a JSON body
     * instead of query parameters.
     */
    protected JsonNode privatePutJson(String baseUrl, String path, Map<String, Object> body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body == null ? Collections.emptyMap() : body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Hibachi request body", e);
        }
        // Hibachi's gateway rejects any Content-Type with parameters
        // (e.g. "application/json; charset=utf-8") with errorCode=8 —
        // it only accepts an exact "application/json" string. OkHttp
        // appends "; charset=utf-8" if we hand RequestBody a MediaType,
        // so pass null here and set the header manually.
        RequestBody requestBody = RequestBody.create(json, null);
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .put(requestBody)
                .addHeader("Authorization", apiKey)
                .addHeader("Hibachi-Client", hibachiClient)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json");
        return execute(builder.build());
    }

    protected HttpUrl buildUrl(String baseUrl, String path, Map<String, String> params) {
        HttpUrl.Builder b = HttpUrl.parse(baseUrl + path).newBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    b.addQueryParameter(e.getKey(), e.getValue());
                }
            }
        }
        return b.build();
    }

    protected JsonNode execute(Request request) {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Hibachi request failed with HTTP " + response.code() + ": " + body);
            }
            if (body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new RuntimeException("Hibachi request failed: " + e.getMessage(), e);
        }
    }

    protected Map<String, String> symbolParams(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        return params;
    }

    protected void requirePrivateApi() {
        if (publicApiOnly) {
            throw new IllegalStateException("Hibachi private API requires apiKey configuration");
        }
    }

    protected String normalizeBaseUrl(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        String text = node.path(field).asText("");
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        return node.path(field).asLong();
    }
}
