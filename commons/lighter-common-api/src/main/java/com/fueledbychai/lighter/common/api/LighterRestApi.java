package com.fueledbychai.lighter.common.api;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.json.JSONObject;
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
import com.fueledbychai.time.Span;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LighterRestApi implements ILighterRestApi {
    protected static Logger logger = LoggerFactory.getLogger(LighterRestApi.class);
    protected static Logger latencyLogger = LoggerFactory.getLogger(Span.LATENCY_LOGGER_NAME);
    private final Gson gson;

    protected static ILighterRestApi publicOnlyApi;
    protected static ILighterRestApi privateApi;

    @FunctionalInterface
    public interface RetryableAction {
        void run() throws Exception; // Allows throwing checked exceptions
    }

    protected OkHttpClient client;
    protected String baseUrl;
    protected String accountAddressString;
    protected String privateKeyString;
    protected boolean publicApiOnly = true;

    public LighterRestApi(String baseUrl, boolean isTestnet) {
        this(baseUrl, null, null, isTestnet, null);
    }

    public LighterRestApi(String baseUrl, boolean isTestnet, OkHttpClient client) {
        this(baseUrl, null, null, isTestnet, client);
    }

    public LighterRestApi(String baseUrl, String accountAddressString, String privateKeyString, boolean isTestnet) {
        this(baseUrl, accountAddressString, privateKeyString, isTestnet, null);
    }

    public LighterRestApi(String baseUrl, String accountAddressString, String privateKeyString, boolean isTestnet,
            OkHttpClient client) {
        this.client = client == null ? new OkHttpClient() : client;
        this.baseUrl = baseUrl;
        this.accountAddressString = accountAddressString;
        this.privateKeyString = privateKeyString;
        // Register the custom adapter
        this.gson = new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).create();
        publicApiOnly = accountAddressString == null || privateKeyString == null;
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        return executeWithRetry(() -> {
            String path = "/orderBookDetails";
            String url = baseUrl + path;
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            String filter = getOrderBookFilter(instrumentType);
            if (filter != null && !filter.isBlank()) {
                urlBuilder.addQueryParameter("filter", filter);
            }
            String newUrl = urlBuilder.build().toString();

            Request request = new Request.Builder().url(newUrl).get().build();
            logger.info("Request: " + request);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Error response: " + response.body().string());
                    throw new ResponseException("Unexpected code " + response.code() + ": " + response.message(),
                            response.code());
                }

                String responseBody = response.body().string();
                logger.info("Response output: " + responseBody);
                return parseInstrumentDescriptors(instrumentType, responseBody);

            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new ResponseException("Network error getting all instruments for type: " + e.getMessage(), e);
            }
        }, 3, 500);
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        return executeWithRetry(() -> {
            String path = "/markets";
            String url = baseUrl + path;
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            urlBuilder.addQueryParameter("market", symbol);
            String newUrl = urlBuilder.build().toString();

            Request request = new Request.Builder().url(newUrl).get().build();
            logger.info("Request: " + request);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Error response: " + response.body().string());
                    throw new ResponseException("Unexpected code " + response.code() + ": " + response.message(),
                            response.code());
                }

                String responseBody = response.body().string();
                logger.info("Response output: " + responseBody);
                return parseInstrumentDescriptor(responseBody);

            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, 3, 500);
    }

    protected void executeWithRetry(RetryableAction action, int maxRetries, long retryDelayMillis) {
        int retries = 0;
        while (true) {
            try {
                action.run(); // Execute the action
                return; // Exit after successful execution
            } catch (java.net.SocketTimeoutException | IllegalStateException e) {
                if (retries < maxRetries) {
                    retries++;
                    logger.error("Request failed. Retrying... Attempt " + retries, e);
                    try {
                        Thread.sleep(retryDelayMillis * retries); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Retry interrupted", ie);
                    }
                } else {
                    logger.error("Max retries reached. Failing request.", e);
                    throw new IllegalStateException("Max retries reached", e);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex.getMessage(), ex); // Handle other exceptions (e.g., IOException, etc.)
            }
        }
    }

    protected <T> T executeWithRetry(Callable<T> action, int maxRetries, long retryDelayMillis) {
        int retries = 0;
        while (true) {
            try {
                return action.call(); // Execute the HTTP request
            } catch (java.net.SocketTimeoutException | IllegalStateException e) {
                if (retries < maxRetries) {
                    retries++;
                    logger.error("Request timed out. Retrying... Attempt " + retries, e);
                    try {
                        Thread.sleep(retryDelayMillis * retries); // Exponential backoff
                    } catch (InterruptedException ie) {
                        throw new IllegalStateException("Retry interrupted", ie);
                    }
                } else {
                    logger.error("Max retries reached. Failing request.", e);
                    throw new RuntimeException(e); // Rethrow the exception after max retries
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e); // Handle other exceptions (e.g., IOException, etc.)
            }
        }
    }

    protected InstrumentDescriptor parseInstrumentDescriptor(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray results = root.getAsJsonArray("results");

            if (results == null || results.size() == 0) {
                logger.warn("No results found in instrument descriptor response");
                return null;
            }

            // Parse the first instrument from the results array
            JsonObject instrumentObj = results.get(0).getAsJsonObject();

            // Validate that this is a perpetual futures instrument
            String assetKind = null;
            if (instrumentObj.has("asset_kind") && !instrumentObj.get("asset_kind").isJsonNull()) {
                assetKind = instrumentObj.get("asset_kind").getAsString();
            }

            if (!"PERP".equals(assetKind)) {
                String symbol = instrumentObj.has("symbol") ? instrumentObj.get("symbol").getAsString() : "unknown";
                logger.error("Expected PERP asset_kind for instrument '{}', but found '{}'", symbol, assetKind);
                throw new FueledByChaiException("Invalid asset_kind '" + assetKind + "' for instrument '" + symbol
                        + "'. Expected 'PERP' for perpetual futures.");
            }

            // Extract required fields for InstrumentDescriptor
            String symbol = instrumentObj.get("symbol").getAsString();
            String baseCurrency = instrumentObj.get("base_currency").getAsString();
            String quoteCurrency = instrumentObj.get("quote_currency").getAsString();

            // Create common symbol (without exchange-specific suffix)
            String commonSymbol = symbol + "/USD";
            String exchangeSymbol = symbol;

            // Parse tick size and order size increment from the JSON
            BigDecimal priceTickSize = instrumentObj.get("price_tick_size").getAsBigDecimal();
            BigDecimal orderSizeIncrement = instrumentObj.get("order_size_increment").getAsBigDecimal();

            // Parse min_notional and funding_period_hours
            int minNotionalOrderSize = instrumentObj.get("min_notional").getAsInt();
            int fundingPeriodHours = instrumentObj.get("funding_period_hours").getAsInt();

            // Create and return the InstrumentDescriptor
            return new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES, Exchange.LIGHTER, commonSymbol,
                    exchangeSymbol, baseCurrency, quoteCurrency, orderSizeIncrement, priceTickSize,
                    minNotionalOrderSize, BigDecimal.ZERO, fundingPeriodHours, BigDecimal.ONE, 1, "");

        } catch (Exception e) {
            logger.error("Error parsing instrument descriptor: " + e.getMessage(), e);
            throw new FueledByChaiException(e);
        }
    }

    protected InstrumentDescriptor[] parseInstrumentDescriptors(InstrumentType instrumentType, String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            // Lighter format: order_book_details / spot_order_book_details
            if (root.has("order_book_details") || root.has("spot_order_book_details")) {
                return parseOrderBookInstrumentDescriptors(instrumentType, root);
            }

            // Fallback: Paradex-style "results"
            JsonArray results = null;
            if (root.has("results") && !root.get("results").isJsonNull()) {
                results = root.getAsJsonArray("results");
            }

            if (results == null || results.size() == 0) {
                logger.warn("No results found in instrument descriptors response");
                return new InstrumentDescriptor[0];
            }

            List<InstrumentDescriptor> descriptors = new ArrayList<>();

            // Parse each instrument from the results array
            for (int i = 0; i < results.size(); i++) {
                JsonObject instrumentObj = results.get(i).getAsJsonObject();

                // Validate asset_kind matches the expected instrument type
                String assetKind = null;
                if (instrumentObj.has("asset_kind") && !instrumentObj.get("asset_kind").isJsonNull()) {
                    assetKind = instrumentObj.get("asset_kind").getAsString();
                }

                if (assetKind == null || !isValidAssetKindForInstrumentType(assetKind, instrumentType)) {
                    logger.warn("Skipping instrument with asset_kind '{}' as it doesn't match expected type '{}'",
                            assetKind, instrumentType);
                    continue;
                }

                // Extract required fields for InstrumentDescriptor
                String symbol = instrumentObj.get("symbol").getAsString();
                String baseCurrency = instrumentObj.get("base_currency").getAsString();
                String quoteCurrency = instrumentObj.get("quote_currency").getAsString();

                // Create common symbol (without exchange-specific suffix)
                String commonSymbol = symbol.split("-")[0];
                String exchangeSymbol = symbol;

                // Parse tick size and order size increment from the JSON
                BigDecimal priceTickSize = instrumentObj.get("price_tick_size").getAsBigDecimal();
                BigDecimal orderSizeIncrement = instrumentObj.get("order_size_increment").getAsBigDecimal();

                // Parse min_notional and funding_period_hours
                int minNotionalOrderSize = instrumentObj.get("min_notional").getAsInt();
                int fundingPeriodHours = instrumentObj.get("funding_period_hours").getAsInt();

                // Create the InstrumentDescriptor with the provided instrument type
                InstrumentDescriptor descriptor = new InstrumentDescriptor(instrumentType, Exchange.PARADEX,
                        commonSymbol, exchangeSymbol, baseCurrency, quoteCurrency, orderSizeIncrement, priceTickSize,
                        minNotionalOrderSize, BigDecimal.ZERO, fundingPeriodHours, BigDecimal.ONE, 1, "");

                descriptors.add(descriptor);
            }

            return descriptors.toArray(new InstrumentDescriptor[0]);

        } catch (Exception e) {
            logger.error("Error parsing instrument descriptors: " + e.getMessage(), e);
            throw new FueledByChaiException(e);
        }
    }

    protected InstrumentDescriptor[] parseOrderBookInstrumentDescriptors(InstrumentType instrumentType,
            JsonObject root) {
        JsonArray orderBookDetails = getOrderBookDetailsForType(root, instrumentType);
        if (orderBookDetails == null || orderBookDetails.size() == 0) {
            logger.warn("No order_book_details found for instrument type {}", instrumentType);
            return new InstrumentDescriptor[0];
        }

        List<InstrumentDescriptor> descriptors = new ArrayList<>();

        for (int i = 0; i < orderBookDetails.size(); i++) {
            JsonObject instrumentObj = orderBookDetails.get(i).getAsJsonObject();

            String status = getString(instrumentObj, "status");
            if (status != null && !"active".equalsIgnoreCase(status)) {
                continue;
            }

            String marketType = getString(instrumentObj, "market_type");
            if (!isValidMarketTypeForInstrumentType(marketType, instrumentType)) {
                logger.warn("Skipping instrument with market_type '{}' as it doesn't match expected type '{}'",
                        marketType, instrumentType);
                continue;
            }

            String symbol = getString(instrumentObj, "symbol");
            if (symbol == null) {
                logger.warn("Skipping instrument with missing symbol");
                continue;
            }

            String commonSymbol = symbol.contains("-") ? symbol.split("-")[0] : symbol;
            String exchangeSymbol = symbol;

            String[] baseQuote = inferBaseQuoteCurrencies(symbol);
            String baseCurrency = baseQuote[0];
            String quoteCurrency = baseQuote[1];

            int sizeDecimals = getIntFromFields(instrumentObj, 0, "size_decimals", "supported_size_decimals");
            int priceDecimals = getIntFromFields(instrumentObj, 0, "price_decimals", "supported_price_decimals");
            BigDecimal orderSizeIncrement = decimalFromDecimals(sizeDecimals);
            BigDecimal priceTickSize = decimalFromDecimals(priceDecimals);

            BigDecimal minOrderSize = getBigDecimal(instrumentObj, "min_base_amount", BigDecimal.ZERO);
            int minNotionalOrderSize = toInt(getBigDecimal(instrumentObj, "min_quote_amount", BigDecimal.ZERO));

            int fundingPeriodHours = instrumentType == InstrumentType.PERPETUAL_FUTURES ? 8 : 0;
            BigDecimal contractMultiplier = getBigDecimal(instrumentObj, "quote_multiplier", BigDecimal.ONE);
            int marginFraction = getIntFromFields(instrumentObj, 0, "default_initial_margin_fraction");
            if (marginFraction <= 0) {
                marginFraction = getIntFromFields(instrumentObj, 0, "min_initial_margin_fraction");
            }
            int maxLeverage = getMaxLeverageFromMarginFraction(marginFraction);
            if (maxLeverage <= 0) {
                maxLeverage = 1;
            }

            String instrumentId = instrumentObj.has("market_id") && !instrumentObj.get("market_id").isJsonNull()
                    ? instrumentObj.get("market_id").getAsString()
                    : "";

            InstrumentDescriptor descriptor = new InstrumentDescriptor(instrumentType, Exchange.PARADEX, commonSymbol,
                    exchangeSymbol, baseCurrency, quoteCurrency, orderSizeIncrement, priceTickSize,
                    minNotionalOrderSize, minOrderSize, fundingPeriodHours, contractMultiplier, maxLeverage,
                    instrumentId);

            descriptors.add(descriptor);
        }

        return descriptors.toArray(new InstrumentDescriptor[0]);
    }

    protected JsonArray getOrderBookDetailsForType(JsonObject root, InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.CRYPTO_SPOT) {
            if (root.has("spot_order_book_details") && !root.get("spot_order_book_details").isJsonNull()) {
                return root.getAsJsonArray("spot_order_book_details");
            }
        }

        if (root.has("order_book_details") && !root.get("order_book_details").isJsonNull()) {
            return root.getAsJsonArray("order_book_details");
        }

        if (root.has("spot_order_book_details") && !root.get("spot_order_book_details").isJsonNull()) {
            return root.getAsJsonArray("spot_order_book_details");
        }

        return null;
    }

    protected String getOrderBookFilter(InstrumentType instrumentType) {
        if (instrumentType == null) {
            return null;
        }
        switch (instrumentType) {
        case CRYPTO_SPOT:
            return "spot";
        case PERPETUAL_FUTURES:
            return "perp";
        default:
            return null;
        }
    }

    protected boolean isValidMarketTypeForInstrumentType(String marketType, InstrumentType instrumentType) {
        if (marketType == null) {
            return false;
        }

        switch (instrumentType) {
        case PERPETUAL_FUTURES:
            return "perp".equalsIgnoreCase(marketType);
        case FUTURES:
            return "future".equalsIgnoreCase(marketType);
        case OPTION:
            return "option".equalsIgnoreCase(marketType);
        case CRYPTO_SPOT:
            return "spot".equalsIgnoreCase(marketType);
        default:
            logger.warn("Unknown instrument type '{}' - allowing market_type '{}'", instrumentType, marketType);
            return true;
        }
    }

    protected String[] inferBaseQuoteCurrencies(String symbol) {
        if (symbol == null) {
            return new String[] { "", "USD" };
        }

        String trimmed = symbol.trim();
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-");
            if (parts.length >= 2) {
                return new String[] { parts[0], parts[1] };
            }
        }
        if (trimmed.contains("/")) {
            String[] parts = trimmed.split("/");
            if (parts.length >= 2) {
                return new String[] { parts[0], parts[1] };
            }
        }
        if (trimmed.length() == 6) {
            return new String[] { trimmed.substring(0, 3), trimmed.substring(3, 6) };
        }

        return new String[] { trimmed, "USD" };
    }

    protected BigDecimal decimalFromDecimals(int decimals) {
        if (decimals <= 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ONE.divide(BigDecimal.TEN.pow(decimals));
    }

    protected BigDecimal getBigDecimal(JsonObject obj, String field, BigDecimal defaultValue) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsBigDecimal();
        }
        return defaultValue;
    }

    protected String getString(JsonObject obj, String field) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsString();
        }
        return null;
    }

    protected int getIntFromFields(JsonObject obj, int defaultValue, String... fields) {
        for (String field : fields) {
            if (obj.has(field) && !obj.get(field).isJsonNull()) {
                return obj.get(field).getAsInt();
            }
        }
        return defaultValue;
    }

    protected int toInt(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return value.intValue();
    }

    protected int getMaxLeverageFromMarginFraction(int marginFraction) {
        if (marginFraction <= 0) {
            return 0;
        }
        return (int) Math.floor(10000.0 / marginFraction);
    }

    /**
     * Validates that the asset_kind from JSON matches the expected InstrumentType
     */
    protected boolean isValidAssetKindForInstrumentType(String assetKind, InstrumentType instrumentType) {
        // Handle null asset_kind
        if (assetKind == null) {
            return false;
        }

        switch (instrumentType) {
        case PERPETUAL_FUTURES:
            return "PERP".equals(assetKind);
        case FUTURES:
            return "FUTURE".equals(assetKind);
        case OPTION:
            return "OPTION".equals(assetKind);
        case CRYPTO_SPOT:
            return "SPOT".equals(assetKind);
        default:
            // For unknown instrument types, log a warning but allow processing
            logger.warn("Unknown instrument type '{}' - allowing asset_kind '{}'", instrumentType, assetKind);
            return true;
        }
    }

    private class ZonedDateTimeAdapter extends TypeAdapter<ZonedDateTime> {

        private final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

        @Override
        public void write(JsonWriter out, ZonedDateTime value) throws IOException {
            out.value(value.format(FORMATTER));
        }

        @Override
        public ZonedDateTime read(JsonReader in) throws IOException {
            return ZonedDateTime.parse(in.nextString(), FORMATTER);
        }
    }

}
