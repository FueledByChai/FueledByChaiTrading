package com.fueledbychai.lighter.common.api;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.ResponseException;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.account.LighterPosition;
import com.fueledbychai.http.BaseRestApi;
import com.fueledbychai.lighter.common.api.auth.LighterApiTokenResponse;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierRequest;
import com.fueledbychai.lighter.common.api.auth.LighterChangeAccountTierResponse;
import com.fueledbychai.lighter.common.api.auth.LighterCreateApiTokenRequest;
import com.fueledbychai.lighter.common.api.signer.LighterNativeTransactionSigner;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;
import com.fueledbychai.time.Span;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;
import com.fueledbychai.websocket.ProxyConfig;
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
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LighterRestApi extends BaseRestApi implements ILighterRestApi {
    protected static Logger logger = LoggerFactory.getLogger(LighterRestApi.class);
    protected static Logger latencyLogger = LoggerFactory.getLogger(Span.LATENCY_LOGGER_NAME);
    protected static final long DEFAULT_AUTH_TOKEN_TTL_SECONDS = 60L * 60L;
    protected static final long DEFAULT_API_TOKEN_TTL_SECONDS = 7L * 24L * 60L * 60L;
    protected static final String[] ACCOUNT_LOOKUP_BY_FIELDS = new String[] { "l1_address", "address", "account_address" };
    private final Gson gson;

    protected static ILighterRestApi publicOnlyApi;
    protected static ILighterRestApi privateApi;

    protected OkHttpClient client;
    protected String baseUrl;
    protected String accountAddressString;
    protected String privateKeyString;
    protected boolean publicApiOnly = true;
    protected volatile LighterNativeTransactionSigner authSigner;
    protected volatile int authSignerApiKeyIndex = Integer.MIN_VALUE;
    protected volatile long authSignerAccountIndex = Long.MIN_VALUE;

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
        this.client = client == null ? buildDefaultHttpClient() : client;
        this.baseUrl = baseUrl;
        this.accountAddressString = accountAddressString;
        this.privateKeyString = privateKeyString;
        // Register the custom adapter
        this.gson = new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).create();
        publicApiOnly = accountAddressString == null || privateKeyString == null;
    }

    protected OkHttpClient buildDefaultHttpClient() {
        Proxy proxy = ProxyConfig.getInstance().getProxy();
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (proxy != null && proxy != Proxy.NO_PROXY) {
            builder.proxy(proxy);
        }
        return builder.build();
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        return executeWithRetry(() -> {
            String path = "/orderBooks";
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

    @Override
    public LighterApiTokenResponse createApiToken(String authToken, LighterCreateApiTokenRequest request) {
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("authToken is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        request.validate();

        return executeWithRetry(() -> {
            String url = baseUrl + "/tokens/create";
            FormBody.Builder formBodyBuilder = new FormBody.Builder()
                    .add("name", request.getName())
                    .add("account_index", Long.toString(request.getAccountIndex()))
                    .add("expiry", Long.toString(request.getExpiry()))
                    .add("sub_account_access", Boolean.toString(request.isSubAccountAccess()));
            if (request.getScopes() != null && !request.getScopes().isBlank()) {
                formBodyBuilder.add("scopes", request.getScopes());
            }

            Request requestBuilder = new Request.Builder().url(url)
                    .addHeader("Authorization", authToken)
                    .post(formBodyBuilder.build())
                    .build();
            logger.info("Request: {}", requestBuilder);

            try (Response response = client.newCall(requestBuilder).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() == null ? "" : response.body().string();
                    logger.error("Error response: {}", errorBody);
                    throw buildApiErrorResponseException("Unable to create Lighter API token", response.code(),
                            response.message(), errorBody);
                }

                String responseBody = response.body().string();
                logger.info("Response output: {}", responseBody);
                return parseApiTokenResponse(responseBody);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new ResponseException("Network error creating Lighter API token: " + e.getMessage(), e);
            }
        }, 3, 500);
    }

    @Override
    public LighterApiTokenResponse createApiToken(LighterCreateApiTokenRequest request) {
        checkPrivateApi();
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        request.validate();

        long timestamp = System.currentTimeMillis() / 1000L;
        long expiry = Math.min(request.getExpiry(), timestamp + DEFAULT_AUTH_TOKEN_TTL_SECONDS);
        if (expiry <= timestamp) {
            expiry = timestamp + DEFAULT_AUTH_TOKEN_TTL_SECONDS;
        }

        int apiKeyIndex = LighterConfiguration.getInstance().getApiKeyIndex();
        String authToken = getOrCreateAuthSigner(apiKeyIndex, request.getAccountIndex())
                .createAuthTokenWithExpiry(timestamp, expiry, apiKeyIndex, request.getAccountIndex());
        return createApiToken(authToken, request);
    }

    @Override
    public LighterChangeAccountTierResponse changeAccountTier(String authToken, LighterChangeAccountTierRequest request) {
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("authToken is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        request.validate();

        return executeWithRetry(() -> {
            String url = baseUrl + "/changeAccountTier";
            FormBody.Builder formBodyBuilder = new FormBody.Builder()
                    .add("account_index", Long.toString(request.getAccountIndex()))
                    .add("new_tier", request.getNewTier().getApiValue());
            if (request.getAuth() != null && !request.getAuth().isBlank()) {
                formBodyBuilder.add("auth", request.getAuth());
            }

            Request requestBuilder = new Request.Builder().url(url)
                    .addHeader("Authorization", authToken)
                    .post(formBodyBuilder.build())
                    .build();
            logger.info("Request: {}", requestBuilder);

            try (Response response = client.newCall(requestBuilder).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Error response: {}", response.body().string());
                    throw new ResponseException("Unexpected code " + response.code() + ": " + response.message(),
                            response.code());
                }

                String responseBody = response.body().string();
                logger.info("Response output: {}", responseBody);
                return parseChangeAccountTierResponse(responseBody);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new ResponseException("Network error changing Lighter account tier: " + e.getMessage(), e);
            }
        }, 3, 500);
    }

    @Override
    public LighterChangeAccountTierResponse changeAccountTier(LighterChangeAccountTierRequest request) {
        checkPrivateApi();
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        request.validate();

        long timestamp = System.currentTimeMillis() / 1000L;
        long expiry = timestamp + DEFAULT_AUTH_TOKEN_TTL_SECONDS;
        int apiKeyIndex = LighterConfiguration.getInstance().getApiKeyIndex();
        String authToken = getOrCreateAuthSigner(apiKeyIndex, request.getAccountIndex())
                .createAuthTokenWithExpiry(timestamp, expiry, apiKeyIndex, request.getAccountIndex());
        return changeAccountTier(authToken, request);
    }

    @Override
    public String getApiToken() {
        return getApiToken(LighterConfiguration.getInstance().getAccountIndex());
    }

    @Override
    public String getApiToken(long accountIndex) {
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        LighterCreateApiTokenRequest request = buildDefaultApiTokenRequest(accountIndex);
        return createApiToken(request).getApiToken();
    }

    @Override
    public long resolveAccountIndex(String accountAddress) {
        if (accountAddress == null || accountAddress.isBlank()) {
            throw new IllegalArgumentException("accountAddress is required");
        }

        String normalizedAddress = accountAddress.trim();
        ResponseException lastResponseException = null;
        RuntimeException lastRuntimeException = null;

        for (String byField : ACCOUNT_LOOKUP_BY_FIELDS) {
            try {
                JsonObject root = requestAccountLookup(byField, normalizedAddress);
                Long resolved = parseAccountIndexFromAccountLookup(root, normalizedAddress);
                if (resolved != null && resolved.longValue() >= 0) {
                    return resolved.longValue();
                }
                logger.debug("Lighter account lookup returned no account index for by={} address={}", byField,
                        normalizedAddress);
            } catch (ResponseException ex) {
                lastResponseException = ex;
                logger.debug("Lighter account lookup by={} failed for address={}: {}", byField, normalizedAddress,
                        ex.getMessage());
            } catch (RuntimeException ex) {
                lastRuntimeException = ex;
                logger.debug("Unexpected error resolving Lighter account index by={} address={}", byField,
                        normalizedAddress, ex);
            }
        }

        String detail = "";
        if (lastResponseException != null && lastResponseException.getMessage() != null) {
            detail = ": " + lastResponseException.getMessage();
        } else if (lastRuntimeException != null && lastRuntimeException.getMessage() != null) {
            detail = ": " + lastRuntimeException.getMessage();
        }
        throw new ResponseException("Unable to resolve Lighter account index for address " + normalizedAddress + detail,
                lastResponseException == null ? 404 : lastResponseException.getStatusCode());
    }

    @Override
    public long getNextNonce(long accountIndex, int apiKeyIndex) {
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (apiKeyIndex < 0) {
            throw new IllegalArgumentException("apiKeyIndex must be >= 0");
        }

        return executeWithRetry(() -> {
            String url = baseUrl + "/nextNonce";
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            urlBuilder.addQueryParameter("account_index", Long.toString(accountIndex));
            urlBuilder.addQueryParameter("api_key_index", Integer.toString(apiKeyIndex));
            String newUrl = urlBuilder.build().toString();

            Request request = new Request.Builder().url(newUrl).get().build();
            logger.info("Request: {}", request);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = response.body() == null ? "" : response.body().string();
                    logger.error("Error response: {}", error);
                    throw new ResponseException("Unexpected code " + response.code() + ": " + response.message(),
                            response.code());
                }

                String responseBody = response.body() == null ? "" : response.body().string();
                logger.info("Response output: {}", responseBody);

                JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
                int code = readInt(root, "code", response.code());
                if (code != 200) {
                    String message = getString(root, "message");
                    if (message == null || message.isBlank()) {
                        message = getString(root, "msg");
                    }
                    if (message == null || message.isBlank()) {
                        message = responseBody;
                    }
                    throw new ResponseException("Unable to get Lighter next nonce: " + message, code);
                }
                if (!root.has("nonce") || root.get("nonce").isJsonNull()) {
                    throw new ResponseException("Lighter nextNonce response missing nonce", code);
                }
                return root.get("nonce").getAsLong();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new ResponseException("Network error getting Lighter next nonce: " + e.getMessage(), e);
            }
        }, 3, 500);
    }

    @Override
    public List<LighterOrder> getAccountActiveOrders(String authToken, long accountIndex, int marketId) {
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("authToken is required");
        }
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }

        return executeWithRetry(() -> {
            String url = baseUrl + "/accountActiveOrders";
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            urlBuilder.addQueryParameter("account_index", Long.toString(accountIndex));
            urlBuilder.addQueryParameter("market_id", Integer.toString(marketId));
            urlBuilder.addQueryParameter("authorization", authToken);
            String newUrl = urlBuilder.build().toString();

            Request request = new Request.Builder().url(newUrl).get().addHeader("Authorization", authToken).build();
            logger.info("Request: {}", request);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = response.body() == null ? "" : response.body().string();
                    logger.error("Error response: {}", error);
                    throw new ResponseException("Unexpected code " + response.code() + ": " + response.message(),
                            response.code());
                }

                String responseBody = response.body() == null ? "" : response.body().string();
                logger.info("Response output: {}", responseBody);
                return parseAccountActiveOrdersResponse(responseBody);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new ResponseException("Network error getting Lighter account active orders: " + e.getMessage(),
                        e);
            }
        }, 3, 500);
    }

    @Override
    public List<LighterOrder> getAccountActiveOrders(long accountIndex, int marketId) {
        checkPrivateApi();
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        long expiry = timestamp + DEFAULT_AUTH_TOKEN_TTL_SECONDS;
        int apiKeyIndex = LighterConfiguration.getInstance().getApiKeyIndex();
        String authToken = getOrCreateAuthSigner(apiKeyIndex, accountIndex).createAuthTokenWithExpiry(timestamp, expiry,
                apiKeyIndex, accountIndex);
        return getAccountActiveOrders(authToken, accountIndex, marketId);
    }

    protected ResponseException buildApiErrorResponseException(String operation, int httpStatusCode, String httpMessage,
            String responseBody) {
        int apiCode = Integer.MIN_VALUE;
        String apiMessage = null;

        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
                apiCode = readInt(root, "code", Integer.MIN_VALUE);
                apiMessage = getString(root, "message");
                if (apiMessage == null || apiMessage.isBlank()) {
                    apiMessage = getString(root, "msg");
                }
            } catch (Exception ignored) {
                // Non-JSON response body, keep raw text fallback below.
            }
        }

        StringBuilder message = new StringBuilder(operation == null || operation.isBlank() ? "Lighter API request failed"
                : operation);
        if (apiMessage != null && !apiMessage.isBlank()) {
            message.append(": ").append(apiMessage);
        } else if (responseBody != null && !responseBody.isBlank()) {
            message.append(": ").append(responseBody);
        } else {
            message.append(": Unexpected code ").append(httpStatusCode).append(": ").append(httpMessage);
        }

        if (apiCode != Integer.MIN_VALUE && apiCode != httpStatusCode) {
            message.append(" (api code ").append(apiCode).append(")");
        }
        return new ResponseException(message.toString(), httpStatusCode);
    }

    @Override
    public List<LighterPosition> getPositions(long accountIndex) {
        if (accountIndex < 0) {
            throw new IllegalArgumentException("accountIndex must be >= 0");
        }

        return executeWithRetry(() -> {
            String url = baseUrl + "/account";
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            urlBuilder.addQueryParameter("by", "index");
            urlBuilder.addQueryParameter("value", Long.toString(accountIndex));
            String newUrl = urlBuilder.build().toString();

            Request request = new Request.Builder().url(newUrl).get().build();
            logger.info("Request: {}", request);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = response.body() == null ? "" : response.body().string();
                    logger.error("Error response: {}", error);
                    throw new ResponseException("Unexpected code " + response.code() + ": " + response.message(),
                            response.code());
                }

                String responseBody = response.body() == null ? "" : response.body().string();
                logger.info("Response output: {}", responseBody);
                return parsePositionsResponse(responseBody, accountIndex);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new ResponseException("Network error getting Lighter positions: " + e.getMessage(), e);
            }
        }, 3, 500);
    }

    protected LighterCreateApiTokenRequest buildDefaultApiTokenRequest() {
        return buildDefaultApiTokenRequest(LighterConfiguration.getInstance().getAccountIndex());
    }

    protected LighterCreateApiTokenRequest buildDefaultApiTokenRequest(long accountIndex) {
        long timestamp = System.currentTimeMillis() / 1000L;

        LighterCreateApiTokenRequest request = new LighterCreateApiTokenRequest();
        request.setName("fueledbychai-readonly-" + timestamp);
        request.setAccountIndex(accountIndex);
        request.setExpiry(timestamp + DEFAULT_API_TOKEN_TTL_SECONDS);
        request.setSubAccountAccess(false);
        request.setScopes(LighterCreateApiTokenRequest.DEFAULT_SCOPES);
        return request;
    }

    protected JsonObject requestAccountLookup(String byField, String value) {
        if (byField == null || byField.isBlank()) {
            throw new IllegalArgumentException("byField is required");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }

        return executeWithRetry(() -> {
            String url = baseUrl + "/account";
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            urlBuilder.addQueryParameter("by", byField);
            urlBuilder.addQueryParameter("value", value);
            String newUrl = urlBuilder.build().toString();

            Request request = new Request.Builder().url(newUrl).get().build();
            logger.info("Request: {}", request);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = response.body() == null ? "" : response.body().string();
                    logger.error("Error response: {}", error);
                    throw new ResponseException("Unexpected code " + response.code() + ": " + response.message(),
                            response.code());
                }

                String responseBody = response.body() == null ? "" : response.body().string();
                logger.info("Response output: {}", responseBody);

                JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
                int code = readInt(root, "code", response.code());
                if (code != 200) {
                    String message = getString(root, "message");
                    if (message == null || message.isBlank()) {
                        message = getString(root, "msg");
                    }
                    if (message == null || message.isBlank()) {
                        message = responseBody;
                    }
                    throw new ResponseException("Unable to lookup Lighter account by " + byField + ": " + message,
                            code);
                }
                return root;
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new ResponseException("Network error looking up Lighter account: " + e.getMessage(), e);
            }
        }, 3, 500);
    }

    protected Long parseAccountIndexFromAccountLookup(JsonObject root, String accountAddress) {
        if (root == null) {
            return null;
        }

        Long rootIndex = readNonNegativeIndex(root);
        if (rootIndex != null) {
            return rootIndex;
        }

        if (root.has("account") && !root.get("account").isJsonNull() && root.get("account").isJsonObject()) {
            Long accountIndex = extractAccountIndexFromAccountObject(root.getAsJsonObject("account"), accountAddress);
            if (accountIndex != null) {
                return accountIndex;
            }
        }

        JsonArray accounts = root.has("accounts") && !root.get("accounts").isJsonNull() ? root.getAsJsonArray("accounts")
                : null;
        JsonObject accountObject = findAccountObjectByAddress(accounts, accountAddress);
        if (accountObject == null) {
            return null;
        }
        return extractAccountIndexFromAccountObject(accountObject, accountAddress);
    }

    protected JsonObject findAccountObjectByAddress(JsonArray accounts, String accountAddress) {
        if (accounts == null || accounts.size() == 0) {
            return null;
        }

        String normalizedAddress = normalizeAccountAddress(accountAddress);
        JsonObject first = null;
        for (int i = 0; i < accounts.size(); i++) {
            if (!accounts.get(i).isJsonObject()) {
                continue;
            }

            JsonObject accountObject = accounts.get(i).getAsJsonObject();
            if (first == null) {
                first = accountObject;
            }

            String responseAddress = getStringFromFields(accountObject, "l1_address", "account_address", "address",
                    "accountAddress", "owner");
            if (isSameAccountAddress(normalizedAddress, responseAddress)) {
                return accountObject;
            }
        }

        if (accounts.size() == 1) {
            return first;
        }
        return null;
    }

    protected Long extractAccountIndexFromAccountObject(JsonObject accountObject, String accountAddress) {
        if (accountObject == null) {
            return null;
        }

        Long accountIndex = readNonNegativeIndex(accountObject);
        if (accountIndex == null) {
            return null;
        }

        if (accountAddress == null || accountAddress.isBlank()) {
            return accountIndex;
        }

        String responseAddress = getStringFromFields(accountObject, "l1_address", "account_address", "address",
                "accountAddress", "owner");
        if (responseAddress == null || responseAddress.isBlank()) {
            return accountIndex;
        }
        return isSameAccountAddress(accountAddress, responseAddress) ? accountIndex : null;
    }

    protected Long readNonNegativeIndex(JsonObject accountObject) {
        long accountIndex = readLong(accountObject, "account_index", Long.MIN_VALUE);
        if (accountIndex == Long.MIN_VALUE) {
            accountIndex = readLong(accountObject, "index", Long.MIN_VALUE);
        }
        if (accountIndex < 0 || accountIndex == Long.MIN_VALUE) {
            return null;
        }
        return Long.valueOf(accountIndex);
    }

    protected boolean isSameAccountAddress(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return false;
        }
        return normalizeAccountAddress(left).equals(normalizeAccountAddress(right));
    }

    protected String normalizeAccountAddress(String accountAddress) {
        if (accountAddress == null) {
            return "";
        }

        String normalized = accountAddress.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        normalized = normalized.replaceFirst("^0+(?!$)", "");
        return normalized;
    }

    protected List<LighterOrder> parseAccountActiveOrdersResponse(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            int code = readInt(root, "code", 200);
            if (code != 200) {
                String message = getString(root, "message");
                if (message == null || message.isBlank()) {
                    message = responseBody;
                }
                throw new ResponseException("Unable to get Lighter account active orders: " + message, code);
            }

            JsonArray orders = root.has("orders") && !root.get("orders").isJsonNull() ? root.getAsJsonArray("orders")
                    : null;
            if (orders == null || orders.size() == 0) {
                return new ArrayList<>();
            }

            List<LighterOrder> parsedOrders = new ArrayList<>();
            for (int i = 0; i < orders.size(); i++) {
                if (!orders.get(i).isJsonObject()) {
                    continue;
                }
                LighterOrder order = parseOrder(orders.get(i).getAsJsonObject());
                if (order != null) {
                    parsedOrders.add(order);
                }
            }
            return parsedOrders;
        } catch (ResponseException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error parsing Lighter account active orders response: {}", e.getMessage(), e);
            throw new FueledByChaiException(e);
        }
    }

    protected List<LighterPosition> parsePositionsResponse(String responseBody, long accountIndex) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            int code = readInt(root, "code", 200);
            if (code != 200) {
                String message = getString(root, "message");
                if (message == null || message.isBlank()) {
                    message = responseBody;
                }
                throw new ResponseException("Unable to get Lighter positions: " + message, code);
            }

            JsonArray accounts = root.has("accounts") && !root.get("accounts").isJsonNull()
                    ? root.getAsJsonArray("accounts")
                    : null;
            if (accounts == null || accounts.size() == 0) {
                return new ArrayList<>();
            }

            JsonObject accountObject = findAccountObject(accounts, accountIndex);
            if (accountObject == null) {
                logger.warn("No account returned for account_index={} in positions response", accountIndex);
                return new ArrayList<>();
            }

            JsonArray positions = accountObject.has("positions") && !accountObject.get("positions").isJsonNull()
                    ? accountObject.getAsJsonArray("positions")
                    : null;
            if (positions == null || positions.size() == 0) {
                return new ArrayList<>();
            }

            ITickerRegistry tickerRegistry = getTickerRegistry();
            List<LighterPosition> result = new ArrayList<>();
            for (int i = 0; i < positions.size(); i++) {
                JsonObject positionObject = positions.get(i).getAsJsonObject();

                BigDecimal rawPosition = readBigDecimal(positionObject, "position", BigDecimal.ZERO);
                BigDecimal absolutePosition = rawPosition.abs();
                Side side = resolvePositionSide(positionObject, rawPosition);
                LighterPosition.Status status = absolutePosition.compareTo(BigDecimal.ZERO) > 0
                        ? LighterPosition.Status.OPEN
                        : LighterPosition.Status.CLOSED;

                LighterPosition position = new LighterPosition(resolvePositionTicker(positionObject, tickerRegistry));
                position.setSize(absolutePosition);
                position.setAverageEntryPrice(readBigDecimal(positionObject, "avg_entry_price", BigDecimal.ZERO));
                position.setLiquidationPrice(readBigDecimal(positionObject, "liquidation_price", BigDecimal.ZERO));
                position.setSide(side);
                position.setStatus(status);
                result.add(position);
            }
            return result;
        } catch (ResponseException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error parsing Lighter positions response: {}", e.getMessage(), e);
            throw new FueledByChaiException(e);
        }
    }

    protected LighterOrder parseOrder(JsonObject orderObject) {
        if (orderObject == null) {
            return null;
        }
        LighterOrder order = new LighterOrder();
        order.setOrderIndex(readNullableLong(orderObject, "order_index"));
        order.setClientOrderIndex(readNullableLong(orderObject, "client_order_index"));
        order.setOrderId(getString(orderObject, "order_id"));
        order.setClientOrderId(getString(orderObject, "client_order_id"));
        order.setMarketIndex(readNullableInteger(orderObject, "market_index"));
        order.setOwnerAccountIndex(readNullableLong(orderObject, "owner_account_index"));
        order.setInitialBaseAmount(readBigDecimal(orderObject, "initial_base_amount", null));
        order.setPrice(readBigDecimal(orderObject, "price", null));
        order.setNonce(readNullableLong(orderObject, "nonce"));
        order.setRemainingBaseAmount(readBigDecimal(orderObject, "remaining_base_amount", null));
        order.setAsk(readNullableBoolean(orderObject, "is_ask"));
        order.setBaseSize(readNullableLong(orderObject, "base_size"));
        order.setBasePrice(readNullableLong(orderObject, "base_price"));
        order.setFilledBaseAmount(readBigDecimal(orderObject, "filled_base_amount", null));
        order.setFilledQuoteAmount(readBigDecimal(orderObject, "filled_quote_amount", null));
        order.setSide(getString(orderObject, "side"));
        order.setType(getString(orderObject, "type"));
        order.setTimeInForce(getString(orderObject, "time_in_force"));
        order.setReduceOnly(readNullableBoolean(orderObject, "reduce_only"));
        order.setTriggerPrice(readBigDecimal(orderObject, "trigger_price", null));
        order.setOrderExpiry(readNullableLong(orderObject, "order_expiry"));
        order.setStatus(getString(orderObject, "status"));
        order.setTriggerStatus(getString(orderObject, "trigger_status"));
        order.setTriggerTime(readNullableLong(orderObject, "trigger_time"));
        order.setParentOrderIndex(readNullableLong(orderObject, "parent_order_index"));
        order.setParentOrderId(getString(orderObject, "parent_order_id"));
        order.setToTriggerOrderId0(getString(orderObject, "to_trigger_order_id_0"));
        order.setToTriggerOrderId1(getString(orderObject, "to_trigger_order_id_1"));
        order.setToCancelOrderId0(getString(orderObject, "to_cancel_order_id_0"));
        order.setBlockHeight(readNullableLong(orderObject, "block_height"));
        order.setTimestamp(readNullableLong(orderObject, "timestamp"));
        order.setCreatedAt(readNullableLong(orderObject, "created_at"));
        order.setUpdatedAt(readNullableLong(orderObject, "updated_at"));
        order.setTransactionTime(readNullableLong(orderObject, "transaction_time"));
        return order;
    }

    protected JsonObject findAccountObject(JsonArray accounts, long accountIndex) {
        if (accounts == null || accounts.size() == 0) {
            return null;
        }
        for (int i = 0; i < accounts.size(); i++) {
            JsonObject accountObject = accounts.get(i).getAsJsonObject();
            long responseIndex = readLong(accountObject, "account_index", Long.MIN_VALUE);
            if (responseIndex == Long.MIN_VALUE) {
                responseIndex = readLong(accountObject, "index", Long.MIN_VALUE);
            }
            if (responseIndex == accountIndex) {
                return accountObject;
            }
        }
        if (accounts.size() == 1) {
            return accounts.get(0).getAsJsonObject();
        }
        return null;
    }

    protected Side resolvePositionSide(JsonObject positionObject, BigDecimal rawPosition) {
        int sign = readInt(positionObject, "sign", 0);
        if (sign < 0) {
            return Side.SHORT;
        }
        if (sign > 0) {
            return Side.LONG;
        }
        if (rawPosition.signum() < 0) {
            return Side.SHORT;
        }
        return Side.LONG;
    }

    protected Ticker resolvePositionTicker(JsonObject positionObject, ITickerRegistry tickerRegistry) {
        String instrumentId = getStringFromFields(positionObject, "market_id", "marketId", "instrument_id",
                "instrumentId", "id");
        String symbol = getString(positionObject, "symbol");

        if (tickerRegistry != null) {
            if (instrumentId != null && !instrumentId.isBlank()) {
                Ticker ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, instrumentId);
                if (ticker != null) {
                    return ticker;
                }
            }

            if (symbol != null && !symbol.isBlank()) {
                Ticker ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
                if (ticker != null) {
                    return ticker;
                }
            }
        }

        return buildFallbackPositionTicker(symbol, instrumentId);
    }

    protected ITickerRegistry getTickerRegistry() {
        try {
            return TickerRegistryFactory.getInstance(Exchange.LIGHTER);
        } catch (Exception e) {
            logger.debug("Unable to load Lighter ticker registry for position parsing: {}", e.getMessage());
            return null;
        }
    }

    protected Ticker buildFallbackPositionTicker(String symbol, String instrumentId) {
        String tickerSymbol = symbol;
        if (tickerSymbol == null || tickerSymbol.isBlank()) {
            tickerSymbol = instrumentId == null || instrumentId.isBlank() ? "UNKNOWN" : instrumentId;
        }
        Ticker ticker = new Ticker(tickerSymbol);
        ticker.setSymbol(tickerSymbol);
        ticker.setId(instrumentId);
        ticker.setExchange(Exchange.LIGHTER);
        ticker.setPrimaryExchange(Exchange.LIGHTER);
        ticker.setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        ticker.setCurrency("USDC");
        return ticker;
    }

    protected LighterApiTokenResponse parseApiTokenResponse(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            LighterApiTokenResponse response = new LighterApiTokenResponse();
            response.setCode(readInt(root, "code", 0));
            response.setMessage(getString(root, "message"));
            response.setTokenId(readLong(root, "token_id", 0L));
            response.setApiToken(getString(root, "api_token"));
            response.setName(getString(root, "name"));
            response.setAccountIndex(readLong(root, "account_index", 0L));
            response.setExpiry(readLong(root, "expiry", 0L));
            response.setSubAccountAccess(readBoolean(root, "sub_account_access", false));
            response.setRevoked(readBoolean(root, "revoked", false));
            response.setScopes(getString(root, "scopes"));

            if (response.getApiToken() == null || response.getApiToken().isBlank()) {
                throw new FueledByChaiException("api_token not found in response");
            }
            return response;
        } catch (Exception e) {
            logger.error("Error parsing Lighter API token response: {}", e.getMessage(), e);
            throw new FueledByChaiException(e);
        }
    }

    protected LighterChangeAccountTierResponse parseChangeAccountTierResponse(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            LighterChangeAccountTierResponse response = new LighterChangeAccountTierResponse();
            response.setCode(readInt(root, "code", 0));
            response.setMessage(getString(root, "message"));
            return response;
        } catch (Exception e) {
            logger.error("Error parsing Lighter change account tier response: {}", e.getMessage(), e);
            throw new FueledByChaiException(e);
        }
    }

    protected void checkPrivateApi() {
        if (publicApiOnly) {
            throw new IllegalStateException(
                    "Private key configuration not available. Set both " + LighterConfiguration.LIGHTER_ACCOUNT_ADDRESS
                            + " and " + LighterConfiguration.LIGHTER_PRIVATE_KEY
                            + " to use authenticated Lighter REST API calls.");
        }
    }

    protected LighterNativeTransactionSigner getOrCreateAuthSigner() {
        LighterConfiguration configuration = LighterConfiguration.getInstance();
        return getOrCreateAuthSigner(configuration.getApiKeyIndex(), configuration.getAccountIndex());
    }

    protected LighterNativeTransactionSigner getOrCreateAuthSigner(int apiKeyIndex, long accountIndex) {
        LighterNativeTransactionSigner signer = authSigner;
        if (signer != null && authSignerApiKeyIndex == apiKeyIndex && authSignerAccountIndex == accountIndex) {
            return signer;
        }
        synchronized (this) {
            if (authSigner == null || authSignerApiKeyIndex != apiKeyIndex || authSignerAccountIndex != accountIndex) {
                LighterConfiguration configuration = LighterConfiguration.getInstance();
                String privateKey = (privateKeyString == null || privateKeyString.isBlank())
                        ? configuration.getPrivateKey()
                        : privateKeyString;
                authSigner = createAuthSigner(privateKey, apiKeyIndex, accountIndex);
                authSignerApiKeyIndex = apiKeyIndex;
                authSignerAccountIndex = accountIndex;
            }
            return authSigner;
        }
    }

    protected LighterNativeTransactionSigner createAuthSigner(String privateKey, int apiKeyIndex, long accountIndex) {
        return new LighterNativeTransactionSigner(baseUrl, privateKey, apiKeyIndex, accountIndex);
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

            // Create common symbol (Lighter uses USDC as quote)
            String commonSymbol = baseCurrency + "/USDC";
            String exchangeSymbol = symbol;

            // Parse tick size and order size increment from the JSON
            BigDecimal priceTickSize = instrumentObj.get("price_tick_size").getAsBigDecimal();
            BigDecimal orderSizeIncrement = instrumentObj.get("order_size_increment").getAsBigDecimal();

            // Parse min_notional and funding_period_hours
            int minNotionalOrderSize = instrumentObj.get("min_notional").getAsInt();
            int fundingPeriodHours = instrumentObj.get("funding_period_hours").getAsInt();
            String instrumentId = getStringFromFields(instrumentObj, "market_id", "marketId", "instrument_id",
                    "instrumentId", "id");

            // Create and return the InstrumentDescriptor
            return new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES, Exchange.LIGHTER, commonSymbol,
                    exchangeSymbol, baseCurrency, quoteCurrency, orderSizeIncrement, priceTickSize,
                    minNotionalOrderSize, BigDecimal.ZERO, fundingPeriodHours, BigDecimal.ONE, 1, instrumentId);

        } catch (Exception e) {
            logger.error("Error parsing instrument descriptor: " + e.getMessage(), e);
            throw new FueledByChaiException(e);
        }
    }

    protected InstrumentDescriptor[] parseInstrumentDescriptors(InstrumentType instrumentType, String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            // Lighter format: order_books/order_book_details/spot_order_book_details
            if (getOrderBookDetailsForType(root, instrumentType) != null) {
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
                String instrumentId = getStringFromFields(instrumentObj, "market_id", "marketId", "instrument_id",
                        "instrumentId", "id");

                // Create the InstrumentDescriptor with the provided instrument type
                InstrumentDescriptor descriptor = new InstrumentDescriptor(instrumentType, Exchange.LIGHTER,
                        commonSymbol, exchangeSymbol, baseCurrency, quoteCurrency, orderSizeIncrement, priceTickSize,
                        minNotionalOrderSize, BigDecimal.ZERO, fundingPeriodHours, BigDecimal.ONE, 1, instrumentId);

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

            String[] baseQuote = inferBaseQuoteCurrencies(symbol);
            String baseCurrency = baseQuote[0];
            String quoteCurrency = baseQuote[1];
            String commonSymbol = baseCurrency + "/USDC";
            String exchangeSymbol = symbol;

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

            String instrumentId = getStringFromFields(instrumentObj, "market_id", "marketId", "instrument_id",
                    "instrumentId", "id");

            InstrumentDescriptor descriptor = new InstrumentDescriptor(instrumentType, Exchange.LIGHTER, commonSymbol,
                    exchangeSymbol, baseCurrency, quoteCurrency, orderSizeIncrement, priceTickSize,
                    minNotionalOrderSize, minOrderSize, fundingPeriodHours, contractMultiplier, maxLeverage,
                    instrumentId);

            descriptors.add(descriptor);
        }

        return descriptors.toArray(new InstrumentDescriptor[0]);
    }

    protected JsonArray getOrderBookDetailsForType(JsonObject root, InstrumentType instrumentType) {
        if (root.has("order_books") && !root.get("order_books").isJsonNull()) {
            return root.getAsJsonArray("order_books");
        }
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
            return new String[] { "", "USDC" };
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

        return new String[] { trimmed, "USDC" };
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

    protected BigDecimal readBigDecimal(JsonObject obj, String field, BigDecimal defaultValue) {
        if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(field).getAsBigDecimal();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    protected Long readNullableLong(JsonObject obj, String field) {
        if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(field).getAsLong();
        } catch (Exception e) {
            try {
                return Long.valueOf(obj.get(field).getAsString());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    protected Integer readNullableInteger(JsonObject obj, String field) {
        if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(field).getAsInt();
        } catch (Exception e) {
            try {
                return Integer.valueOf(obj.get(field).getAsString());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    protected Boolean readNullableBoolean(JsonObject obj, String field) {
        if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(field).getAsBoolean();
        } catch (Exception e) {
            String text = obj.get(field).getAsString();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
            return null;
        }
    }

    protected String getString(JsonObject obj, String field) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsString();
        }
        return null;
    }

    protected int readInt(JsonObject obj, String field, int defaultValue) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsInt();
        }
        return defaultValue;
    }

    protected long readLong(JsonObject obj, String field, long defaultValue) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsLong();
        }
        return defaultValue;
    }

    protected boolean readBoolean(JsonObject obj, String field, boolean defaultValue) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsBoolean();
        }
        return defaultValue;
    }

    protected String getStringFromFields(JsonObject obj, String... fields) {
        if (obj == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            if (field != null && obj.has(field) && !obj.get(field).isJsonNull()) {
                return obj.get(field).getAsString();
            }
        }
        return "";
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
