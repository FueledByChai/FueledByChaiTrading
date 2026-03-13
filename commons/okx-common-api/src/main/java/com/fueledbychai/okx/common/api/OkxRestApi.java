package com.fueledbychai.okx.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
import com.fueledbychai.okx.common.api.ws.model.OkxFundingRateUpdate;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Public REST implementation for OKX market metadata.
 */
public class OkxRestApi implements IOkxRestApi {

    protected static final Logger logger = LoggerFactory.getLogger(OkxRestApi.class);
    protected static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);
    protected static final int MAX_ERROR_BODY_CHARS = 280;
    protected static final List<InstrumentType> SUPPORTED_TYPES = List.of(
            InstrumentType.CRYPTO_SPOT, InstrumentType.PERPETUAL_FUTURES, InstrumentType.FUTURES,
            InstrumentType.OPTION);
    protected static final DateTimeFormatter COMMON_OPTION_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    protected static final DateTimeFormatter OKX_CONTRACT_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    protected final String baseUrl;
    protected final String accountAddress;
    protected final String privateKey;
    protected final boolean publicApiOnly;
    protected final OkHttpClient httpClient;
    protected final Map<InstrumentType, InstrumentDescriptor[]> descriptorsByType = new EnumMap<>(InstrumentType.class);
    protected final Map<String, InstrumentDescriptor> descriptorBySymbol = new ConcurrentHashMap<>();
    protected final Map<String, InstrumentDescriptor> descriptorByCommonSymbol = new ConcurrentHashMap<>();

    public OkxRestApi(String baseUrl) {
        this(baseUrl, null, null);
    }

    public OkxRestApi(String baseUrl, String accountAddress, String privateKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.accountAddress = accountAddress;
        this.privateKey = privateKey;
        this.publicApiOnly = accountAddress == null || privateKey == null || accountAddress.isBlank()
                || privateKey.isBlank();
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

            List<InstrumentDescriptor> descriptors = loadInstrumentsForType(instrumentType);
            InstrumentDescriptor[] resolved = descriptors.toArray(new InstrumentDescriptor[0]);
            descriptorsByType.put(instrumentType, resolved);
            return resolved;
        }
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }

        String normalized = symbol.trim().toUpperCase(Locale.US);

        InstrumentDescriptor byExchangeSymbol = descriptorBySymbol.get(normalized);
        if (byExchangeSymbol != null) {
            return byExchangeSymbol;
        }

        InstrumentDescriptor byCommonSymbol = descriptorByCommonSymbol.get(normalized);
        if (byCommonSymbol != null) {
            return byCommonSymbol;
        }

        InstrumentType inferredType = inferInstrumentType(normalized);
        if (inferredType != null) {
            return searchByType(inferredType, normalized);
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
    public OkxFundingRateUpdate getFundingRate(String instrumentId) {
        if (instrumentId == null || instrumentId.isBlank()) {
            throw new IllegalArgumentException("instrumentId is required");
        }

        String normalizedInstrumentId = instrumentId.trim().toUpperCase(Locale.US);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("instId", normalizedInstrumentId);

        JsonObject payload = executeGet("public/funding-rate", params);
        JsonArray data = getDataArray(payload);
        if (data == null || data.isEmpty() || !data.get(0).isJsonObject()) {
            return null;
        }
        return toFundingRateUpdate(data.get(0).getAsJsonObject(), normalizedInstrumentId);
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

    protected String normalizeBaseUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    protected void validateSupportedType(InstrumentType instrumentType) {
        if (!SUPPORTED_TYPES.contains(instrumentType)) {
            throw new IllegalArgumentException("Unsupported OKX instrument type: " + instrumentType);
        }
    }

    protected List<InstrumentDescriptor> loadInstrumentsForType(InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.OPTION) {
            return loadOptionInstruments();
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("instType", toOkxInstrumentType(instrumentType));
        return loadInstruments("public/instruments", params, instrumentType);
    }

    protected List<InstrumentDescriptor> loadOptionInstruments() {
        List<String> underlyings = loadUnderlyings("OPTION");
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        for (String underlying : underlyings) {
            if (!isPresent(underlying)) {
                continue;
            }
            Map<String, String> params = new LinkedHashMap<>();
            params.put("instType", "OPTION");
            params.put("uly", underlying);
            descriptors.addAll(loadInstruments("public/instruments", params, InstrumentType.OPTION));
        }
        return descriptors;
    }

    protected List<String> loadUnderlyings(String okxInstrumentType) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("instType", okxInstrumentType);
        JsonObject payload = executeGet("public/underlying", params);
        JsonArray data = getDataArray(payload);
        Set<String> underlyings = new LinkedHashSet<>();
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        for (JsonElement element : data) {
            collectUnderlyings(element, underlyings);
        }
        return new ArrayList<>(underlyings);
    }

    protected void collectUnderlyings(JsonElement element, Set<String> underlyings) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (isPresent(value)) {
                underlyings.add(value.trim().toUpperCase(Locale.US));
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement nested : element.getAsJsonArray()) {
                collectUnderlyings(nested, underlyings);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        String underlying = getString(object, "uly");
        if (!isPresent(underlying)) {
            underlying = getString(object, "instFamily");
        }
        if (isPresent(underlying)) {
            underlyings.add(underlying.trim().toUpperCase(Locale.US));
        }
    }

    protected List<InstrumentDescriptor> loadInstruments(String methodPath, Map<String, String> params,
            InstrumentType instrumentType) {
        JsonObject payload = executeGet(methodPath, params);
        return toInstrumentDescriptors(payload, instrumentType);
    }

    protected List<InstrumentDescriptor> toInstrumentDescriptors(JsonObject payload, InstrumentType requestedType) {
        JsonArray data = getDataArray(payload);
        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return descriptors;
        }

        for (JsonElement element : data) {
            if (!element.isJsonObject()) {
                continue;
            }
            InstrumentDescriptor descriptor = toInstrumentDescriptor(element.getAsJsonObject(), requestedType);
            if (descriptor == null) {
                continue;
            }
            descriptors.add(descriptor);
            cacheDescriptor(descriptor);
        }

        return descriptors;
    }

    protected void cacheDescriptor(InstrumentDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }

        if (descriptor.getExchangeSymbol() != null && !descriptor.getExchangeSymbol().isBlank()) {
            descriptorBySymbol.putIfAbsent(descriptor.getExchangeSymbol().toUpperCase(Locale.US), descriptor);
        }
        if (descriptor.getCommonSymbol() != null && !descriptor.getCommonSymbol().isBlank()) {
            descriptorByCommonSymbol.putIfAbsent(descriptor.getCommonSymbol().toUpperCase(Locale.US), descriptor);
        }
    }

    protected InstrumentDescriptor toInstrumentDescriptor(JsonObject instrumentObject, InstrumentType requestedType) {
        InstrumentType resolvedType = resolveInstrumentType(getString(instrumentObject, "instType"));
        if (resolvedType == null || resolvedType != requestedType) {
            return null;
        }

        String instId = getString(instrumentObject, "instId");
        if (instId == null || instId.isBlank()) {
            return null;
        }

        String baseCurrency = resolveBaseCurrency(instrumentObject);
        String quoteCurrency = resolveQuoteCurrency(instrumentObject, resolvedType);

        BigDecimal tickSize = sanitizePositive(getBigDecimal(instrumentObject, "tickSz"), new BigDecimal("0.0001"));
        BigDecimal lotSize = sanitizePositive(getBigDecimal(instrumentObject, "lotSz"), BigDecimal.ONE);
        BigDecimal minSize = sanitizePositive(getBigDecimal(instrumentObject, "minSz"), lotSize);

        BigDecimal contractValue = sanitizePositive(getBigDecimal(instrumentObject, "ctVal"), BigDecimal.ONE);
        BigDecimal contractMultiple = sanitizePositive(getBigDecimal(instrumentObject, "ctMult"), BigDecimal.ONE);
        BigDecimal contractMultiplier = resolvedType == InstrumentType.CRYPTO_SPOT ? BigDecimal.ONE
                : contractValue.multiply(contractMultiple);

        String commonSymbol = toCommonSymbol(resolvedType, instrumentObject, baseCurrency, quoteCurrency, instId);
        int fundingPeriodHours = resolvedType == InstrumentType.PERPETUAL_FUTURES ? 8 : 0;

        return new InstrumentDescriptor(resolvedType, Exchange.OKX, commonSymbol, instId, baseCurrency, quoteCurrency,
                lotSize.stripTrailingZeros(), tickSize.stripTrailingZeros(), 1, minSize.stripTrailingZeros(),
                fundingPeriodHours, contractMultiplier.stripTrailingZeros(), 1, instId);
    }

    protected String resolveBaseCurrency(JsonObject instrumentObject) {
        String baseCurrency = getString(instrumentObject, "baseCcy");
        if (isPresent(baseCurrency)) {
            return baseCurrency;
        }

        String underlying = getString(instrumentObject, "uly");
        String derived = firstToken(underlying);
        if (isPresent(derived)) {
            return derived;
        }

        String family = getString(instrumentObject, "instFamily");
        derived = firstToken(family);
        if (isPresent(derived)) {
            return derived;
        }

        String instId = getString(instrumentObject, "instId");
        derived = firstToken(instId);
        return isPresent(derived) ? derived : "UNKNOWN";
    }

    protected String resolveQuoteCurrency(JsonObject instrumentObject, InstrumentType instrumentType) {
        String quoteCurrency = getString(instrumentObject, "quoteCcy");
        if (isPresent(quoteCurrency)) {
            return quoteCurrency;
        }

        quoteCurrency = getString(instrumentObject, "settleCcy");
        if (isPresent(quoteCurrency)) {
            return quoteCurrency;
        }

        String underlying = getString(instrumentObject, "uly");
        String derived = secondToken(underlying);
        if (isPresent(derived)) {
            return derived;
        }

        String family = getString(instrumentObject, "instFamily");
        derived = secondToken(family);
        if (isPresent(derived)) {
            return derived;
        }

        if (instrumentType == InstrumentType.OPTION) {
            return "USD";
        }
        return "USDT";
    }

    protected String toCommonSymbol(InstrumentType instrumentType, JsonObject instrumentObject, String baseCurrency,
            String quoteCurrency, String instId) {
        if (instrumentType == InstrumentType.CRYPTO_SPOT) {
            return baseCurrency + "/" + quoteCurrency;
        }

        if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            return baseCurrency + "/" + quoteCurrency + "-PERP";
        }

        if (instrumentType == InstrumentType.FUTURES) {
            LocalDate expiry = resolveExpiry(instrumentObject, instId);
            if (expiry == null) {
                return instId;
            }
            return baseCurrency + "/" + quoteCurrency + "-" + expiry.format(COMMON_OPTION_DATE);
        }

        if (instrumentType == InstrumentType.OPTION) {
            LocalDate expiry = resolveExpiry(instrumentObject, instId);
            String strike = getString(instrumentObject, "stk");
            String optionRight = normalizeOptionRight(getString(instrumentObject, "optType"));
            if (expiry == null || !isPresent(strike) || !isPresent(optionRight)) {
                return instId;
            }
            return baseCurrency + "/" + quoteCurrency + "-" + expiry.format(COMMON_OPTION_DATE) + "-" + strike
                    + "-" + optionRight;
        }

        return instId;
    }

    protected LocalDate resolveExpiry(JsonObject instrumentObject, String instId) {
        Long expirationTimestamp = getLong(instrumentObject, "expTime");
        if (expirationTimestamp != null && expirationTimestamp.longValue() > 0L) {
            return Instant.ofEpochMilli(expirationTimestamp.longValue()).atZone(ZoneOffset.UTC).toLocalDate();
        }

        if (instId == null || instId.isBlank()) {
            return null;
        }

        String[] parts = instId.split("-");
        if (parts.length < 3 || !parts[2].matches("\\d{6}")) {
            return null;
        }

        try {
            return LocalDate.parse(parts[2], OKX_CONTRACT_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    protected InstrumentType resolveInstrumentType(String okxType) {
        if (okxType == null || okxType.isBlank()) {
            return null;
        }
        return switch (okxType.trim().toUpperCase(Locale.US)) {
            case "SPOT" -> InstrumentType.CRYPTO_SPOT;
            case "SWAP" -> InstrumentType.PERPETUAL_FUTURES;
            case "FUTURES" -> InstrumentType.FUTURES;
            case "OPTION" -> InstrumentType.OPTION;
            default -> null;
        };
    }

    protected InstrumentType inferInstrumentType(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        if (symbol.endsWith("-SWAP") || symbol.endsWith("-PERP")) {
            return InstrumentType.PERPETUAL_FUTURES;
        }
        if (symbol.matches("^[A-Z0-9]+-[A-Z0-9]+-\\d{6}$")
                || symbol.matches("^[A-Z0-9]+/[A-Z0-9]+-\\d{8}$")) {
            return InstrumentType.FUTURES;
        }
        if (symbol.matches("^[A-Z0-9]+-[A-Z0-9]+-\\d{6}-[0-9.]+-[CP]$")
                || symbol.matches("^[A-Z0-9]+/[A-Z0-9]+-\\d{8}-[0-9.]+-[CP]$")) {
            return InstrumentType.OPTION;
        }
        if (symbol.contains("/") || symbol.matches("^[A-Z0-9]+-[A-Z0-9]+$")) {
            return InstrumentType.CRYPTO_SPOT;
        }
        return null;
    }

    protected String normalizeOptionRight(String optionRight) {
        if (optionRight == null || optionRight.isBlank()) {
            return null;
        }

        String normalized = optionRight.trim().toUpperCase(Locale.US);
        if ("CALL".equals(normalized)) {
            return "C";
        }
        if ("PUT".equals(normalized)) {
            return "P";
        }
        return normalized;
    }

    protected String toOkxInstrumentType(InstrumentType instrumentType) {
        return switch (instrumentType) {
            case CRYPTO_SPOT -> "SPOT";
            case PERPETUAL_FUTURES -> "SWAP";
            case FUTURES -> "FUTURES";
            case OPTION -> "OPTION";
            default -> throw new IllegalArgumentException("Unsupported OKX instrument type: " + instrumentType);
        };
    }

    protected JsonObject executeGet(String methodPath, Map<String, String> params) {
        URI uri = buildUri(methodPath, params);
        Request request = new Request.Builder().url(uri.toString()).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? null : response.body().string();
            if (response.code() < 200 || response.code() >= 300) {
                throw new IllegalStateException(
                        "OKX returned HTTP " + response.code() + " for " + methodPath + " body="
                                + summarizeBody(responseBody));
            }

            JsonObject payload = JsonParser.parseString(responseBody).getAsJsonObject();
            String code = getString(payload, "code");
            if (code != null && !"0".equals(code)) {
                String message = getString(payload, "msg");
                throw new IllegalStateException(
                        "OKX API error for " + methodPath + " code=" + code + " message=" + message);
            }
            return payload;
        } catch (IOException e) {
            throw new IllegalStateException("I/O failure calling OKX " + methodPath, e);
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

    protected JsonArray getDataArray(JsonObject payload) {
        if (payload == null || !payload.has("data") || !payload.get("data").isJsonArray()) {
            return null;
        }
        return payload.getAsJsonArray("data");
    }

    protected OkxFundingRateUpdate toFundingRateUpdate(JsonObject payload, String fallbackInstrumentId) {
        if (payload == null) {
            return null;
        }

        String instrumentId = getString(payload, "instId");
        if (!isPresent(instrumentId)) {
            instrumentId = fallbackInstrumentId;
        }
        if (!isPresent(instrumentId)) {
            return null;
        }
        instrumentId = instrumentId.trim().toUpperCase(Locale.US);

        BigDecimal fundingRate = firstPresentDecimal(
                getBigDecimal(payload, "fundingRate"),
                getBigDecimal(payload, "funding_rate"));
        if (fundingRate == null) {
            return null;
        }

        BigDecimal nextFundingRate = firstPresentDecimal(
                getBigDecimal(payload, "nextFundingRate"),
                getBigDecimal(payload, "next_funding_rate"));
        Long fundingTime = firstPresentLong(getLong(payload, "fundingTime"), getLong(payload, "funding_time"));
        Long nextFundingTime = firstPresentLong(getLong(payload, "nextFundingTime"), getLong(payload, "next_funding_time"));
        Long timestamp = firstPresentLong(getLong(payload, "ts"), fundingTime);

        return new OkxFundingRateUpdate(instrumentId, getString(payload, "instType"), timestamp, fundingRate,
                nextFundingRate, fundingTime, nextFundingTime);
    }

    protected String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
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
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            logger.debug("Ignoring non-numeric decimal field key={} value={}", key, value);
            return null;
        }
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
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            logger.debug("Ignoring non-numeric long field key={} value={}", key, value);
            return null;
        }
    }

    protected BigDecimal sanitizePositive(BigDecimal value, BigDecimal defaultValue) {
        if (value == null || value.signum() <= 0) {
            return defaultValue;
        }
        return value;
    }

    protected String firstToken(String value) {
        if (!isPresent(value)) {
            return null;
        }
        String[] parts = value.trim().toUpperCase(Locale.US).split("-");
        return parts.length > 0 ? parts[0] : null;
    }

    protected String secondToken(String value) {
        if (!isPresent(value)) {
            return null;
        }
        String[] parts = value.trim().toUpperCase(Locale.US).split("-");
        return parts.length > 1 ? parts[1] : null;
    }

    protected boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    protected BigDecimal firstPresentDecimal(BigDecimal primary, BigDecimal fallback) {
        return primary != null ? primary : fallback;
    }

    protected Long firstPresentLong(Long primary, Long fallback) {
        return primary != null ? primary : fallback;
    }
}
