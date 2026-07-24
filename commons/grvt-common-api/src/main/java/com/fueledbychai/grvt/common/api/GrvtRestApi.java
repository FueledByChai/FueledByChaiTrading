package com.fueledbychai.grvt.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.grvt.common.api.model.GrvtInstrument;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;
import com.fueledbychai.grvt.common.api.model.GrvtOrderLeg;
import com.fueledbychai.grvt.common.api.model.GrvtSignature;
import com.fueledbychai.http.BaseRestApi;
import com.fueledbychai.http.OkHttpClientFactory;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * REST client for the GRVT exchange. Handles the api-key {@code ->} session-cookie auth flow,
 * market-data and trade-data POST endpoints, and EIP-712 order signing (shared with the WebSocket
 * API so order entry over either transport is identical).
 */
public class GrvtRestApi extends BaseRestApi implements IGrvtRestApi {

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);
    static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    static final String API_PREFIX = "/full/v1/";

    protected final GrvtEnvironment environment;
    protected final String apiKey;
    protected final String subAccountId;
    protected final boolean publicApiOnly;
    protected final GrvtEip712OrderSigner orderSigner;
    protected final OkHttpClient client;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected final Map<String, GrvtInstrument> instrumentsByName = new ConcurrentHashMap<>();
    protected final Map<String, InstrumentDescriptor> descriptorByExchangeSymbol = new ConcurrentHashMap<>();
    protected final Map<String, InstrumentDescriptor> descriptorByCommonSymbol = new ConcurrentHashMap<>();
    protected volatile boolean instrumentsLoaded = false;

    protected volatile String sessionCookie;
    protected volatile String accountId;
    protected volatile long cookieExpiryEpochSeconds = 0L;

    public GrvtRestApi(GrvtEnvironment environment) {
        this(environment, null, null, null);
    }

    public GrvtRestApi(GrvtEnvironment environment, String apiKey, String privateKey, String subAccountId) {
        if (environment == null) {
            throw new IllegalArgumentException("environment is required");
        }
        this.environment = environment;
        this.apiKey = apiKey;
        this.subAccountId = subAccountId;
        this.publicApiOnly = isBlank(apiKey) || isBlank(privateKey) || isBlank(subAccountId);
        this.orderSigner = isBlank(privateKey) ? null : new GrvtEip712OrderSigner(privateKey);
        this.client = OkHttpClientFactory.create(REQUEST_TIMEOUT);
    }

    @Override
    public boolean isPublicApiOnly() {
        return publicApiOnly;
    }

    @Override
    public String getSessionCookie() {
        return sessionCookie;
    }

    @Override
    public String getAccountId() {
        return accountId;
    }

    // ----------------------------------------------------------------- auth

    @Override
    public synchronized void login() {
        if (isBlank(apiKey)) {
            throw new IllegalStateException("GRVT login requires an api key.");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("api_key", apiKey);
        Request request = new Request.Builder()
                .url(authLoginUrl())
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                // GRVT's API-key login flow requires this routing cookie. Without it
                // login can return HTTP 200 without a usable trading session, and every
                // subsequent private endpoint returns code 1000 / HTTP 401.
                .addHeader("Cookie", "rm=true;")
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("GRVT login failed with HTTP " + response.code() + ": " + responseBody);
            }
            this.sessionCookie = null;
            this.cookieExpiryEpochSeconds = 0L;
            this.accountId = response.header("X-Grvt-Account-Id", accountId);
            if (isBlank(this.accountId) && !responseBody.isBlank()) {
                JsonNode loginBody = objectMapper.readTree(responseBody);
                this.accountId = loginBody.path("sub_account_id").asText(accountId);
            }
            for (String setCookie : response.headers("Set-Cookie")) {
                parseSetCookie(setCookie);
            }
            if (isBlank(sessionCookie)) {
                throw new IllegalStateException("GRVT login succeeded without a gravity session cookie");
            }
            baseLogger.info("GRVT login OK accountId={} cookieExpiryEpochSeconds={}", accountId,
                    cookieExpiryEpochSeconds);
        } catch (IOException e) {
            throw new RuntimeException("GRVT login failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void refreshCookieIfNeeded() {
        if (isBlank(apiKey)) {
            return;
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        boolean fresh = sessionCookie != null && (cookieExpiryEpochSeconds - nowSeconds) > 5;
        if (!fresh) {
            login();
        }
    }

    protected void parseSetCookie(String setCookie) {
        if (setCookie == null || setCookie.isBlank()) {
            return;
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long expiry = nowSeconds + GrvtConfiguration.getInstance().getCookieRefreshSeconds();
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            String lower = trimmed.toLowerCase(Locale.US);
            if (lower.startsWith("gravity=")) {
                this.sessionCookie = trimmed.substring("gravity=".length());
            } else if (lower.startsWith("expires=")) {
                try {
                    ZonedDateTime parsed = ZonedDateTime.parse(trimmed.substring("expires=".length()),
                            DateTimeFormatter.RFC_1123_DATE_TIME);
                    expiry = parsed.toEpochSecond();
                } catch (Exception ignored) {
                    // keep the refresh-interval based fallback
                }
            }
        }
        this.cookieExpiryEpochSeconds = expiry;
    }

    // ---------------------------------------------------------- market data

    @Override
    public JsonNode getInstrument(String instrument) {
        return marketDataResult("instrument", payload().put("instrument", instrument));
    }

    @Override
    public JsonNode getTicker(String instrument) {
        return marketDataResult("ticker", payload().put("instrument", instrument));
    }

    @Override
    public JsonNode getMiniTicker(String instrument) {
        return marketDataResult("mini", payload().put("instrument", instrument));
    }

    @Override
    public JsonNode getOrderBook(String instrument, int depth) {
        ObjectNode body = payload().put("instrument", instrument).put("aggregate", 1);
        if (depth > 0) {
            body.put("depth", depth);
        }
        return marketDataResult("book", body);
    }

    @Override
    public JsonNode getRecentTrades(String instrument, int limit) {
        ObjectNode body = payload().put("instrument", instrument);
        if (limit > 0) {
            body.put("limit", limit);
        }
        return marketDataResult("trade", body);
    }

    @Override
    public JsonNode getFundingRateHistory(String instrument, long startTimeNanos, long endTimeNanos, int limit) {
        ObjectNode body = payload().put("instrument", instrument);
        if (startTimeNanos > 0) {
            body.put("start_time", Long.toString(startTimeNanos));
        }
        if (endTimeNanos > 0) {
            body.put("end_time", Long.toString(endTimeNanos));
        }
        if (limit > 0) {
            body.put("limit", limit);
        }
        return marketDataResult("funding", body);
    }

    @Override
    public JsonNode getCandlestick(String instrument, String interval, String type, long startTimeNanos,
            long endTimeNanos, int limit) {
        ObjectNode body = payload().put("instrument", instrument);
        if (interval != null) {
            body.put("interval", interval);
        }
        if (type != null) {
            body.put("type", type);
        }
        if (startTimeNanos > 0) {
            body.put("start_time", Long.toString(startTimeNanos));
        }
        if (endTimeNanos > 0) {
            body.put("end_time", Long.toString(endTimeNanos));
        }
        if (limit > 0) {
            body.put("limit", limit);
        }
        return marketDataResult("kline", body);
    }

    // ----------------------------------------------------------- order entry

    @Override
    public JsonNode buildSignedOrderRequest(GrvtOrder order) {
        requirePrivateApi();
        ensureInstrumentsLoaded();
        orderSigner.signOrder(order, instrumentsByName, environment.getChainId());

        ObjectNode orderNode = objectMapper.createObjectNode();
        orderNode.put("sub_account_id", order.getSubAccountId());
        orderNode.put("is_market", order.isMarket());
        orderNode.put("time_in_force", order.getTimeInForce().name());
        orderNode.put("post_only", order.isPostOnly());
        orderNode.put("reduce_only", order.isReduceOnly());

        ArrayNode legs = orderNode.putArray("legs");
        for (GrvtOrderLeg leg : order.getLegs()) {
            ObjectNode legNode = legs.addObject();
            legNode.put("instrument", leg.getInstrument());
            legNode.put("size", leg.getSize().toPlainString());
            legNode.put("limit_price", leg.getLimitPrice().toPlainString());
            legNode.put("is_buying_asset", leg.isBuyingAsset());
        }

        GrvtSignature signature = order.getSignature();
        ObjectNode signatureNode = orderNode.putObject("signature");
        signatureNode.put("signer", signature.getSigner());
        signatureNode.put("r", signature.getR());
        signatureNode.put("s", signature.getS());
        signatureNode.put("v", signature.getV());
        signatureNode.put("expiration", signature.getExpiration());
        signatureNode.put("nonce", signature.getNonce());

        ObjectNode metadata = orderNode.putObject("metadata");
        metadata.put("client_order_id", order.getClientOrderId());

        ObjectNode request = objectMapper.createObjectNode();
        request.set("order", orderNode);
        return request;
    }

    @Override
    public JsonNode createOrder(GrvtOrder order) {
        JsonNode request = buildSignedOrderRequest(order);
        JsonNode response = tradeDataPost("create_order", request);
        return response.path("result");
    }

    @Override
    public JsonNode cancelOrder(String orderId, String clientOrderId) {
        requirePrivateApi();
        ObjectNode body = payload().put("sub_account_id", subAccountId);
        if (!isBlank(orderId)) {
            body.put("order_id", orderId);
        } else if (!isBlank(clientOrderId)) {
            body.put("client_order_id", clientOrderId);
        } else {
            throw new IllegalArgumentException("orderId or clientOrderId is required");
        }
        return tradeDataPost("cancel_order", body);
    }

    @Override
    public JsonNode cancelAllOrders() {
        requirePrivateApi();
        return tradeDataPost("cancel_all_orders", payload().put("sub_account_id", subAccountId));
    }

    // --------------------------------------------------- account / positions

    @Override
    public JsonNode getOpenOrders() {
        requirePrivateApi();
        return tradeDataPost("open_orders", payload().put("sub_account_id", subAccountId)).path("result");
    }

    @Override
    public JsonNode getOrder(String orderId, String clientOrderId) {
        requirePrivateApi();
        ObjectNode body = payload().put("sub_account_id", subAccountId);
        if (!isBlank(orderId)) {
            body.put("order_id", orderId);
        } else if (!isBlank(clientOrderId)) {
            body.put("client_order_id", clientOrderId);
        } else {
            throw new IllegalArgumentException("orderId or clientOrderId is required");
        }
        return tradeDataPost("order", body);
    }

    @Override
    public JsonNode getPositions() {
        requirePrivateApi();
        return tradeDataPost("positions", payload().put("sub_account_id", subAccountId)).path("result");
    }

    @Override
    public JsonNode getAccountSummary() {
        requirePrivateApi();
        return tradeDataPost("account_summary", payload().put("sub_account_id", subAccountId)).path("result");
    }

    @Override
    public JsonNode getFillHistory(long startTimeNanos, long endTimeNanos, int limit) {
        requirePrivateApi();
        ObjectNode body = payload().put("sub_account_id", subAccountId);
        if (startTimeNanos > 0) {
            body.put("start_time", Long.toString(startTimeNanos));
        }
        if (endTimeNanos > 0) {
            body.put("end_time", Long.toString(endTimeNanos));
        }
        if (limit > 0) {
            body.put("limit", limit);
        }
        return tradeDataPost("fill_history", body);
    }

    // --------------------------------------------------------- instruments

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForTypes(InstrumentType[] instrumentTypes) {
        ensureInstrumentsLoaded();
        List<InstrumentType> requested = instrumentTypes == null ? List.of() : List.of(instrumentTypes);
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        for (InstrumentDescriptor descriptor : descriptorByExchangeSymbol.values()) {
            if (requested.isEmpty() || requested.contains(descriptor.getInstrumentType())) {
                descriptors.add(descriptor);
            }
        }
        return descriptors.toArray(new InstrumentDescriptor[0]);
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        if (isBlank(symbol)) {
            return null;
        }
        ensureInstrumentsLoaded();
        String normalized = symbol.trim().toUpperCase(Locale.US);
        InstrumentDescriptor byExchange = descriptorByExchangeSymbol.get(normalized);
        if (byExchange != null) {
            return byExchange;
        }
        return descriptorByCommonSymbol.get(normalized);
    }

    protected synchronized void ensureInstrumentsLoaded() {
        if (instrumentsLoaded) {
            return;
        }
        JsonNode result = marketDataResult("all_instruments", payload().put("is_active", true));
        if (result != null && result.isArray()) {
            for (JsonNode node : result) {
                GrvtInstrument instrument = parseInstrument(node);
                if (instrument == null) {
                    continue;
                }
                instrumentsByName.put(instrument.getInstrument(), instrument);
                InstrumentDescriptor descriptor = toDescriptor(instrument);
                if (descriptor != null) {
                    descriptorByExchangeSymbol.put(descriptor.getExchangeSymbol().toUpperCase(Locale.US), descriptor);
                    descriptorByCommonSymbol.put(descriptor.getCommonSymbol().toUpperCase(Locale.US), descriptor);
                }
            }
        }
        instrumentsLoaded = true;
        baseLogger.info("GRVT loaded {} instruments", instrumentsByName.size());
    }

    protected GrvtInstrument parseInstrument(JsonNode node) {
        String name = node.path("instrument").asText("");
        if (name.isBlank()) {
            return null;
        }
        GrvtInstrument instrument = new GrvtInstrument();
        instrument.setInstrument(name);
        instrument.setInstrumentHash(node.path("instrument_hash").asText(""));
        instrument.setBase(node.path("base").asText(""));
        instrument.setQuote(node.path("quote").asText(""));
        instrument.setKind(node.path("kind").asText(""));
        instrument.setBaseDecimals(node.path("base_decimals").asInt(9));
        instrument.setQuoteDecimals(node.path("quote_decimals").asInt(9));
        instrument.setTickSize(decimalOrNull(node, "tick_size"));
        instrument.setMinSize(decimalOrNull(node, "min_size"));
        instrument.setFundingIntervalHours(node.path("funding_interval_hours").asInt(0));
        return instrument;
    }

    protected InstrumentDescriptor toDescriptor(GrvtInstrument instrument) {
        InstrumentType type = mapInstrumentType(instrument.getKind());
        if (type == null || isBlank(instrument.getBase()) || isBlank(instrument.getQuote())) {
            return null;
        }
        BigDecimal tickSize = instrument.getTickSize() == null ? BigDecimal.ONE : instrument.getTickSize();
        BigDecimal minSize = instrument.getMinSize() == null ? BigDecimal.ONE : instrument.getMinSize();
        int fundingPeriodHours = type == InstrumentType.PERPETUAL_FUTURES
                ? positiveOrDefault(instrument.getFundingIntervalHours(), 8)
                : 0;
        return new InstrumentDescriptor(type, Exchange.GRVT, instrument.getBase() + "/" + instrument.getQuote(),
                instrument.getInstrument(), instrument.getBase(), instrument.getQuote(), minSize, tickSize, 1, minSize,
                fundingPeriodHours, BigDecimal.ONE, 1, instrument.getInstrument());
    }

    protected int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    protected InstrumentType mapInstrumentType(String kind) {
        if (kind == null) {
            return null;
        }
        switch (kind.toUpperCase(Locale.US)) {
            case "PERPETUAL":
                return InstrumentType.PERPETUAL_FUTURES;
            case "FUTURE":
                return InstrumentType.FUTURES;
            case "CALL":
            case "PUT":
                return InstrumentType.OPTION;
            case "SPOT":
                return InstrumentType.CRYPTO_SPOT;
            default:
                return null;
        }
    }

    // -------------------------------------------------------------- helpers

    protected ObjectNode payload() {
        return objectMapper.createObjectNode();
    }

    protected JsonNode marketDataResult(String endpoint, JsonNode body) {
        return post(marketRestUrl() + API_PREFIX + endpoint, body, false).path("result");
    }

    protected JsonNode tradeDataPost(String endpoint, JsonNode body) {
        return post(tradeRestUrl() + API_PREFIX + endpoint, body, true);
    }

    protected String authLoginUrl() {
        return environment.getAuthLoginUrl();
    }

    protected String marketRestUrl() {
        return environment.getMarketRestUrl();
    }

    protected String tradeRestUrl() {
        return environment.getTradeRestUrl();
    }

    protected JsonNode post(String url, JsonNode body, boolean requireAuth) {
        return post(url, body, requireAuth, true);
    }

    private JsonNode post(String url, JsonNode body, boolean requireAuth, boolean retryUnauthorized) {
        if (requireAuth) {
            refreshCookieIfNeeded();
        }
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body == null ? "{}" : body.toString(), JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json");
        if (sessionCookie != null) {
            builder.addHeader("Cookie", "gravity=" + sessionCookie);
        }
        if (accountId != null) {
            builder.addHeader("X-Grvt-Account-Id", accountId);
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (requireAuth && retryUnauthorized && response.code() == 401) {
                invalidateSession();
                return post(url, body, true, false);
            }
            if (!response.isSuccessful()) {
                throw new IllegalStateException("GRVT request to " + url + " failed with HTTP " + response.code()
                        + ": " + responseBody);
            }
            if (responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        } catch (IOException e) {
            throw new RuntimeException("GRVT request to " + url + " failed: " + e.getMessage(), e);
        }
    }

    protected synchronized void invalidateSession() {
        sessionCookie = null;
        accountId = null;
        cookieExpiryEpochSeconds = 0L;
    }

    protected void requirePrivateApi() {
        if (publicApiOnly) {
            throw new IllegalStateException("GRVT private API requires api key, private key and sub account id.");
        }
    }

    protected BigDecimal decimalOrNull(JsonNode node, String field) {
        String raw = node.path(field).asText("");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    protected Map<String, GrvtInstrument> getInstrumentsByName() {
        ensureInstrumentsLoaded();
        return new LinkedHashMap<>(instrumentsByName);
    }
}
