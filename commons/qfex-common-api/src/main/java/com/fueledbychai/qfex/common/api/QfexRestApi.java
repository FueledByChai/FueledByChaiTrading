package com.fueledbychai.qfex.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class QfexRestApi implements IQfexRestApi {

    private static final Logger logger = LoggerFactory.getLogger(QfexRestApi.class);
    private static final Duration CONTRACT_CACHE_TTL = Duration.ofSeconds(60);
    static final ObjectMapper MAPPER = new ObjectMapper();

    protected final String baseUrl;
    protected final QfexHmacSigner signer;
    protected final String accountId;
    protected final OkHttpClient client;

    private volatile List<QfexContract> contracts;
    private volatile long contractsLoadedAt;
    private final Map<String, QfexContract> contractsBySymbol = new ConcurrentHashMap<>();

    public QfexRestApi(String baseUrl) {
        this(baseUrl, null, null);
    }

    public QfexRestApi(String baseUrl, QfexHmacSigner signer, String accountId) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signer = signer;
        this.accountId = accountId;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public boolean isPublicApiOnly() {
        return signer == null;
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES) {
            return new InstrumentDescriptor[0];
        }
        return getContracts().stream()
                .filter(c -> !"DELISTED".equalsIgnoreCase(c.status()))
                .map(QfexRestApi::toDescriptor)
                .toArray(InstrumentDescriptor[]::new);
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        QfexContract c = getContract(symbol);
        return c == null ? null : toDescriptor(c);
    }

    @Override
    public List<QfexContract> getContracts() {
        List<QfexContract> cached = contracts;
        if (cached != null && System.currentTimeMillis() - contractsLoadedAt < CONTRACT_CACHE_TTL.toMillis()) {
            return cached;
        }
        synchronized (this) {
            if (contracts != null && System.currentTimeMillis() - contractsLoadedAt < CONTRACT_CACHE_TTL.toMillis()) {
                return contracts;
            }
            List<QfexContract> loaded = parseRefdata(publicGet(HttpUrl.get(baseUrl + "/refdata")));
            contractsBySymbol.clear();
            loaded.forEach(c -> contractsBySymbol.put(c.symbol(), c));
            contracts = loaded;
            contractsLoadedAt = System.currentTimeMillis();
            return loaded;
        }
    }

    @Override
    public QfexContract getContract(String symbol) {
        if (symbol == null) {
            return null;
        }
        getContracts();
        return contractsBySymbol.get(symbol);
    }

    @Override
    public JsonNode getCandles(String symbol, String resolution, Instant from, Instant to) {
        HttpUrl url = HttpUrl.get(baseUrl + "/candles/" + symbol).newBuilder()
                .addQueryParameter("resolution", resolution)
                .addQueryParameter("fromISO", from.toString())
                .addQueryParameter("toISO", to.toString())
                .build();
        JsonNode root = publicGet(url);
        return sortCandlesOldestFirst(root);
    }

    @Override
    public JsonNode getOrderBook(String symbol) {
        return publicGet(HttpUrl.get(baseUrl + "/md/orderbook/" + symbol));
    }

    @Override
    public JsonNode getPositions() {
        return privateGet(HttpUrl.get(baseUrl + "/user/positions"));
    }

    static List<QfexContract> parseRefdata(JsonNode root) {
        JsonNode arr = root;
        if (root != null && root.isObject()) {
            for (String key : new String[] { "refdata", "data", "contracts" }) {
                if (root.path(key).isArray()) {
                    arr = root.path(key);
                    break;
                }
            }
        }
        List<QfexContract> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                if (n.hasNonNull("symbol")) {
                    out.add(QfexContract.fromJson(n));
                }
            }
        }
        return out;
    }

    /** QFEX returns candles newest first; callers expect chronological order. */
    static JsonNode sortCandlesOldestFirst(JsonNode root) {
        if (root == null || !root.path("candles").isArray()) {
            return root;
        }
        List<JsonNode> list = new ArrayList<>();
        root.path("candles").forEach(list::add);
        list.sort(Comparator.comparing(n -> n.path("startedAt").asText("")));
        ObjectNode out = MAPPER.createObjectNode();
        ArrayNode arr = out.putArray("candles");
        list.forEach(arr::add);
        return out;
    }

    static InstrumentDescriptor toDescriptor(QfexContract c) {
        BigDecimal minSize = c.minQuantity() != null ? c.minQuantity() : c.lotSize();
        return new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES, Exchange.QFEX,
                c.baseAsset() != null ? c.baseAsset() : c.symbol(), c.symbol(), c.baseAsset(), c.quoteAsset(),
                c.lotSize(), c.tickSize(), 0, minSize, 1, BigDecimal.ONE, c.maxLeverage(), c.symbol());
    }

    protected JsonNode publicGet(HttpUrl url) {
        return execute(new Request.Builder().url(url).header("User-Agent", "fueledbychai-qfex").get().build());
    }

    protected JsonNode privateGet(HttpUrl url) {
        if (signer == null) {
            throw new IllegalStateException("QFEX private endpoint requires API keys: " + url.encodedPath());
        }
        QfexHmacSigner.Credentials c = signer.sign();
        Request.Builder b = new Request.Builder().url(url).get()
                .header("User-Agent", "fueledbychai-qfex")
                .header("x-qfex-public-key", c.publicKey())
                .header("x-qfex-hmac-signature", c.signature())
                .header("x-qfex-nonce", c.nonce())
                .header("x-qfex-timestamp", String.valueOf(c.unixTs()));
        if (accountId != null && !accountId.isBlank()) {
            b.header("x-qfex-requested-account-id", accountId);
        }
        return execute(b.build());
    }

    protected JsonNode execute(Request request) {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("QFEX " + request.url().encodedPath() + " HTTP " + response.code()
                        + ": " + body);
            }
            return MAPPER.readTree(body);
        } catch (IOException e) {
            logger.warn("QFEX request {} failed: {}", request.url().encodedPath(), e.getMessage());
            throw new IllegalStateException("QFEX request failed: " + request.url().encodedPath(), e);
        }
    }
}
