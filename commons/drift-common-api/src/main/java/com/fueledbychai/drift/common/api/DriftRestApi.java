package com.fueledbychai.drift.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.drift.common.api.model.DriftGatewayCancelRequest;
import com.fueledbychai.drift.common.api.model.DriftGatewayMarket;
import com.fueledbychai.drift.common.api.model.DriftGatewayOrder;
import com.fueledbychai.drift.common.api.model.DriftGatewayOrderRequest;
import com.fueledbychai.drift.common.api.model.DriftGatewayPosition;
import com.fueledbychai.drift.common.api.model.DriftMarket;
import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.drift.common.api.model.DriftOrderBookLevel;
import com.fueledbychai.drift.common.api.model.DriftOrderBookSnapshot;
import com.fueledbychai.http.BaseRestApi;
import com.fueledbychai.websocket.ProxyConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DriftRestApi extends BaseRestApi implements IDriftRestApi {

    protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    protected static final BigDecimal DEFAULT_PRICE_TICK_SIZE = new BigDecimal("0.000001");
    protected static final long PUBLIC_MARKETS_TTL_MILLIS = Duration.ofSeconds(30).toMillis();
    protected static final BigDecimal PRICE_PRECISION = new BigDecimal("1000000");
    protected static final BigDecimal BASE_PRECISION = new BigDecimal("1000000000");

    protected final String dataApiUrl;
    protected final String dlobRestUrl;
    protected final String gatewayRestUrl;
    protected final boolean publicApiOnly;
    protected final OkHttpClient client;

    protected volatile List<DriftMarket> cachedMarkets = Collections.emptyList();
    protected volatile long cachedMarketsTimestampMillis = 0L;

    public DriftRestApi(String dataApiUrl) {
        this(dataApiUrl, DriftConfiguration.getInstance().getDlobRestUrl(), null, null);
    }

    public DriftRestApi(String dataApiUrl, String gatewayRestUrl) {
        this(dataApiUrl, DriftConfiguration.getInstance().getDlobRestUrl(), gatewayRestUrl, null);
    }

    public DriftRestApi(String dataApiUrl, String dlobRestUrl, String gatewayRestUrl, OkHttpClient client) {
        if (dataApiUrl == null || dataApiUrl.isBlank()) {
            throw new IllegalArgumentException("dataApiUrl is required");
        }
        if (dlobRestUrl == null || dlobRestUrl.isBlank()) {
            throw new IllegalArgumentException("dlobRestUrl is required");
        }
        this.dataApiUrl = stripTrailingSlash(dataApiUrl);
        this.dlobRestUrl = stripTrailingSlash(dlobRestUrl);
        this.gatewayRestUrl = gatewayRestUrl == null || gatewayRestUrl.isBlank() ? null : stripTrailingSlash(gatewayRestUrl);
        this.publicApiOnly = this.gatewayRestUrl == null;
        this.client = client == null ? buildDefaultHttpClient() : client;
    }

    protected OkHttpClient buildDefaultHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(Duration.ofSeconds(10));
        builder.readTimeout(Duration.ofSeconds(15));
        builder.writeTimeout(Duration.ofSeconds(15));
        builder.proxy(ProxyConfig.getInstance().getProxy());
        return builder.build();
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        requireInstrumentType(instrumentType);

        Map<String, DriftGatewayMarket> gatewayBySymbol = indexGatewayMarkets();
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        for (DriftMarket market : getMarkets()) {
            if (market == null || market.getMarketType() == null) {
                continue;
            }
            if (market.getMarketType().getInstrumentType() != instrumentType) {
                continue;
            }
            descriptors.add(toInstrumentDescriptor(market, gatewayBySymbol.get(market.getSymbol())));
        }
        return descriptors.toArray(new InstrumentDescriptor[0]);
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        DriftMarket market = getMarket(symbol);
        if (market == null) {
            return null;
        }
        return toInstrumentDescriptor(market, indexGatewayMarkets().get(market.getSymbol()));
    }

    @Override
    public List<DriftMarket> getMarkets() {
        long now = System.currentTimeMillis();
        List<DriftMarket> current = cachedMarkets;
        if (!current.isEmpty() && now - cachedMarketsTimestampMillis < PUBLIC_MARKETS_TTL_MILLIS) {
            return current;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            current = cachedMarkets;
            if (!current.isEmpty() && now - cachedMarketsTimestampMillis < PUBLIC_MARKETS_TTL_MILLIS) {
                return current;
            }

            JsonObject root = getJson(getPublicUrlBuilder("stats/markets").build());
            JsonArray marketsArray = getArray(root, "markets");
            List<DriftMarket> parsed = new ArrayList<>();
            for (JsonElement marketElement : marketsArray) {
                if (marketElement != null && marketElement.isJsonObject()) {
                    parsed.add(parsePublicMarket(marketElement.getAsJsonObject()));
                }
            }
            cachedMarkets = Collections.unmodifiableList(parsed);
            cachedMarketsTimestampMillis = now;
            return cachedMarkets;
        }
    }

    @Override
    public DriftMarket getMarket(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        for (DriftMarket market : getMarkets()) {
            if (market != null && symbol.equalsIgnoreCase(market.getSymbol())) {
                return market;
            }
        }
        return null;
    }

    @Override
    public DriftOrderBookSnapshot getOrderBook(String marketName, DriftMarketType marketType) {
        if (marketName == null || marketName.isBlank()) {
            throw new IllegalArgumentException("marketName is required");
        }
        HttpUrl.Builder urlBuilder = HttpUrl.parse(dlobRestUrl + "/l2").newBuilder()
                .addQueryParameter("marketName", marketName);
        if (marketType != null) {
            urlBuilder.addQueryParameter("marketType", marketType.getApiValue());
        }
        return parseOrderBookSnapshot(getJson(urlBuilder.build()));
    }

    @Override
    public List<DriftGatewayMarket> getGatewayMarkets() {
        ensureGatewayAvailable();
        JsonObject root = getJson(getGatewayUrlBuilder("v2/markets").build());
        List<DriftGatewayMarket> markets = new ArrayList<>();
        addGatewayMarkets(markets, getArray(root, "perp"), DriftMarketType.PERP);
        addGatewayMarkets(markets, getArray(root, "spot"), DriftMarketType.SPOT);
        return markets;
    }

    @Override
    public List<DriftGatewayOrder> getOpenOrders() {
        ensureGatewayAvailable();
        JsonElement root = getJsonElement(getGatewayUrlBuilder("v2/orders").build());
        if (root.isJsonArray()) {
            return parseOrders(root.getAsJsonArray());
        }
        JsonObject object = root.getAsJsonObject();
        if (object.has("orders") && object.get("orders").isJsonArray()) {
            return parseOrders(object.getAsJsonArray("orders"));
        }
        if (object.has("openOrders") && object.get("openOrders").isJsonArray()) {
            return parseOrders(object.getAsJsonArray("openOrders"));
        }
        return Collections.emptyList();
    }

    @Override
    public List<DriftGatewayPosition> getPositions() {
        ensureGatewayAvailable();
        JsonObject root = getJson(getGatewayUrlBuilder("v2/positions").build());
        List<DriftGatewayPosition> positions = new ArrayList<>();
        positions.addAll(parsePositions(getArray(root, "perp"), DriftMarketType.PERP, true));
        positions.addAll(parsePositions(getArray(root, "spot"), DriftMarketType.SPOT, false));
        return positions;
    }

    @Override
    public DriftGatewayPosition getPerpPositionInfo(int marketIndex) {
        ensureGatewayAvailable();
        JsonObject root = getJson(getGatewayUrlBuilder("v2/positionInfo/" + marketIndex).build());
        return new DriftGatewayPosition(DriftMarketType.PERP, marketIndex, getBigDecimal(root, "amount"), "perp",
                getBigDecimal(root, "averageEntry"), getBigDecimal(root, "liquidationPrice"),
                getBigDecimal(root, "unrealizedPnl"), getBigDecimal(root, "unsettledPnl"),
                getBigDecimal(root, "oraclePrice"));
    }

    @Override
    public String placeOrder(DriftGatewayOrderRequest orderRequest) {
        Objects.requireNonNull(orderRequest, "orderRequest is required");
        ensureGatewayAvailable();
        JsonObject payload = new JsonObject();
        JsonArray orders = new JsonArray();
        orders.add(toOrderRequestJson(orderRequest));
        payload.add("orders", orders);
        return submitGatewayJson("v2/orders", "POST", payload);
    }

    @Override
    public String modifyOrder(DriftGatewayOrderRequest orderRequest) {
        Objects.requireNonNull(orderRequest, "orderRequest is required");
        ensureGatewayAvailable();
        JsonObject payload = new JsonObject();
        JsonArray orders = new JsonArray();
        orders.add(toOrderRequestJson(orderRequest));
        payload.add("orders", orders);
        return submitGatewayJson("v2/orders", "PATCH", payload);
    }

    @Override
    public String cancelOrder(DriftGatewayCancelRequest cancelRequest) {
        Objects.requireNonNull(cancelRequest, "cancelRequest is required");
        ensureGatewayAvailable();
        JsonObject payload = new JsonObject();
        if (cancelRequest.getMarketIndex() != null) {
            payload.addProperty("marketIndex", cancelRequest.getMarketIndex());
        }
        if (cancelRequest.getMarketType() != null) {
            payload.addProperty("marketType", cancelRequest.getMarketType().getApiValue());
        }
        if (!cancelRequest.getOrderIds().isEmpty()) {
            JsonArray orderIds = new JsonArray();
            cancelRequest.getOrderIds().forEach(orderId -> orderIds.add(orderId));
            payload.add("ids", orderIds);
        }
        if (!cancelRequest.getUserOrderIds().isEmpty()) {
            JsonArray userIds = new JsonArray();
            cancelRequest.getUserOrderIds().forEach(userId -> userIds.add(userId));
            payload.add("userIds", userIds);
        }
        return submitGatewayJson("v2/orders", "DELETE", payload);
    }

    @Override
    public String cancelAllOrders(Integer marketIndex, DriftMarketType marketType) {
        ensureGatewayAvailable();
        Request.Builder requestBuilder = new Request.Builder().url(getGatewayUrlBuilder("v2/orders").build());
        if (marketIndex == null && marketType == null) {
            requestBuilder.delete();
        } else {
            JsonObject payload = new JsonObject();
            if (marketIndex != null) {
                payload.addProperty("marketIndex", marketIndex);
            }
            if (marketType != null) {
                payload.addProperty("marketType", marketType.getApiValue());
            }
            requestBuilder.delete(RequestBody.create(payload.toString(), JSON));
        }
        return executeGatewayRequest(requestBuilder.build());
    }

    @Override
    public BigDecimal getTotalCollateral() {
        ensureGatewayAvailable();
        return getBigDecimal(getJson(getGatewayUrlBuilder("v2/collateral").build()), "total");
    }

    @Override
    public BigDecimal getFreeCollateral() {
        ensureGatewayAvailable();
        return getBigDecimal(getJson(getGatewayUrlBuilder("v2/collateral").build()), "free");
    }

    @Override
    public BigDecimal getInitialMarginRequirement() {
        ensureGatewayAvailable();
        return getBigDecimal(getJson(getGatewayUrlBuilder("v2/user/marginInfo").build()), "initial");
    }

    @Override
    public BigDecimal getMaintenanceMarginRequirement() {
        ensureGatewayAvailable();
        return getBigDecimal(getJson(getGatewayUrlBuilder("v2/user/marginInfo").build()), "maintenance");
    }

    @Override
    public boolean isPublicApiOnly() {
        return publicApiOnly;
    }

    protected void requireInstrumentType(InstrumentType instrumentType) {
        if (instrumentType == null) {
            throw new IllegalArgumentException("instrumentType is required");
        }
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES && instrumentType != InstrumentType.CRYPTO_SPOT) {
            throw new IllegalArgumentException("Unsupported instrument type: " + instrumentType);
        }
    }

    protected Map<String, DriftGatewayMarket> indexGatewayMarkets() {
        if (publicApiOnly) {
            return Collections.emptyMap();
        }
        try {
            return getGatewayMarkets().stream().collect(Collectors.toMap(DriftGatewayMarket::getSymbol, market -> market,
                    (left, right) -> left, HashMap::new));
        } catch (RuntimeException e) {
            baseLogger.warn("Unable to load Drift gateway markets for metadata enrichment: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    protected InstrumentDescriptor toInstrumentDescriptor(DriftMarket market, DriftGatewayMarket gatewayMarket) {
        DriftMarketType marketType = market.getMarketType();
        InstrumentType instrumentType = marketType.getInstrumentType();
        String baseAsset = firstNonBlank(market.getBaseAsset(), market.getSymbol().replace("-PERP", ""));
        String quoteAsset = firstNonBlank(market.getQuoteAsset(), "USDC");
        BigDecimal orderSizeIncrement = gatewayMarket != null && gatewayMarket.getAmountStep() != null
                ? gatewayMarket.getAmountStep()
                : inferOrderSizeIncrement(market);
        BigDecimal priceTickSize = gatewayMarket != null && gatewayMarket.getPriceStep() != null
                ? gatewayMarket.getPriceStep()
                : DEFAULT_PRICE_TICK_SIZE;
        BigDecimal minOrderSize = gatewayMarket != null && gatewayMarket.getMinOrderSize() != null
                ? gatewayMarket.getMinOrderSize()
                : inferMinOrderSize(market);
        int maxLeverage = market.getMaxLeverage() == null ? 1 : market.getMaxLeverage().intValue();
        String commonSymbol = baseAsset + "/" + quoteAsset;
        String instrumentId = String.valueOf(market.getMarketIndex());

        return new InstrumentDescriptor(instrumentType, Exchange.DRIFT, commonSymbol, market.getSymbol(), baseAsset,
                quoteAsset, orderSizeIncrement, priceTickSize, 1, minOrderSize, marketType == DriftMarketType.PERP ? 1 : 0,
                BigDecimal.ONE, maxLeverage, instrumentId);
    }

    protected BigDecimal inferOrderSizeIncrement(DriftMarket market) {
        if (market.getMinAmount() != null && market.getMinAmount().compareTo(BigDecimal.ZERO) > 0) {
            return market.getMinAmount();
        }
        return BigDecimal.ONE.scaleByPowerOfTen(-Math.max(0, Math.min(market.getPrecision(), 9)));
    }

    protected BigDecimal inferMinOrderSize(DriftMarket market) {
        if (market.getMinAmount() != null && market.getMinAmount().compareTo(BigDecimal.ZERO) > 0) {
            return market.getMinAmount();
        }
        return inferOrderSizeIncrement(market);
    }

    protected DriftMarket parsePublicMarket(JsonObject market) {
        JsonObject limits = getObject(market, "limits");
        JsonObject leverage = getObject(limits, "leverage");
        JsonObject amount = getObject(limits, "amount");
        JsonObject fees = getObject(market, "fees");
        JsonObject openInterest = getObject(market, "openInterest");
        JsonObject fundingRate = getObject(market, "fundingRate");
        JsonObject priceHigh = getObject(market, "priceHigh");
        JsonObject priceLow = getObject(market, "priceLow");

        String symbol = getString(market, "symbol");
        DriftMarketType marketType = DriftMarketType.fromString(getString(market, "marketType"));
        String baseAsset = firstNonBlank(getString(market, "baseAsset"),
                marketType == DriftMarketType.PERP && symbol != null ? symbol.replace("-PERP", "") : symbol);
        String quoteAsset = firstNonBlank(getString(market, "quoteAsset"), "USDC");

        return new DriftMarket(symbol, getInt(market, "marketIndex", 0), marketType, baseAsset, quoteAsset,
                getString(market, "status"), getInt(market, "precision", 6), getBigDecimal(amount, "min"),
                getBigDecimal(amount, "max"), getBigDecimal(leverage, "min"), getBigDecimal(leverage, "max"),
                getBigDecimal(fees, "maker"), getBigDecimal(fees, "taker"), getBigDecimal(market, "oraclePrice"),
                getBigDecimal(market, "markPrice"), getBigDecimal(market, "price"), getBigDecimal(market, "baseVolume"),
                getBigDecimal(market, "quoteVolume"), getBigDecimal(openInterest, "long"),
                getBigDecimal(openInterest, "short"), getBigDecimal(fundingRate, "long"),
                getBigDecimal(fundingRate, "short"), getBigDecimal(market, "fundingRate24h"),
                getLongObject(market, "fundingRateUpdateTs"), getBigDecimal(market, "priceChange24h"),
                getBigDecimal(market, "priceChange24hPercent"), getBigDecimal(priceHigh, "fill"),
                getBigDecimal(priceLow, "fill"));
    }

    protected DriftOrderBookSnapshot parseOrderBookSnapshot(JsonObject root) {
        return new DriftOrderBookSnapshot(getString(root, "marketName"),
                root.has("marketType") ? DriftMarketType.fromString(getString(root, "marketType")) : DriftMarketType.PERP,
                getInt(root, "marketIndex", 0), getLong(root, "ts", 0L), getLong(root, "slot", 0L),
                scalePrice(getBigDecimal(root, "markPrice")), scalePrice(getBigDecimal(root, "bestBidPrice")),
                scalePrice(getBigDecimal(root, "bestAskPrice")), scalePrice(getBigDecimal(root, "oracle")),
                parseOrderBookLevels(getArray(root, "bids")), parseOrderBookLevels(getArray(root, "asks")));
    }

    protected List<DriftOrderBookLevel> parseOrderBookLevels(JsonArray levels) {
        List<DriftOrderBookLevel> parsed = new ArrayList<>();
        for (JsonElement levelElement : levels) {
            if (!levelElement.isJsonObject()) {
                continue;
            }
            JsonObject level = levelElement.getAsJsonObject();
            Map<String, BigDecimal> sources = new HashMap<>();
            JsonObject sourceObject = getObject(level, "sources");
            for (Map.Entry<String, JsonElement> entry : sourceObject.entrySet()) {
                sources.put(entry.getKey(), scaleSize(asBigDecimal(entry.getValue())));
            }
            parsed.add(new DriftOrderBookLevel(scalePrice(getBigDecimal(level, "price")),
                    scaleSize(getBigDecimal(level, "size")), sources));
        }
        return parsed;
    }

    protected void addGatewayMarkets(List<DriftGatewayMarket> target, JsonArray markets, DriftMarketType marketType) {
        for (JsonElement element : markets) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject market = element.getAsJsonObject();
            target.add(new DriftGatewayMarket(getInt(market, "marketIndex", 0), marketType, getString(market, "symbol"),
                    getBigDecimal(market, "priceStep"), getBigDecimal(market, "amountStep"),
                    getBigDecimal(market, "minOrderSize"), getBigDecimal(market, "initialMarginRatio"),
                    getBigDecimal(market, "maintenanceMarginRatio")));
        }
    }

    protected List<DriftGatewayOrder> parseOrders(JsonArray orders) {
        List<DriftGatewayOrder> parsed = new ArrayList<>();
        for (JsonElement element : orders) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject order = element.getAsJsonObject();
            parsed.add(new DriftGatewayOrder(getString(order, "orderType"), getInt(order, "marketIndex", 0),
                    DriftMarketType.fromString(getString(order, "marketType")), getBigDecimal(order, "amount"),
                    getBigDecimal(order, "filled"), getBigDecimal(order, "price"), getBoolean(order, "postOnly"),
                    getBoolean(order, "reduceOnly"), getInteger(order, "userOrderId"), getLongObject(order, "orderId"),
                    getBigDecimal(order, "oraclePriceOffset"), getString(order, "direction")));
        }
        return parsed;
    }

    protected List<DriftGatewayPosition> parsePositions(JsonArray positions, DriftMarketType marketType,
            boolean includeExtendedFields) {
        List<DriftGatewayPosition> parsed = new ArrayList<>();
        for (JsonElement element : positions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject position = element.getAsJsonObject();
            parsed.add(new DriftGatewayPosition(marketType, getInt(position, "marketIndex", 0),
                    getBigDecimal(position, "amount"), getString(position, "type"),
                    includeExtendedFields ? getBigDecimal(position, "averageEntry") : null,
                    includeExtendedFields ? getBigDecimal(position, "liquidationPrice") : null,
                    includeExtendedFields ? getBigDecimal(position, "unrealizedPnl") : null,
                    includeExtendedFields ? getBigDecimal(position, "unsettledPnl") : null,
                    includeExtendedFields ? getBigDecimal(position, "oraclePrice") : null));
        }
        return parsed;
    }

    protected JsonObject toOrderRequestJson(DriftGatewayOrderRequest request) {
        JsonObject order = new JsonObject();
        order.addProperty("marketIndex", request.getMarketIndex());
        order.addProperty("marketType", request.getMarketType().getApiValue());
        order.addProperty("amount", request.getAmount().toPlainString());
        if (request.getPrice() != null) {
            order.addProperty("price", request.getPrice().toPlainString());
        }
        order.addProperty("postOnly", request.isPostOnly());
        order.addProperty("orderType", request.getOrderType());
        if (request.getUserOrderId() != null) {
            order.addProperty("userOrderId", request.getUserOrderId());
        }
        order.addProperty("reduceOnly", request.isReduceOnly());
        if (request.getMaxTs() != null) {
            order.addProperty("maxTs", request.getMaxTs());
        }
        if (request.getOrderId() != null) {
            order.addProperty("orderId", request.getOrderId());
        }
        if (request.getOraclePriceOffset() != null) {
            order.addProperty("oraclePriceOffset", request.getOraclePriceOffset().toPlainString());
        }
        return order;
    }

    protected String submitGatewayJson(String path, String method, JsonObject payload) {
        HttpUrl url = getGatewayUrlBuilder(path).build();
        JsonObject safePayload = payload == null ? new JsonObject() : payload;
        RequestBody body = RequestBody.create(safePayload.toString(), JSON);
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if ("POST".equalsIgnoreCase(method)) {
            requestBuilder.post(body);
        } else if ("PATCH".equalsIgnoreCase(method)) {
            requestBuilder.patch(body);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            requestBuilder.delete(body);
        } else {
            throw new IllegalArgumentException("Unsupported method: " + method);
        }
        return executeGatewayRequest(requestBuilder.build());
    }

    protected String executeGatewayRequest(Request request) {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Drift gateway request failed with HTTP " + response.code() + ": " + body);
            }
            return extractGatewaySignature(body);
        } catch (IOException e) {
            throw new IllegalStateException("Drift gateway request failed", e);
        }
    }

    protected String extractGatewaySignature(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            return trimmed.replace("\"", "");
        }
        JsonObject root = JsonParser.parseString(trimmed).getAsJsonObject();
        for (String candidate : new String[] { "signature", "txSig", "transactionSignature" }) {
            if (root.has(candidate) && !root.get(candidate).isJsonNull()) {
                return root.get(candidate).getAsString();
            }
        }
        if (root.has("data") && root.get("data").isJsonObject()) {
            JsonObject data = root.getAsJsonObject("data");
            for (String candidate : new String[] { "signature", "txSig", "transactionSignature" }) {
                if (data.has(candidate) && !data.get(candidate).isJsonNull()) {
                    return data.get(candidate).getAsString();
                }
            }
        }
        return trimmed;
    }

    protected JsonObject getJson(HttpUrl url) {
        JsonElement element = getJsonElement(url);
        if (!element.isJsonObject()) {
            throw new IllegalStateException("Expected JSON object response from " + url);
        }
        return element.getAsJsonObject();
    }

    protected JsonElement getJsonElement(HttpUrl url) {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Drift request failed with HTTP " + response.code() + ": " + body);
            }
            return JsonParser.parseString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Drift request failed for " + url, e);
        }
    }

    protected HttpUrl.Builder getPublicUrlBuilder(String path) {
        return Objects.requireNonNull(HttpUrl.parse(dataApiUrl + "/" + path), "Invalid Drift public URL").newBuilder();
    }

    protected HttpUrl.Builder getGatewayUrlBuilder(String path) {
        ensureGatewayAvailable();
        return Objects.requireNonNull(HttpUrl.parse(gatewayRestUrl + "/" + path), "Invalid Drift gateway URL")
                .newBuilder();
    }

    protected void ensureGatewayAvailable() {
        if (publicApiOnly) {
            throw new IllegalStateException("Drift gateway API is not configured for this instance");
        }
    }

    protected JsonArray getArray(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || !object.get(member).isJsonArray()) {
            return new JsonArray();
        }
        return object.getAsJsonArray(member);
    }

    protected JsonObject getObject(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || !object.get(member).isJsonObject()) {
            return new JsonObject();
        }
        return object.getAsJsonObject(member);
    }

    protected String getString(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || object.get(member).isJsonNull()) {
            return null;
        }
        return object.get(member).getAsString();
    }

    protected BigDecimal getBigDecimal(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member)) {
            return null;
        }
        return asBigDecimal(object.get(member));
    }

    protected BigDecimal asBigDecimal(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        String value = element.getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    protected Integer getInteger(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || object.get(member).isJsonNull()) {
            return null;
        }
        return object.get(member).getAsInt();
    }

    protected int getInt(JsonObject object, String member, int defaultValue) {
        Integer value = getInteger(object, member);
        return value == null ? defaultValue : value;
    }

    protected Long getLongObject(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || object.get(member).isJsonNull()) {
            return null;
        }
        return object.get(member).getAsLong();
    }

    protected long getLong(JsonObject object, String member, long defaultValue) {
        Long value = getLongObject(object, member);
        return value == null ? defaultValue : value.longValue();
    }

    protected boolean getBoolean(JsonObject object, String member) {
        return object != null && member != null && object.has(member) && !object.get(member).isJsonNull()
                && object.get(member).getAsBoolean();
    }

    protected BigDecimal scalePrice(BigDecimal raw) {
        return raw == null ? null : raw.divide(PRICE_PRECISION, 6, RoundingMode.HALF_UP);
    }

    protected BigDecimal scaleSize(BigDecimal raw) {
        return raw == null ? null : raw.divide(BASE_PRECISION, 9, RoundingMode.HALF_UP);
    }

    protected String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    protected String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    protected String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
