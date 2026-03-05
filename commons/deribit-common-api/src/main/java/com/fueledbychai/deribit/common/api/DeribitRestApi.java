package com.fueledbychai.deribit.common.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

/**
 * Public REST implementation for Deribit market metadata.
 */
public class DeribitRestApi implements IDeribitRestApi {

    protected static final Logger logger = LoggerFactory.getLogger(DeribitRestApi.class);
    protected static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);
    protected static final DateTimeFormatter DERIBIT_OPTION_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dMMMyy")
            .toFormatter(Locale.US);
    protected static final List<InstrumentType> SUPPORTED_TYPES = List.of(
            InstrumentType.CRYPTO_SPOT, InstrumentType.PERPETUAL_FUTURES, InstrumentType.OPTION);
    protected static final List<String> FALLBACK_CURRENCIES = List.of("BTC", "ETH", "SOL", "XRP", "BNB", "PAXG");

    protected final String baseUrl;
    protected final HttpClient httpClient;
    protected final boolean publicApiOnly;
    protected final Map<InstrumentType, InstrumentDescriptor[]> descriptorsByType = new EnumMap<>(InstrumentType.class);
    protected final Map<String, InstrumentDescriptor> descriptorBySymbol = new ConcurrentHashMap<>();
    protected volatile List<String> supportedCurrencies;

    public DeribitRestApi(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.httpClient = createHttpClient();
        this.publicApiOnly = true;
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        validateSupportedType(instrumentType);
        synchronized (descriptorsByType) {
            InstrumentDescriptor[] cached = descriptorsByType.get(instrumentType);
            if (cached != null) {
                return cached;
            }

            List<InstrumentDescriptor> descriptors = new ArrayList<>();
            for (String currency : getSupportedCurrencies()) {
                descriptors.addAll(loadInstrumentsForTypeAndCurrency(instrumentType, currency));
            }

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
        InstrumentDescriptor cached = descriptorBySymbol.get(normalized);
        if (cached != null) {
            return cached;
        }

        InstrumentType inferredType = inferInstrumentType(normalized);
        if (inferredType != null) {
            for (InstrumentDescriptor descriptor : getAllInstrumentsForType(inferredType)) {
                if (normalized.equalsIgnoreCase(descriptor.getExchangeSymbol())) {
                    return descriptor;
                }
            }
            return null;
        }

        for (InstrumentType instrumentType : SUPPORTED_TYPES) {
            for (InstrumentDescriptor descriptor : getAllInstrumentsForType(instrumentType)) {
                if (normalized.equalsIgnoreCase(descriptor.getExchangeSymbol())) {
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

    protected HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
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
            throw new IllegalArgumentException("Unsupported Deribit instrument type: " + instrumentType);
        }
    }

    protected List<InstrumentDescriptor> loadInstrumentsForTypeAndCurrency(InstrumentType instrumentType, String currency) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("currency", currency);
        params.put("expired", "false");
        params.put("kind", toDeribitKind(instrumentType));

        JsonObject response = executeGet("public/get_instruments", params);
        JsonArray result = getResultArray(response);
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<InstrumentDescriptor> descriptors = new ArrayList<>();
        for (JsonElement element : result) {
            if (!element.isJsonObject()) {
                continue;
            }
            InstrumentDescriptor descriptor = toInstrumentDescriptor(element.getAsJsonObject(), instrumentType);
            if (descriptor != null) {
                descriptors.add(descriptor);
                descriptorBySymbol.putIfAbsent(descriptor.getExchangeSymbol().toUpperCase(Locale.US), descriptor);
            }
        }
        return descriptors;
    }

    protected InstrumentDescriptor toInstrumentDescriptor(JsonObject instrumentObject, InstrumentType requestedType) {
        InstrumentType resolvedType = resolveInstrumentType(instrumentObject);
        if (resolvedType == null || resolvedType != requestedType) {
            return null;
        }

        String instrumentName = getString(instrumentObject, "instrument_name");
        String baseCurrency = getString(instrumentObject, "base_currency");
        String quoteCurrency = resolveQuoteCurrency(instrumentObject, resolvedType);
        BigDecimal tickSize = sanitizePositive(getBigDecimal(instrumentObject, "tick_size"), new BigDecimal("0.01"));
        BigDecimal minTradeAmount = sanitizePositive(getBigDecimal(instrumentObject, "min_trade_amount"), BigDecimal.ONE);
        BigDecimal contractSize = sanitizePositive(getBigDecimal(instrumentObject, "contract_size"), BigDecimal.ONE);
        String instrumentId = getString(instrumentObject, "instrument_id");
        Long expirationTimestamp = getLong(instrumentObject, "expiration_timestamp");

        String commonSymbol = toCommonSymbol(resolvedType, baseCurrency, quoteCurrency, instrumentName, expirationTimestamp);
        int fundingPeriodHours = resolvedType == InstrumentType.PERPETUAL_FUTURES ? 8 : 0;

        return new InstrumentDescriptor(resolvedType, Exchange.DERIBIT, commonSymbol, instrumentName, baseCurrency,
                quoteCurrency, minTradeAmount.stripTrailingZeros(), tickSize.stripTrailingZeros(), 0,
                minTradeAmount.stripTrailingZeros(), fundingPeriodHours, contractSize.stripTrailingZeros(), 1,
                instrumentId);
    }

    protected String resolveQuoteCurrency(JsonObject instrumentObject, InstrumentType instrumentType) {
        String quoteCurrency = getString(instrumentObject, "quote_currency");
        if (quoteCurrency == null || quoteCurrency.isBlank()) {
            quoteCurrency = getString(instrumentObject, "counter_currency");
        }
        if ((quoteCurrency == null || quoteCurrency.isBlank()) && instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            quoteCurrency = "USD";
        }
        if ((quoteCurrency == null || quoteCurrency.isBlank()) && instrumentType == InstrumentType.OPTION) {
            quoteCurrency = "USD";
        }
        return quoteCurrency == null ? "USD" : quoteCurrency;
    }

    protected String toCommonSymbol(InstrumentType instrumentType, String baseCurrency, String quoteCurrency,
            String instrumentName, Long expirationTimestamp) {
        if (instrumentName == null || instrumentName.isBlank()) {
            return null;
        }
        if (instrumentType == InstrumentType.CRYPTO_SPOT || instrumentType == InstrumentType.PERPETUAL_FUTURES) {
            return baseCurrency + "/" + quoteCurrency;
        }
        if (instrumentType != InstrumentType.OPTION) {
            return instrumentName;
        }

        String[] parts = instrumentName.toUpperCase(Locale.US).split("-");
        if (parts.length != 4) {
            return instrumentName;
        }

        LocalDate expiry = resolveOptionExpiry(expirationTimestamp, parts[1]);
        if (expiry == null) {
            return instrumentName;
        }
        return baseCurrency + "/" + quoteCurrency + "-"
                + String.format(Locale.US, "%04d%02d%02d", expiry.getYear(), expiry.getMonthValue(),
                        expiry.getDayOfMonth())
                + "-" + parts[2] + "-" + parts[3];
    }

    protected LocalDate resolveOptionExpiry(Long expirationTimestamp, String exchangeExpiryToken) {
        if (expirationTimestamp != null && expirationTimestamp.longValue() > 0L) {
            return Instant.ofEpochMilli(expirationTimestamp.longValue()).atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (exchangeExpiryToken == null || exchangeExpiryToken.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(exchangeExpiryToken, DERIBIT_OPTION_DATE);
        } catch (RuntimeException e) {
            return null;
        }
    }

    protected InstrumentType resolveInstrumentType(JsonObject instrumentObject) {
        String kind = getString(instrumentObject, "kind");
        String settlementPeriod = getString(instrumentObject, "settlement_period");
        String instrumentName = getString(instrumentObject, "instrument_name");
        if ("spot".equalsIgnoreCase(kind)) {
            return InstrumentType.CRYPTO_SPOT;
        }
        if ("option".equalsIgnoreCase(kind)) {
            return InstrumentType.OPTION;
        }
        if ("future".equalsIgnoreCase(kind)
                && ("perpetual".equalsIgnoreCase(settlementPeriod)
                        || (instrumentName != null && instrumentName.endsWith("-PERPETUAL")))) {
            return InstrumentType.PERPETUAL_FUTURES;
        }
        return null;
    }

    protected InstrumentType inferInstrumentType(String symbol) {
        if (symbol == null) {
            return null;
        }
        if (symbol.contains("_")) {
            return InstrumentType.CRYPTO_SPOT;
        }
        if (symbol.endsWith("-PERPETUAL")) {
            return InstrumentType.PERPETUAL_FUTURES;
        }
        if (symbol.matches("^[A-Z0-9]+-\\d{1,2}[A-Z]{3}\\d{2}-[0-9.]+-[CP]$")) {
            return InstrumentType.OPTION;
        }
        return null;
    }

    protected String toDeribitKind(InstrumentType instrumentType) {
        return switch (instrumentType) {
            case CRYPTO_SPOT -> "spot";
            case PERPETUAL_FUTURES -> "future";
            case OPTION -> "option";
            default -> throw new IllegalArgumentException("Unsupported Deribit instrument type: " + instrumentType);
        };
    }

    protected List<String> getSupportedCurrencies() {
        List<String> cached = supportedCurrencies;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (supportedCurrencies != null) {
                return supportedCurrencies;
            }

            try {
                JsonObject response = executeGet("public/get_currencies", Collections.emptyMap());
                JsonArray result = getResultArray(response);
                List<String> currencies = new ArrayList<>();
                if (result != null) {
                    for (JsonElement element : result) {
                        if (!element.isJsonObject()) {
                            continue;
                        }
                        String currency = getString(element.getAsJsonObject(), "currency");
                        if (currency != null && !currency.isBlank()) {
                            currencies.add(currency.toUpperCase(Locale.US));
                        }
                    }
                }
                if (!currencies.isEmpty()) {
                    supportedCurrencies = List.copyOf(currencies);
                    return supportedCurrencies;
                }
            } catch (RuntimeException e) {
                logger.warn("Falling back to a static Deribit currency list after get_currencies failed", e);
            }

            supportedCurrencies = FALLBACK_CURRENCIES;
            return supportedCurrencies;
        }
    }

    protected JsonObject executeGet(String methodPath, Map<String, String> params) {
        try {
            URI uri = buildUri(methodPath, params);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Deribit returned HTTP " + response.statusCode() + " for " + methodPath);
            }

            JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
            if (payload.has("error") && payload.get("error").isJsonObject()) {
                JsonObject error = payload.getAsJsonObject("error");
                throw new IllegalStateException("Deribit API error for " + methodPath + ": " + error);
            }
            return payload;
        } catch (IOException e) {
            throw new IllegalStateException("I/O failure calling Deribit " + methodPath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Deribit " + methodPath, e);
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

    protected JsonArray getResultArray(JsonObject payload) {
        if (payload == null || !payload.has("result") || !payload.get("result").isJsonArray()) {
            return null;
        }
        return payload.getAsJsonArray("result");
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
        if (element.isJsonPrimitive()) {
            return new BigDecimal(element.getAsString());
        }
        return null;
    }

    protected Long getLong(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element.isJsonPrimitive()) {
            return Long.valueOf(element.getAsString());
        }
        return null;
    }

    protected BigDecimal sanitizePositive(BigDecimal value, BigDecimal defaultValue) {
        if (value == null || value.signum() <= 0) {
            return defaultValue;
        }
        return value;
    }
}
