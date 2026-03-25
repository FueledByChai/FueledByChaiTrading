package com.fueledbychai.bybit.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.http.OkHttpClientFactory;
import com.fueledbychai.websocket.ProxyConfig;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Public REST implementation for Bybit market metadata.
 */
public class BybitRestApi implements IBybitRestApi {

    protected static final Logger logger = LoggerFactory.getLogger(BybitRestApi.class);
    protected static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);
    protected static final int MAX_ERROR_BODY_CHARS = 280;

    protected static final List<InstrumentType> SUPPORTED_TYPES = List.of(
            InstrumentType.CRYPTO_SPOT, InstrumentType.PERPETUAL_FUTURES, InstrumentType.FUTURES,
            InstrumentType.OPTION);

    protected static final DateTimeFormatter COMMON_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    protected static final DateTimeFormatter BYBIT_CONTRACT_DATE = DateTimeFormatter.ofPattern("ddMMMyy", Locale.US);
    protected static final String OPTION_BASE_COINS_PROPERTY = "bybit.option.base.coins";
    protected static final List<String> DEFAULT_OPTION_BASE_COINS = List.of("BTC", "ETH");

    protected static final List<String> COMMON_QUOTE_SUFFIXES = List.of("USDT", "USDC", "USD", "BTC", "ETH", "EUR",
            "JPY", "TRY");

    protected final String baseUrl;
    protected final String apiKey;
    protected final String apiSecret;
    protected final boolean publicApiOnly;
    protected final OkHttpClient httpClient;

    protected final Map<InstrumentType, InstrumentDescriptor[]> descriptorsByType = new EnumMap<>(InstrumentType.class);
    protected final Map<String, InstrumentDescriptor> descriptorByExchangeSymbol = new ConcurrentHashMap<>();
    protected final Map<String, InstrumentDescriptor> descriptorByCommonSymbol = new ConcurrentHashMap<>();
    protected final Map<String, InstrumentDescriptor[]> optionDescriptorsByBaseCoin = new ConcurrentHashMap<>();

    public BybitRestApi(String baseUrl) {
        this(baseUrl, null, null);
    }

    public BybitRestApi(String baseUrl, String apiKey, String apiSecret) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.publicApiOnly = apiKey == null || apiSecret == null || apiKey.isBlank() || apiSecret.isBlank();
        this.httpClient = createHttpClient();
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        validateSupportedType(instrumentType);
        synchronized (descriptorsByType) {
            InstrumentDescriptor[] cached = descriptorsByType.get(instrumentType);
            if (cached != null) {
                return cached;
            }

            List<InstrumentDescriptor> loaded = loadInstrumentsForType(instrumentType);
            InstrumentDescriptor[] resolved = loaded.toArray(new InstrumentDescriptor[0]);
            descriptorsByType.put(instrumentType, resolved);
            return resolved;
        }
    }

    @Override
    public InstrumentDescriptor[] getOptionInstrumentsForBaseCoin(String baseCoin) {
        String normalizedBaseCoin = normalizeBaseCoin(baseCoin);

        synchronized (descriptorsByType) {
            InstrumentDescriptor[] cachedByBaseCoin = optionDescriptorsByBaseCoin.get(normalizedBaseCoin);
            if (cachedByBaseCoin != null) {
                return cachedByBaseCoin;
            }

            InstrumentDescriptor[] cachedAllOptions = descriptorsByType.get(InstrumentType.OPTION);
            if (cachedAllOptions != null) {
                List<InstrumentDescriptor> filtered = filterOptionsByBaseCoin(cachedAllOptions, normalizedBaseCoin);
                if (!filtered.isEmpty()) {
                    InstrumentDescriptor[] resolved = filtered.toArray(new InstrumentDescriptor[0]);
                    optionDescriptorsByBaseCoin.put(normalizedBaseCoin, resolved);
                    return resolved;
                }
            }
        }

        List<InstrumentDescriptor> loaded = loadByCategory("option", InstrumentType.OPTION, normalizedBaseCoin);
        InstrumentDescriptor[] resolved = loaded.toArray(new InstrumentDescriptor[0]);

        synchronized (descriptorsByType) {
            optionDescriptorsByBaseCoin.put(normalizedBaseCoin, resolved);

            InstrumentDescriptor[] cachedAllOptions = descriptorsByType.get(InstrumentType.OPTION);
            InstrumentDescriptor[] merged = mergeDescriptors(cachedAllOptions, resolved);
            descriptorsByType.put(InstrumentType.OPTION, merged);
        }

        return resolved;
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

        InstrumentType inferredType = inferInstrumentType(normalized);
        if (inferredType != null) {
            InstrumentDescriptor descriptor = searchByType(inferredType, normalized);
            if (descriptor != null) {
                return descriptor;
            }
        }

        for (InstrumentType instrumentType : SUPPORTED_TYPES) {
            InstrumentDescriptor descriptor = searchByType(instrumentType, normalized);
            if (descriptor != null) {
                return descriptor;
            }
        }

        return null;
    }

    @Override
    public JsonObject getTicker(String category, String symbol) {
        if (!isPresent(category)) {
            throw new IllegalArgumentException("category is required");
        }
        if (!isPresent(symbol)) {
            throw new IllegalArgumentException("symbol is required");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("category", category.trim());
        params.put("symbol", symbol.trim());
        return executeGet("market/tickers", params);
    }

    @Override
    public boolean isPublicApiOnly() {
        return publicApiOnly;
    }

    protected InstrumentDescriptor searchByType(InstrumentType instrumentType, String normalizedSymbol) {
        for (InstrumentDescriptor descriptor : getAllInstrumentsForType(instrumentType)) {
            if (descriptor == null) {
                continue;
            }
            if (normalizedSymbol.equalsIgnoreCase(descriptor.getExchangeSymbol())
                    || normalizedSymbol.equalsIgnoreCase(descriptor.getCommonSymbol())) {
                return descriptor;
            }
        }
        return null;
    }

    protected OkHttpClient createHttpClient() {
        return OkHttpClientFactory.create(REQUEST_TIMEOUT);
    }

    protected Proxy resolveProxy() {
        return ProxyConfig.getInstance().getProxy();
    }

    protected String normalizeBaseUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    protected void validateSupportedType(InstrumentType instrumentType) {
        if (!SUPPORTED_TYPES.contains(instrumentType)) {
            throw new IllegalArgumentException("Unsupported Bybit instrument type: " + instrumentType);
        }
    }

    protected List<InstrumentDescriptor> loadInstrumentsForType(InstrumentType instrumentType) {
        return switch (instrumentType) {
            case CRYPTO_SPOT -> loadByCategory("spot", instrumentType);
            case OPTION -> loadOptionsByBaseCoin(instrumentType);
            case PERPETUAL_FUTURES, FUTURES -> loadLinearAndInverse(instrumentType);
            default -> throw new IllegalArgumentException("Unsupported Bybit instrument type: " + instrumentType);
        };
    }

    protected List<InstrumentDescriptor> loadLinearAndInverse(InstrumentType instrumentType) {
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        descriptors.addAll(loadByCategory("linear", instrumentType));
        descriptors.addAll(loadByCategory("inverse", instrumentType));
        return descriptors;
    }

    protected List<InstrumentDescriptor> loadOptionsByBaseCoin(InstrumentType instrumentType) {
        LinkedHashMap<String, InstrumentDescriptor> merged = new LinkedHashMap<>();
        RuntimeException lastFailure = null;

        for (String baseCoin : resolveOptionBaseCoins()) {
            try {
                List<InstrumentDescriptor> descriptors = loadByCategory("option", instrumentType, baseCoin);
                optionDescriptorsByBaseCoin.put(baseCoin.toUpperCase(Locale.US),
                        descriptors.toArray(new InstrumentDescriptor[0]));
                for (InstrumentDescriptor descriptor : descriptors) {
                    merged.put(descriptor.getExchangeSymbol(), descriptor);
                }
            } catch (RuntimeException e) {
                lastFailure = e;
                logger.warn("Unable to load Bybit option instruments for baseCoin={}", baseCoin, e);
            }
        }

        if (!merged.isEmpty()) {
            return new ArrayList<>(merged.values());
        }

        try {
            return loadByCategory("option", instrumentType);
        } catch (RuntimeException fallbackFailure) {
            if (lastFailure != null) {
                fallbackFailure.addSuppressed(lastFailure);
            }
            throw fallbackFailure;
        }
    }

    protected List<String> resolveOptionBaseCoins() {
        Set<String> baseCoins = new LinkedHashSet<>();

        String configured = System.getProperty(OPTION_BASE_COINS_PROPERTY);
        if (isPresent(configured)) {
            Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(value -> value.toUpperCase(Locale.US))
                    .forEach(baseCoins::add);
        }

        if (baseCoins.isEmpty()) {
            baseCoins.addAll(DEFAULT_OPTION_BASE_COINS);
        }

        return new ArrayList<>(baseCoins);
    }

    protected String normalizeBaseCoin(String baseCoin) {
        if (!isPresent(baseCoin)) {
            throw new IllegalArgumentException("baseCoin is required");
        }
        return baseCoin.trim().toUpperCase(Locale.US);
    }

    protected List<InstrumentDescriptor> filterOptionsByBaseCoin(InstrumentDescriptor[] descriptors, String baseCoin) {
        List<InstrumentDescriptor> filtered = new ArrayList<>();
        if (descriptors == null || descriptors.length == 0 || !isPresent(baseCoin)) {
            return filtered;
        }

        for (InstrumentDescriptor descriptor : descriptors) {
            if (descriptor == null || !InstrumentType.OPTION.equals(descriptor.getInstrumentType())) {
                continue;
            }
            if (!baseCoin.equalsIgnoreCase(descriptor.getBaseCurrency())) {
                continue;
            }
            filtered.add(descriptor);
        }
        return filtered;
    }

    protected InstrumentDescriptor[] mergeDescriptors(InstrumentDescriptor[] current, InstrumentDescriptor[] additions) {
        LinkedHashMap<String, InstrumentDescriptor> merged = new LinkedHashMap<>();
        if (current != null) {
            for (InstrumentDescriptor descriptor : current) {
                if (descriptor != null && isPresent(descriptor.getExchangeSymbol())) {
                    merged.put(descriptor.getExchangeSymbol(), descriptor);
                }
            }
        }

        if (additions != null) {
            for (InstrumentDescriptor descriptor : additions) {
                if (descriptor != null && isPresent(descriptor.getExchangeSymbol())) {
                    merged.put(descriptor.getExchangeSymbol(), descriptor);
                }
            }
        }

        return merged.values().toArray(new InstrumentDescriptor[0]);
    }

    protected List<InstrumentDescriptor> loadByCategory(String category, InstrumentType requestedType) {
        return loadByCategory(category, requestedType, null);
    }

    protected List<InstrumentDescriptor> loadByCategory(String category, InstrumentType requestedType, String baseCoin) {
        List<JsonObject> rows = loadInstrumentRows(category, baseCoin);
        List<InstrumentDescriptor> descriptors = new ArrayList<>();

        for (JsonObject row : rows) {
            InstrumentDescriptor descriptor = toInstrumentDescriptor(category, row);
            if (descriptor == null || descriptor.getInstrumentType() != requestedType) {
                continue;
            }
            descriptors.add(descriptor);
            cacheDescriptor(descriptor);
        }

        return descriptors;
    }

    protected List<JsonObject> loadInstrumentRows(String category) {
        return loadInstrumentRows(category, null);
    }

    protected List<JsonObject> loadInstrumentRows(String category, String baseCoin) {
        List<JsonObject> rows = new ArrayList<>();
        String cursor = null;

        while (true) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("category", category);
            if (!"spot".equalsIgnoreCase(category)) {
                params.put("limit", "1000");
            }
            if ("option".equalsIgnoreCase(category) && isPresent(baseCoin)) {
                params.put("baseCoin", baseCoin.trim().toUpperCase(Locale.US));
            }
            if (isPresent(cursor)) {
                params.put("cursor", cursor);
            }

            JsonObject payload = executeGet("market/instruments-info", params);
            JsonObject result = getJsonObject(payload, "result");
            JsonArray list = result == null ? null : getJsonArray(result, "list");
            if (list != null) {
                for (JsonElement element : list) {
                    if (element.isJsonObject()) {
                        rows.add(element.getAsJsonObject());
                    }
                }
            }

            if ("spot".equalsIgnoreCase(category)) {
                break;
            }

            cursor = result == null ? null : getString(result, "nextPageCursor");
            if (!isPresent(cursor) || list == null || list.isEmpty()) {
                break;
            }
        }

        return rows;
    }

    protected InstrumentDescriptor toInstrumentDescriptor(String category, JsonObject row) {
        if (row == null) {
            return null;
        }

        String symbol = getString(row, "symbol");
        if (!isPresent(symbol)) {
            return null;
        }

        InstrumentType instrumentType = resolveInstrumentType(category, row);
        if (instrumentType == null) {
            return null;
        }

        String baseCurrency = resolveBaseCurrency(row, symbol);
        String quoteCurrency = resolveQuoteCurrency(row, category, symbol);

        JsonObject priceFilter = getJsonObject(row, "priceFilter");
        JsonObject lotSizeFilter = getJsonObject(row, "lotSizeFilter");

        BigDecimal tickSize = sanitizePositive(getBigDecimal(priceFilter, "tickSize"), new BigDecimal("0.0001"));
        BigDecimal qtyStep = sanitizePositive(firstNonNull(getBigDecimal(lotSizeFilter, "qtyStep"),
                getBigDecimal(lotSizeFilter, "basePrecision"), getBigDecimal(row, "basePrecision")), BigDecimal.ONE);
        BigDecimal minQty = sanitizePositive(firstNonNull(getBigDecimal(lotSizeFilter, "minOrderQty"),
                getBigDecimal(lotSizeFilter, "minOrderAmt"), getBigDecimal(lotSizeFilter, "minOrderSize")), qtyStep);

        int minNotional = toPositiveInt(getBigDecimal(lotSizeFilter, "minNotionalValue"), 1);
        BigDecimal contractMultiplier = BigDecimal.ONE;
        int fundingPeriodHours = instrumentType == InstrumentType.PERPETUAL_FUTURES ? 8 : 0;

        String commonSymbol = toCommonSymbol(instrumentType, row, baseCurrency, quoteCurrency, symbol);

        return new InstrumentDescriptor(instrumentType, Exchange.BYBIT, commonSymbol, symbol.toUpperCase(Locale.US),
                baseCurrency, quoteCurrency, qtyStep.stripTrailingZeros(), tickSize.stripTrailingZeros(), minNotional,
                minQty.stripTrailingZeros(), fundingPeriodHours, contractMultiplier, 1,
                symbol.toUpperCase(Locale.US));
    }

    protected InstrumentType resolveInstrumentType(String category, JsonObject row) {
        if ("spot".equalsIgnoreCase(category)) {
            return InstrumentType.CRYPTO_SPOT;
        }
        if ("option".equalsIgnoreCase(category)) {
            return InstrumentType.OPTION;
        }

        String contractType = getString(row, "contractType");
        if (isPresent(contractType)) {
            String normalized = contractType.trim().toUpperCase(Locale.US);
            if (normalized.contains("PERPETUAL")) {
                return InstrumentType.PERPETUAL_FUTURES;
            }
            if (normalized.contains("FUTURE")) {
                return InstrumentType.FUTURES;
            }
        }

        String symbol = getString(row, "symbol");
        if (isPresent(symbol) && symbol.contains("-")) {
            return InstrumentType.FUTURES;
        }
        return InstrumentType.PERPETUAL_FUTURES;
    }

    protected String resolveBaseCurrency(JsonObject row, String symbol) {
        String base = getString(row, "baseCoin");
        if (isPresent(base)) {
            return base.trim().toUpperCase(Locale.US);
        }

        if (!isPresent(symbol)) {
            return "UNKNOWN";
        }

        String normalized = symbol.trim().toUpperCase(Locale.US);
        if (normalized.contains("-")) {
            return normalized.split("-")[0];
        }

        String quote = detectQuoteSuffix(normalized);
        if (quote == null || quote.length() >= normalized.length()) {
            return normalized;
        }
        return normalized.substring(0, normalized.length() - quote.length());
    }

    protected String resolveQuoteCurrency(JsonObject row, String category, String symbol) {
        String quote = getString(row, "quoteCoin");
        if (isPresent(quote)) {
            return quote.trim().toUpperCase(Locale.US);
        }

        quote = getString(row, "settleCoin");
        if (isPresent(quote)) {
            return quote.trim().toUpperCase(Locale.US);
        }

        String normalized = symbol == null ? null : symbol.trim().toUpperCase(Locale.US);
        if (isPresent(normalized) && !normalized.contains("-")) {
            String suffix = detectQuoteSuffix(normalized);
            if (suffix != null) {
                return suffix;
            }
        }

        if ("option".equalsIgnoreCase(category)) {
            return "USD";
        }
        return "USDT";
    }

    protected String detectQuoteSuffix(String symbol) {
        if (!isPresent(symbol)) {
            return null;
        }
        for (String suffix : COMMON_QUOTE_SUFFIXES) {
            if (symbol.endsWith(suffix) && symbol.length() > suffix.length()) {
                return suffix;
            }
        }
        return null;
    }

    protected String toCommonSymbol(InstrumentType instrumentType, JsonObject row, String baseCurrency,
            String quoteCurrency, String symbol) {
        if (instrumentType == InstrumentType.CRYPTO_SPOT) {
            return baseCurrency + "/" + quoteCurrency;
        }

        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            return baseCurrency + "/" + quoteCurrency + "-PERP";
        }

        if (instrumentType == InstrumentType.FUTURES) {
            LocalDate expiry = resolveExpiry(row, symbol);
            if (expiry == null) {
                return symbol;
            }
            return baseCurrency + "/" + quoteCurrency + "-" + expiry.format(COMMON_DATE);
        }

        if (instrumentType == InstrumentType.OPTION) {
            LocalDate expiry = resolveExpiry(row, symbol);
            String strike = getString(row, "strike");
            String right = normalizeOptionRight(getString(row, "optionsType"));
            if (expiry == null || !isPresent(strike) || !isPresent(right)) {
                String[] parts = symbol == null ? new String[0] : symbol.toUpperCase(Locale.US).split("-");
                if (parts.length >= 4) {
                    strike = parts[2];
                    right = normalizeOptionRight(parts[3]);
                }
            }
            if (expiry == null || !isPresent(strike) || !isPresent(right)) {
                return symbol;
            }
            return baseCurrency + "/" + quoteCurrency + "-" + expiry.format(COMMON_DATE) + "-" + strike + "-"
                    + right;
        }

        return symbol;
    }

    protected LocalDate resolveExpiry(JsonObject row, String symbol) {
        Long deliveryTime = getLong(row, "deliveryTime");
        if (deliveryTime != null && deliveryTime.longValue() > 0L) {
            try {
                return java.time.Instant.ofEpochMilli(deliveryTime.longValue()).atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate();
            } catch (RuntimeException ignored) {
                // Fallback to symbol parsing below.
            }
        }

        if (!isPresent(symbol)) {
            return null;
        }

        String[] parts = symbol.trim().toUpperCase(Locale.US).split("-");
        if (parts.length < 2) {
            return null;
        }

        String datePart = parts[1];
        if (!datePart.matches("\\d{2}[A-Z]{3}\\d{2}")) {
            return null;
        }

        try {
            return LocalDate.parse(datePart, BYBIT_CONTRACT_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    protected String normalizeOptionRight(String optionRight) {
        if (!isPresent(optionRight)) {
            return null;
        }
        String normalized = optionRight.trim().toUpperCase(Locale.US);
        if ("CALL".equals(normalized)) {
            return "C";
        }
        if ("PUT".equals(normalized)) {
            return "P";
        }
        if ("C".equals(normalized) || "P".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    protected InstrumentType inferInstrumentType(String symbol) {
        if (!isPresent(symbol)) {
            return null;
        }

        String normalized = symbol.trim().toUpperCase(Locale.US);
        if (normalized.matches("^[A-Z0-9]+-\\d{2}[A-Z]{3}\\d{2}-[0-9.]+-[CP]$")
                || normalized.matches("^[A-Z0-9]+/[A-Z0-9]+-\\d{8}-[0-9.]+-[CP]$")) {
            return InstrumentType.OPTION;
        }
        if (normalized.matches("^[A-Z0-9]+-\\d{2}[A-Z]{3}\\d{2}$")
                || normalized.matches("^[A-Z0-9]+/[A-Z0-9]+-\\d{8}$")) {
            return InstrumentType.FUTURES;
        }
        if (normalized.endsWith("-PERP")) {
            return InstrumentType.PERPETUAL_FUTURES;
        }
        if (normalized.contains("/") || normalized.matches("^[A-Z0-9]+$")) {
            return InstrumentType.CRYPTO_SPOT;
        }
        return null;
    }

    protected void cacheDescriptor(InstrumentDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }

        if (isPresent(descriptor.getExchangeSymbol())) {
            descriptorByExchangeSymbol.putIfAbsent(descriptor.getExchangeSymbol().toUpperCase(Locale.US), descriptor);
        }
        if (isPresent(descriptor.getCommonSymbol())) {
            descriptorByCommonSymbol.putIfAbsent(descriptor.getCommonSymbol().toUpperCase(Locale.US), descriptor);
        }
    }

    protected JsonObject executeGet(String methodPath, Map<String, String> params) {
        URI uri = buildUri(methodPath, params);
        Request request = new Request.Builder().url(uri.toString()).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? null : response.body().string();
            if (response.code() < 200 || response.code() >= 300) {
                throw new IllegalStateException("Bybit returned HTTP " + response.code() + " for " + methodPath
                        + " body=" + summarizeBody(responseBody));
            }

            JsonObject payload = JsonParser.parseString(responseBody).getAsJsonObject();
            String retCode = getString(payload, "retCode");
            if (retCode != null && !"0".equals(retCode)) {
                throw new IllegalStateException("Bybit API error for " + methodPath + " retCode=" + retCode
                        + " retMsg=" + getString(payload, "retMsg"));
            }
            return payload;
        } catch (IOException e) {
            throw new IllegalStateException("I/O failure calling Bybit " + methodPath, e);
        }
    }

    protected URI buildUri(String methodPath, Map<String, String> params) {
        StringBuilder builder = new StringBuilder(baseUrl);
        if (!methodPath.startsWith("/")) {
            builder.append('/');
        }
        builder.append(methodPath);

        if (params != null && !params.isEmpty()) {
            builder.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                if (!first) {
                    builder.append('&');
                }
                first = false;
                builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                builder.append('=');
                builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }

        return URI.create(builder.toString());
    }

    protected String summarizeBody(String body) {
        if (body == null) {
            return "<empty>";
        }
        String compact = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.isBlank()) {
            return "<blank>";
        }
        if (compact.length() <= MAX_ERROR_BODY_CHARS) {
            return compact;
        }
        return compact.substring(0, MAX_ERROR_BODY_CHARS) + "...";
    }

    protected JsonObject getJsonObject(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(key);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    protected JsonArray getJsonArray(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(key);
        return element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    protected String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    protected Long getLong(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        if (!isPresent(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected BigDecimal getBigDecimal(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        if (!isPresent(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected BigDecimal sanitizePositive(BigDecimal value, BigDecimal fallback) {
        if (value == null || value.signum() <= 0) {
            return fallback;
        }
        return value;
    }

    protected int toPositiveInt(BigDecimal value, int fallback) {
        if (value == null || value.signum() <= 0) {
            return fallback;
        }
        try {
            return value.setScale(0, RoundingMode.CEILING).intValueExact();
        } catch (ArithmeticException e) {
            return value.setScale(0, RoundingMode.CEILING).intValue();
        }
    }

    protected BigDecimal firstNonNull(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
