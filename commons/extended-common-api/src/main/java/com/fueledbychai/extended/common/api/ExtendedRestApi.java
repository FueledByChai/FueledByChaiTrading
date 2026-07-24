package com.fueledbychai.extended.common.api;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.Position;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.ResponseException;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.extended.common.api.order.ExtendedOrder;
import com.fueledbychai.extended.common.api.order.ExtendedOrderStatus;
import com.fueledbychai.http.BaseRestApi;
import com.fueledbychai.util.TickerRegistryFactory;
import com.fueledbychai.websocket.ProxyConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.swmansion.starknet.crypto.StarknetCurveSignature;
import com.swmansion.starknet.data.types.Felt;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OkHttp-based REST client for the Extended (extended.exchange) API.
 *
 * <p>
 * Public market-data calls require no auth; private calls add an
 * {@code X-Api-Key} header. Order placement additionally signs the StarkEx
 * perpetual order hash via {@link ExtendedStarkOrderSigner}.
 * </p>
 */
public class ExtendedRestApi extends BaseRestApi implements IExtendedRestApi {

    protected static final Logger logger = LoggerFactory.getLogger(ExtendedRestApi.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** Settlement expiration buffer added to the order expiry, in seconds (14 days). */
    private static final long SETTLEMENT_EXPIRATION_BUFFER_SECONDS = 14L * 24L * 60L * 60L;
    /** Default taker fee rate used when an order does not carry one. */
    private static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.0005");

    protected final OkHttpClient client;
    protected final String baseUrl;
    protected final Gson gson = new Gson();

    protected final String apiKey;
    protected final String vaultId;
    protected final boolean publicApiOnly;

    private final ExtendedStarkOrderSigner signer;

    private final Map<String, ExtendedMarketConfig> marketConfigCache = new ConcurrentHashMap<>();

    /** Public-only constructor (market data). */
    public ExtendedRestApi(String baseUrl) {
        this(baseUrl, null, null, null, null, StarknetDomain.MAINNET);
    }

    /**
     * Full constructor.
     *
     * @param baseUrl        REST base URL (e.g. {@code .../api/v1})
     * @param apiKey         API key for the {@code X-Api-Key} header (nullable =&gt; public only)
     * @param starkPrivateKey Stark private key (nullable)
     * @param starkPublicKey  Stark public key (nullable)
     * @param vaultId         collateral vault / position id (nullable)
     * @param domain          SNIP-12 signing domain
     */
    public ExtendedRestApi(String baseUrl, String apiKey, String starkPrivateKey, String starkPublicKey, String vaultId,
            StarknetDomain domain) {
        this.client = createHttpClient();
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.vaultId = vaultId;
        this.publicApiOnly = apiKey == null || starkPrivateKey == null || starkPublicKey == null || vaultId == null;
        this.signer = this.publicApiOnly ? null
                : new ExtendedStarkOrderSigner(starkPrivateKey, starkPublicKey, domain);
    }

    protected OkHttpClient createHttpClient() {
        Proxy proxy = ProxyConfig.getInstance().getProxy();
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (proxy != null && proxy != Proxy.NO_PROXY) {
            builder.proxy(proxy);
        }
        return builder.connectTimeout(Duration.ofSeconds(15)).readTimeout(Duration.ofSeconds(15))
                .writeTimeout(Duration.ofSeconds(15)).callTimeout(Duration.ofSeconds(15)).build();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public boolean isPublicApiOnly() {
        return publicApiOnly;
    }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    protected Request.Builder authed(Request.Builder builder) {
        if (apiKey != null) {
            builder.addHeader("X-Api-Key", apiKey);
        }
        return builder;
    }

    protected String httpGet(String url, boolean authed) {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (authed) {
            authed(builder);
        }
        Request request = builder.build();
        logger.debug("GET {}", url);
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                logger.error("Error response ({}): {}", response.code(), body);
                throw new ResponseException("Unexpected code " + response.code() + ": " + body, response.code());
            }
            return body;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new ResponseException("Network error: " + e.getMessage(), e);
        }
    }

    protected JsonObject getJson(String url, boolean authed) {
        return JsonParser.parseString(httpGet(url, authed)).getAsJsonObject();
    }

    /** Returns the {@code data} element of a standard Extended envelope. */
    protected JsonElement dataOf(JsonObject root) {
        if (root.has("data") && !root.get("data").isJsonNull()) {
            return root.get("data");
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Public market data
    // ------------------------------------------------------------------

    protected String marketsResponseBody() {
        return httpGet(baseUrl + "/info/markets", false);
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String market) {
        InstrumentDescriptor[] all = getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
        for (InstrumentDescriptor d : all) {
            if (d.getExchangeSymbol().equalsIgnoreCase(market)) {
                return d;
            }
        }
        return null;
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES) {
            return new InstrumentDescriptor[0];
        }
        return executeWithRetry(() -> parseMarkets(marketsResponseBody()), 3, 500);
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForTypes(InstrumentType[] instrumentTypes) {
        if (instrumentTypes == null) {
            return new InstrumentDescriptor[0];
        }
        for (InstrumentType t : instrumentTypes) {
            if (t == InstrumentType.PERPETUAL_FUTURES) {
                return getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
            }
        }
        return new InstrumentDescriptor[0];
    }

    /** Parses {@code /info/markets} into descriptors and fills the market-config cache. */
    protected InstrumentDescriptor[] parseMarkets(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonElement data = dataOf(root);
            if (data == null || !data.isJsonArray()) {
                logger.warn("No markets data in response");
                return new InstrumentDescriptor[0];
            }
            JsonArray markets = data.getAsJsonArray();
            List<InstrumentDescriptor> descriptors = new ArrayList<>();
            for (JsonElement el : markets) {
                JsonObject m = el.getAsJsonObject();
                String type = optString(m, "type", "PERPETUAL");
                if (!"PERPETUAL".equalsIgnoreCase(type)) {
                    continue;
                }
                String name = m.get("name").getAsString();
                String assetName = optString(m, "assetName", name.split("-")[0]);
                String collateralName = optString(m, "collateralAssetName", "USD");

                JsonObject tc = m.getAsJsonObject("tradingConfig");
                BigDecimal orderSizeIncrement = bd(tc, "minOrderSizeChange", BigDecimal.ONE);
                BigDecimal priceTickSize = bd(tc, "minPriceChange", BigDecimal.ONE);
                BigDecimal minOrderSize = bd(tc, "minOrderSize", BigDecimal.ONE);
                int maxLeverage = tc != null && tc.has("maxLeverage") ? tc.get("maxLeverage").getAsBigDecimal().intValue()
                        : 1;

                descriptors.add(new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES, Exchange.EXTENDED, assetName,
                        name, assetName, collateralName, orderSizeIncrement, priceTickSize, 0, minOrderSize, 1,
                        BigDecimal.ONE, maxLeverage, name));

                JsonObject l2 = m.getAsJsonObject("l2Config");
                if (l2 != null) {
                    marketConfigCache.put(name, new ExtendedMarketConfig(name, l2.get("syntheticId").getAsString(),
                            l2.get("syntheticResolution").getAsLong(), l2.get("collateralId").getAsString(),
                            l2.get("collateralResolution").getAsLong()));
                }
            }
            return descriptors.toArray(new InstrumentDescriptor[0]);
        } catch (Exception e) {
            logger.error("Error parsing markets: " + e.getMessage(), e);
            throw new FueledByChaiException("Error parsing Extended markets", e);
        }
    }

    @Override
    public ExtendedMarketConfig getMarketConfig(String market) {
        ExtendedMarketConfig cfg = marketConfigCache.get(market);
        if (cfg == null) {
            // Refresh the cache (parseMarkets populates it for every market).
            parseMarkets(marketsResponseBody());
            cfg = marketConfigCache.get(market);
        }
        if (cfg == null) {
            throw new FueledByChaiException("No market config found for " + market);
        }
        return cfg;
    }

    @Override
    public JsonObject getOrderBook(String market) {
        return getJson(baseUrl + "/info/markets/" + market + "/orderbook", false);
    }

    @Override
    public JsonObject getTrades(String market) {
        return getJson(baseUrl + "/info/markets/" + market + "/trades", false);
    }

    @Override
    public JsonObject getMarketStats(String market) {
        return getJson(baseUrl + "/info/markets/" + market + "/stats", false);
    }

    @Override
    public JsonObject getCandles(String market, String candleType, int limit) {
        HttpUrl base = HttpUrl.parse(baseUrl + "/info/candles/" + market + "/" + candleType);
        if (base == null) {
            throw new ResponseException("Invalid candles URL for " + market, 0);
        }
        String url = base.newBuilder().addQueryParameter("limit", String.valueOf(limit)).build().toString();
        return getJson(url, false);
    }

    // ------------------------------------------------------------------
    // Private account / trading
    // ------------------------------------------------------------------

    protected void checkPrivateApi() {
        if (publicApiOnly) {
            throw new FueledByChaiException("Extended API in public-only mode (missing API key / Stark credentials).");
        }
    }

    @Override
    public List<Position> getPositionInfo() {
        checkPrivateApi();
        return executeWithRetry(() -> parsePositions(httpGet(baseUrl + "/user/positions", true)), 3, 500);
    }

    protected List<Position> parsePositions(String responseBody) {
        List<Position> positions = new ArrayList<>();
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonElement data = dataOf(root);
        if (data == null || !data.isJsonArray()) {
            return positions;
        }
        for (JsonElement el : data.getAsJsonArray()) {
            JsonObject p = el.getAsJsonObject();
            String market = p.get("market").getAsString();
            String sideStr = optString(p, "side", "LONG");
            BigDecimal size = bd(p, "size", BigDecimal.ZERO).abs();
            BigDecimal avgPrice = bd(p, "openPrice", bd(p, "averageEntryPrice", BigDecimal.ZERO));
            BigDecimal liqPrice = bd(p, "liquidationPrice", BigDecimal.ZERO);

            Ticker ticker = TickerRegistryFactory.getInstance(Exchange.EXTENDED)
                    .lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, market);
            Position position = new Position(ticker);
            position.setSize(size);
            position.setAverageCost(avgPrice);
            position.setLiquidationPrice(liqPrice);
            position.setSide("SHORT".equalsIgnoreCase(sideStr) ? Side.SHORT : Side.LONG);
            position.setStatus(Position.Status.OPEN);
            positions.add(position);
        }
        return positions;
    }

    @Override
    public JsonObject getBalance() {
        checkPrivateApi();
        return getJson(baseUrl + "/user/balance", true);
    }

    @Override
    public List<ExtendedOrder> getOpenOrders() {
        return getOpenOrders(null);
    }

    @Override
    public List<ExtendedOrder> getOpenOrders(String market) {
        checkPrivateApi();
        return executeWithRetry(() -> {
            HttpUrl.Builder b = HttpUrl.parse(baseUrl + "/user/orders").newBuilder();
            if (market != null && !market.isEmpty()) {
                b.addQueryParameter("market", market);
            }
            String body = httpGet(b.build().toString(), true);
            return parseOrders(body);
        }, 3, 500);
    }

    protected List<ExtendedOrder> parseOrders(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonElement data = dataOf(root);
        if (data == null || !data.isJsonArray()) {
            return new ArrayList<>();
        }
        Type listType = new TypeToken<List<ExtendedOrder>>() {
        }.getType();
        List<ExtendedOrder> orders = gson.fromJson(data, listType);
        return orders == null ? new ArrayList<>() : orders;
    }

    @Override
    public ExtendedOrder getOrderById(String orderId) {
        checkPrivateApi();
        JsonObject root = getJson(baseUrl + "/user/orders/" + orderId, true);
        return parseSingleOrder(root);
    }

    @Override
    public ExtendedOrder getOrderByExternalId(String externalId) {
        checkPrivateApi();
        JsonObject root = getJson(baseUrl + "/user/orders/external/" + externalId, true);
        return parseSingleOrder(root);
    }

    protected ExtendedOrder parseSingleOrder(JsonObject root) {
        JsonElement data = dataOf(root);
        if (data == null) {
            return null;
        }
        if (data.isJsonArray()) {
            JsonArray arr = data.getAsJsonArray();
            if (arr.size() == 0) {
                return null;
            }
            return gson.fromJson(arr.get(0), ExtendedOrder.class);
        }
        return gson.fromJson(data, ExtendedOrder.class);
    }

    @Override
    public String placeOrder(ExtendedOrder order) {
        checkPrivateApi();

        ExtendedMarketConfig cfg = getMarketConfig(order.getMarket());
        boolean isBuy = order.getSide().isBuy();
        BigDecimal feeRate = order.getFee() != null ? order.getFee() : DEFAULT_FEE_RATE;

        long expiryMillis = order.getExpiryEpochMillis();
        if (expiryMillis <= 0) {
            expiryMillis = System.currentTimeMillis() + 60L * 60L * 1000L; // default +1h
            order.setExpiryEpochMillis(expiryMillis);
        }
        long expirationSeconds = expiryMillis / 1000L + SETTLEMENT_EXPIRATION_BUFFER_SECONDS;
        long nonce = ThreadLocalRandom.current().nextLong(0, 1L << 32);
        long positionId = Long.parseLong(vaultId);

        ExtendedStarkAmounts amounts = ExtendedStarkAmounts.compute(isBuy, order.getQty(), order.getPrice(), feeRate,
                cfg.getSyntheticResolution(), cfg.getCollateralResolution());

        Felt messageHash = signer.computeMessageHash(positionId, cfg.getSyntheticAssetId(), amounts.syntheticAmount(),
                cfg.getCollateralAssetId(), amounts.collateralAmount(), cfg.getCollateralAssetId(), amounts.feeAmount(),
                expirationSeconds, BigInteger.valueOf(nonce));
        StarknetCurveSignature sig = signer.sign(messageHash);

        JsonObject json = new JsonObject();
        json.addProperty("id", messageHash.getValue().toString());
        json.addProperty("market", order.getMarket());
        json.addProperty("type", order.getType().name());
        json.addProperty("side", order.getSide().name());
        json.addProperty("qty", order.getQty().toPlainString());
        json.addProperty("price", order.getPrice().toPlainString());
        json.addProperty("timeInForce", order.getTimeInForce().name());
        json.addProperty("reduceOnly", order.isReduceOnly());
        json.addProperty("postOnly", order.isPostOnly());
        json.addProperty("expiryEpochMillis", expiryMillis);
        json.addProperty("fee", feeRate.toPlainString());
        json.addProperty("nonce", Long.toString(nonce));
        if (order.getExternalId() != null) {
            json.addProperty("externalId", order.getExternalId());
        }

        JsonObject signature = new JsonObject();
        signature.addProperty("r", sig.getR().hexString());
        signature.addProperty("s", sig.getS().hexString());
        JsonObject settlement = new JsonObject();
        settlement.add("signature", signature);
        settlement.addProperty("starkKey", signer.getPublicKey().hexString());
        settlement.addProperty("collateralPosition", vaultId);
        json.add("settlement", settlement);

        RequestBody requestBody = RequestBody.create(json.toString(), JSON);
        Request request = authed(new Request.Builder().url(baseUrl + "/user/order").post(requestBody)).build();
        logger.info("Placing order: {}", json);

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                logger.error("Error response ({}): {}", response.code(), body);
                throw new ResponseException("Unexpected code " + response.code() + ": " + body, response.code());
            }
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonElement data = dataOf(root);
            if (data != null && data.isJsonObject() && data.getAsJsonObject().has("id")) {
                return data.getAsJsonObject().get("id").getAsString();
            }
            if (root.has("id")) {
                return root.get("id").getAsString();
            }
            return messageHash.getValue().toString();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new ResponseException("Network error placing order: " + e.getMessage(), e);
        }
    }

    @Override
    public RestResponse cancelOrder(String orderId) {
        checkPrivateApi();
        return delete(baseUrl + "/user/order/" + orderId);
    }

    @Override
    public RestResponse cancelOrderByExternalId(String externalId) {
        checkPrivateApi();
        return delete(baseUrl + "/user/order/external/" + externalId);
    }

    protected RestResponse delete(String url) {
        Request request = authed(new Request.Builder().url(url).delete()).build();
        logger.info("DELETE {}", url);
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                logger.error("Error response ({}): {}", response.code(), body);
                throw new ResponseException("Unexpected code " + response.code() + ": " + body, response.code());
            }
            return new RestResponse(response.code(), body);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new ResponseException("Network error canceling order: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // small JSON helpers
    // ------------------------------------------------------------------

    protected static String optString(JsonObject obj, String key, String dflt) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : dflt;
    }

    protected static BigDecimal bd(JsonObject obj, String key, BigDecimal dflt) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return dflt;
        }
        try {
            return obj.get(key).getAsBigDecimal();
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    // Convenience used by callers translating order status strings.
    public static ExtendedOrderStatus parseStatus(String value) {
        return ExtendedOrderStatus.fromString(value);
    }
}
