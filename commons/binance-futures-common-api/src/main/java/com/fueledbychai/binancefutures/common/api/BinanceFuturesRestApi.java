package com.fueledbychai.binancefutures.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.http.BaseRestApi;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * REST client for Binance USD(S)-margined futures public market-data endpoints.
 */
public class BinanceFuturesRestApi extends BaseRestApi implements IBinanceFuturesRestApi {

    static final int DEFAULT_FUNDING_PERIOD_HOURS = 8;

    protected final String baseUrl;
    protected final String accountAddress;
    protected final String privateKey;
    protected final boolean publicApiOnly;
    protected final OkHttpClient client;
    protected final ObjectMapper objectMapper;

    public BinanceFuturesRestApi(String baseUrl) {
        this(baseUrl, null, null);
    }

    public BinanceFuturesRestApi(String baseUrl, String accountAddress, String privateKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        this.baseUrl = baseUrl;
        this.accountAddress = accountAddress;
        this.privateKey = privateKey;
        this.publicApiOnly = accountAddress == null || privateKey == null || accountAddress.isBlank()
                || privateKey.isBlank();
        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType != InstrumentType.PERPETUAL_FUTURES) {
            throw new IllegalArgumentException("Only perpetual futures are supported");
        }
        try {
            JsonNode response = getJson("/fapi/v1/exchangeInfo");
            List<InstrumentDescriptor> descriptors = parseInstrumentDescriptors(response);
            return descriptors.toArray(new InstrumentDescriptor[0]);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load Binance futures exchange info", e);
        }
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        InstrumentDescriptor[] descriptors = getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
        for (InstrumentDescriptor descriptor : descriptors) {
            if (descriptor.getExchangeSymbol().equalsIgnoreCase(symbol.trim())) {
                return descriptor;
            }
        }
        return null;
    }

    @Override
    public Date getServerTime() {
        try {
            JsonNode response = getJson("/fapi/v1/time");
            long serverTime = response.path("serverTime").asLong(-1L);
            if (serverTime <= 0L) {
                throw new IllegalStateException("Missing serverTime in Binance futures response");
            }
            return new Date(serverTime);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load Binance futures server time", e);
        }
    }

    @Override
    public boolean isPublicApiOnly() {
        return publicApiOnly;
    }

    protected JsonNode getJson(String path) throws IOException {
        HttpUrl url = HttpUrl.parse(baseUrl + path);
        if (url == null) {
            throw new IllegalArgumentException("Invalid URL for path " + path);
        }
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new IOException("Unexpected Binance Futures response " + response.code() + ": " + body);
            }
            if (response.body() == null) {
                throw new IOException("Empty Binance Futures response");
            }
            return objectMapper.readTree(response.body().string());
        }
    }

    List<InstrumentDescriptor> parseInstrumentDescriptors(JsonNode root) {
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        if (root == null || !root.has("symbols")) {
            return descriptors;
        }
        for (JsonNode symbolNode : root.path("symbols")) {
            if (!isTradablePerpetual(symbolNode)) {
                continue;
            }
            InstrumentDescriptor descriptor = toInstrumentDescriptor(symbolNode);
            if (descriptor != null) {
                descriptors.add(descriptor);
            }
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

        return new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES, Exchange.BINANCE_FUTURES,
                baseAsset + "/" + quoteAsset, exchangeSymbol, baseAsset, quoteAsset, orderSizeIncrement, priceTickSize,
                minNotionalOrderSize, minOrderSize, DEFAULT_FUNDING_PERIOD_HOURS, BigDecimal.ONE, 1, exchangeSymbol);
    }

    protected JsonNode findFilter(JsonNode symbolNode, String filterType) {
        if (symbolNode == null || filterType == null || !symbolNode.has("filters")) {
            return null;
        }
        for (JsonNode filterNode : symbolNode.path("filters")) {
            if (filterType.equalsIgnoreCase(filterNode.path("filterType").asText())) {
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

    protected int toMinNotional(JsonNode filterNode) {
        if (filterNode == null) {
            return 0;
        }
        String raw = filterNode.path("notional").asText(filterNode.path("minNotional").asText(""));
        if (raw.isBlank()) {
            return 0;
        }
        return new BigDecimal(raw).setScale(0, RoundingMode.CEILING).intValue();
    }
}
