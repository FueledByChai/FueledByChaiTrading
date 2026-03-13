package com.fueledbychai.binancefutures.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.http.BaseRestApi;
import com.fueledbychai.websocket.ProxyConfig;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * REST client for Binance futures and options public market-data endpoints.
 */
public class BinanceFuturesRestApi extends BaseRestApi implements IBinanceFuturesRestApi {

    static final int DEFAULT_FUNDING_PERIOD_HOURS = 8;
    static final int NO_FUNDING_PERIOD_HOURS = 0;

    protected final String futuresBaseUrl;
    protected final String optionsBaseUrl;
    protected final String baseUrl;
    protected final String accountAddress;
    protected final String privateKey;
    protected final boolean publicApiOnly;
    protected final OkHttpClient client;
    protected final ObjectMapper objectMapper;

    public BinanceFuturesRestApi(String baseUrl) {
        this(baseUrl, baseUrl, null, null);
    }

    public BinanceFuturesRestApi(String baseUrl, String accountAddress, String privateKey) {
        this(baseUrl, baseUrl, accountAddress, privateKey);
    }

    public BinanceFuturesRestApi(String futuresBaseUrl, String optionsBaseUrl) {
        this(futuresBaseUrl, optionsBaseUrl, null, null);
    }

    public BinanceFuturesRestApi(String futuresBaseUrl, String optionsBaseUrl, String accountAddress, String privateKey) {
        if (futuresBaseUrl == null || futuresBaseUrl.isBlank()) {
            throw new IllegalArgumentException("futuresBaseUrl is required");
        }
        if (optionsBaseUrl == null || optionsBaseUrl.isBlank()) {
            throw new IllegalArgumentException("optionsBaseUrl is required");
        }
        this.futuresBaseUrl = futuresBaseUrl;
        this.optionsBaseUrl = optionsBaseUrl;
        this.baseUrl = futuresBaseUrl;
        this.accountAddress = accountAddress;
        this.privateKey = privateKey;
        this.publicApiOnly = accountAddress == null || privateKey == null || accountAddress.isBlank()
                || privateKey.isBlank();
        this.client = createHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    protected OkHttpClient createHttpClient() {
        Proxy proxy = ProxyConfig.getInstance().getProxy();
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (proxy != null && proxy != Proxy.NO_PROXY) {
            builder.proxy(proxy);
        }
        return builder.build();
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        if (!supportsInstrumentType(instrumentType)) {
            throw new IllegalArgumentException("Only perpetual futures and options are supported");
        }
        try {
            JsonNode response = getJson(instrumentType, getExchangeInfoPath(instrumentType));
            List<InstrumentDescriptor> descriptors = parseInstrumentDescriptors(instrumentType, response);
            return descriptors.toArray(new InstrumentDescriptor[0]);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load Binance instrument metadata for " + instrumentType, e);
        }
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        String normalizedSymbol = symbol.trim();
        for (InstrumentType instrumentType : searchOrder(normalizedSymbol)) {
            InstrumentDescriptor descriptor = findInstrumentDescriptor(instrumentType, normalizedSymbol);
            if (descriptor != null) {
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
        return getJson(baseUrl, path);
    }

    protected JsonNode getJson(InstrumentType instrumentType, String path) throws IOException {
        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            return getJson(path);
        }
        return getJson(optionsBaseUrl, path);
    }

    protected JsonNode getJson(String requestBaseUrl, String path) throws IOException {
        HttpUrl url = HttpUrl.parse(requestBaseUrl + path);
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

    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES || instrumentType == InstrumentType.OPTION;
    }

    protected String getExchangeInfoPath(InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            return "/fapi/v1/exchangeInfo";
        }
        if (instrumentType == InstrumentType.OPTION) {
            return "/eapi/v1/exchangeInfo";
        }
        throw new IllegalArgumentException("Unsupported instrument type: " + instrumentType);
    }

    protected InstrumentType[] searchOrder(String symbol) {
        if (symbol != null && symbol.contains("-")) {
            return new InstrumentType[] { InstrumentType.OPTION, InstrumentType.PERPETUAL_FUTURES };
        }
        return new InstrumentType[] { InstrumentType.PERPETUAL_FUTURES, InstrumentType.OPTION };
    }

    protected InstrumentDescriptor findInstrumentDescriptor(InstrumentType instrumentType, String symbol) {
        if (!supportsInstrumentType(instrumentType)) {
            return null;
        }
        InstrumentDescriptor[] descriptors = getAllInstrumentsForType(instrumentType);
        for (InstrumentDescriptor descriptor : descriptors) {
            if (descriptor.getExchangeSymbol().equalsIgnoreCase(symbol)) {
                return descriptor;
            }
        }
        return null;
    }

    List<InstrumentDescriptor> parseInstrumentDescriptors(InstrumentType instrumentType, JsonNode root) {
        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            return parsePerpetualInstrumentDescriptors(root);
        }
        if (instrumentType == InstrumentType.OPTION) {
            return parseOptionInstrumentDescriptors(root);
        }
        return new ArrayList<>();
    }

    protected List<InstrumentDescriptor> parsePerpetualInstrumentDescriptors(JsonNode root) {
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

    protected List<InstrumentDescriptor> parseOptionInstrumentDescriptors(JsonNode root) {
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        if (root == null || !root.has("optionSymbols")) {
            return descriptors;
        }
        for (JsonNode symbolNode : root.path("optionSymbols")) {
            if (!isTradableOption(symbolNode)) {
                continue;
            }
            InstrumentDescriptor descriptor = toOptionInstrumentDescriptor(symbolNode);
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

    protected boolean isTradableOption(JsonNode symbolNode) {
        if (symbolNode == null) {
            return false;
        }
        String status = symbolNode.path("status").asText("");
        return status.isBlank() || "TRADING".equalsIgnoreCase(status);
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

    protected InstrumentDescriptor toOptionInstrumentDescriptor(JsonNode symbolNode) {
        String exchangeSymbol = symbolNode.path("symbol").asText("");
        String quoteAsset = firstNonBlank(symbolNode.path("quoteAsset").asText(""),
                symbolNode.path("settleAsset").asText(""));
        String baseAsset = normalizeOptionBaseAsset(
                firstNonBlank(symbolNode.path("underlying").asText(""), symbolNode.path("baseAsset").asText("")),
                quoteAsset, exchangeSymbol);
        if (exchangeSymbol.isBlank() || baseAsset.isBlank() || quoteAsset.isBlank()) {
            return null;
        }

        JsonNode priceFilter = findFilter(symbolNode, "PRICE_FILTER");
        JsonNode lotSizeFilter = findFilter(symbolNode, "LOT_SIZE");
        JsonNode notionalFilter = firstNonNull(findFilter(symbolNode, "MIN_NOTIONAL"), findFilter(symbolNode, "NOTIONAL"));

        BigDecimal priceTickSize = firstNonDefault(decimalOrDefault(priceFilter, "tickSize", null),
                scaleToIncrement(symbolNode, "priceScale"), BigDecimal.ONE);
        BigDecimal orderSizeIncrement = firstNonDefault(decimalOrDefault(lotSizeFilter, "stepSize", null),
                firstNonDefault(decimalOrDefault(symbolNode, "qtyStep", null), scaleToIncrement(symbolNode, "quantityScale"),
                        null),
                BigDecimal.ONE);
        BigDecimal minOrderSize = firstNonDefault(decimalOrDefault(lotSizeFilter, "minQty", null),
                decimalOrDefault(symbolNode, "minQty", null), BigDecimal.ONE);
        int minNotionalOrderSize = toMinNotional(notionalFilter);
        BigDecimal contractMultiplier = decimalOrDefault(symbolNode, "unit", BigDecimal.ONE);

        return new InstrumentDescriptor(InstrumentType.OPTION, Exchange.BINANCE_FUTURES, exchangeSymbol, exchangeSymbol,
                baseAsset, quoteAsset, orderSizeIncrement, priceTickSize, minNotionalOrderSize, minOrderSize,
                NO_FUNDING_PERIOD_HOURS, contractMultiplier, 1, exchangeSymbol);
    }

    protected String normalizeOptionBaseAsset(String rawBaseAsset, String quoteAsset, String exchangeSymbol) {
        String normalized = rawBaseAsset == null ? "" : rawBaseAsset.trim();
        if (!normalized.isBlank()) {
            int dashIndex = normalized.indexOf('-');
            if (dashIndex > 0) {
                normalized = normalized.substring(0, dashIndex);
            }
            if (quoteAsset != null && !quoteAsset.isBlank() && normalized.length() > quoteAsset.length()
                    && normalized.regionMatches(true, normalized.length() - quoteAsset.length(), quoteAsset.trim(), 0,
                            quoteAsset.length())) {
                normalized = normalized.substring(0, normalized.length() - quoteAsset.length());
            }
            return normalized;
        }
        if (exchangeSymbol == null) {
            return "";
        }
        int dashIndex = exchangeSymbol.indexOf('-');
        if (dashIndex > 0) {
            return exchangeSymbol.substring(0, dashIndex);
        }
        return exchangeSymbol.trim();
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

    protected String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right == null ? "" : right;
    }

    protected BigDecimal firstNonDefault(BigDecimal left, BigDecimal right, BigDecimal defaultValue) {
        if (left != null) {
            return left;
        }
        if (right != null) {
            return right;
        }
        return defaultValue;
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

    protected BigDecimal scaleToIncrement(JsonNode node, String field) {
        if (node == null || field == null || !node.has(field)) {
            return null;
        }
        int scale = node.path(field).asInt(-1);
        if (scale < 0) {
            return null;
        }
        return BigDecimal.ONE.scaleByPowerOfTen(-scale).stripTrailingZeros();
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
