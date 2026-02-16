package com.fueledbychai.lighter.common.api.signer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.security.SecureRandom;

import org.json.JSONObject;

import com.fueledbychai.lighter.common.api.LighterConfiguration;
import com.fueledbychai.lighter.common.api.ILighterRestApi;
import com.fueledbychai.lighter.common.api.LighterRestApi;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterOrderType;
import com.fueledbychai.lighter.common.api.order.LighterTimeInForce;

/**
 * Java implementation of Lighter tx signing flow (matching lighter-go).
 */
public class LighterNativeTransactionSigner implements ILighterTransactionSigner {

    private static final int MAINNET_CHAIN_ID = 304;
    private static final int TESTNET_CHAIN_ID = 300;

    private static final int TX_TYPE_L2_CREATE_ORDER = 14;
    private static final int TX_TYPE_L2_CANCEL_ORDER = 15;
    private static final int TX_TYPE_L2_MODIFY_ORDER = 17;

    private static final long DEFAULT_TX_EXPIRY_MILLIS = Duration.ofMinutes(10).minusSeconds(1).toMillis();
    private static final long DEFAULT_ORDER_EXPIRY_MILLIS = Duration.ofDays(28).toMillis();
    private static final long DEFAULT_AUTH_TOKEN_TTL_SECONDS = Duration.ofMinutes(10).toSeconds();

    private final ILighterRestApi restApi;
    private final int chainId;
    private final int configuredApiKeyIndex;
    private final long configuredAccountIndex;
    private final String normalizedPrivateKey;
    private final LighterSignerMath.Scalar apiPrivateKey;
    private final LighterSignerMath.Fp5 apiPublicKey;
    private final SecureRandom secureRandom;

    public LighterNativeTransactionSigner(LighterConfiguration configuration) {
        this(getRestUrl(configuration), getPrivateKey(configuration), getApiKeyIndex(configuration),
                getAccountIndex(configuration), createDefaultRestApi(getRestUrl(configuration)));
    }

    public LighterNativeTransactionSigner(String restUrl, String privateKey, int apiKeyIndex, long accountIndex) {
        this(restUrl, privateKey, apiKeyIndex, accountIndex, createDefaultRestApi(restUrl), new SecureRandom());
    }

    public LighterNativeTransactionSigner(String restUrl, String privateKey, int apiKeyIndex, long accountIndex,
            ILighterRestApi restApi) {
        this(restUrl, privateKey, apiKeyIndex, accountIndex, restApi, new SecureRandom());
    }

    public LighterNativeTransactionSigner(String restUrl, String privateKey, int apiKeyIndex, long accountIndex,
            String signerLibraryPath) {
        this(restUrl, privateKey, apiKeyIndex, accountIndex);
    }

    protected LighterNativeTransactionSigner(String restUrl, String privateKey, int apiKeyIndex, long accountIndex,
            SecureRandom secureRandom) {
        this(restUrl, privateKey, apiKeyIndex, accountIndex, createDefaultRestApi(restUrl), secureRandom);
    }

    protected LighterNativeTransactionSigner(String restUrl, String privateKey, int apiKeyIndex, long accountIndex,
            ILighterRestApi restApi, SecureRandom secureRandom) {
        String normalizedRestUrl = requireNonBlank(restUrl, "restUrl");
        this.restApi = Objects.requireNonNull(restApi, "restApi is required");
        this.normalizedPrivateKey = normalizePrivateKey(privateKey);
        this.apiPrivateKey = LighterSignerMath.parsePrivateKeyHex(this.normalizedPrivateKey);
        this.apiPublicKey = LighterSignerMath.publicKeyFromPrivateKey(this.apiPrivateKey);
        this.configuredApiKeyIndex = apiKeyIndex;
        this.configuredAccountIndex = accountIndex;
        this.chainId = resolveChainId(normalizedRestUrl);
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom is required");
    }

    private static LighterConfiguration requireConfiguration(LighterConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration is required");
        }
        return configuration;
    }

    private static String getRestUrl(LighterConfiguration configuration) {
        return requireConfiguration(configuration).getRestUrl();
    }

    private static String getPrivateKey(LighterConfiguration configuration) {
        return requireConfiguration(configuration).getPrivateKey();
    }

    private static int getApiKeyIndex(LighterConfiguration configuration) {
        return requireConfiguration(configuration).getApiKeyIndex();
    }

    private static long getAccountIndex(LighterConfiguration configuration) {
        return requireConfiguration(configuration).getAccountIndex();
    }

    private static ILighterRestApi createDefaultRestApi(String restUrl) {
        return new LighterRestApi(restUrl, !isMainnetUrl(restUrl));
    }

    private static boolean isMainnetUrl(String restUrl) {
        String url = restUrl == null ? "" : restUrl.toLowerCase(Locale.ROOT);
        return url.contains("mainnet");
    }

    @Override
    public LighterSignedTransaction signCreateOrder(LighterCreateOrderRequest orderRequest) {
        if (orderRequest == null) {
            throw new IllegalArgumentException("orderRequest is required");
        }
        orderRequest.validate();

        int apiKeyIndex = resolveApiKeyIndex(orderRequest.getApiKeyIndex());
        long accountIndex = resolveAccountIndex(orderRequest.getAccountIndex());
        validateSignerIdentity(apiKeyIndex, accountIndex);

        long nonce = resolveNonce(orderRequest.getNonce(), accountIndex, apiKeyIndex);
        long expiredAt = nowMillis() + DEFAULT_TX_EXPIRY_MILLIS;
        long orderExpiry = resolveOrderExpiry(orderRequest);

        LighterSignerMath.Fp5 txHashField = hashCreateOrder(orderRequest, accountIndex, apiKeyIndex, nonce, expiredAt,
                orderExpiry);
        byte[] txHashBytes = txHashField.toLittleEndian40();
        LighterSignerMath.SchnorrSignature signature = LighterSignerMath.signHashedMessage(txHashField,
                apiPrivateKey, secureRandom);

        JSONObject txInfo = new JSONObject();
        txInfo.put("AccountIndex", accountIndex);
        txInfo.put("ApiKeyIndex", apiKeyIndex);
        txInfo.put("MarketIndex", orderRequest.getMarketIndex());
        txInfo.put("ClientOrderIndex", orderRequest.getClientOrderIndex());
        txInfo.put("BaseAmount", orderRequest.getBaseAmount());
        txInfo.put("Price", Integer.toUnsignedLong(orderRequest.getPrice()));
        txInfo.put("IsAsk", orderRequest.isAsk() ? 1 : 0);
        txInfo.put("Type", orderRequest.getOrderType().getCode());
        txInfo.put("TimeInForce", orderRequest.getTimeInForce().getCode());
        txInfo.put("ReduceOnly", orderRequest.isReduceOnly() ? 1 : 0);
        txInfo.put("TriggerPrice", Integer.toUnsignedLong(orderRequest.getTriggerPrice()));
        txInfo.put("OrderExpiry", orderExpiry);
        txInfo.put("ExpiredAt", expiredAt);
        txInfo.put("Nonce", nonce);
        txInfo.put("Sig", Base64.getEncoder().encodeToString(signature.toBytes()));

        return new LighterSignedTransaction(TX_TYPE_L2_CREATE_ORDER, txInfo, LighterSignerMath.toHex(txHashBytes),
                null);
    }

    @Override
    public LighterSignedTransaction signCancelOrder(LighterCancelOrderRequest cancelRequest) {
        if (cancelRequest == null) {
            throw new IllegalArgumentException("cancelRequest is required");
        }
        cancelRequest.validate();

        int apiKeyIndex = resolveApiKeyIndex(cancelRequest.getApiKeyIndex());
        long accountIndex = resolveAccountIndex(cancelRequest.getAccountIndex());
        validateSignerIdentity(apiKeyIndex, accountIndex);

        long nonce = resolveNonce(cancelRequest.getNonce(), accountIndex, apiKeyIndex);
        long expiredAt = nowMillis() + DEFAULT_TX_EXPIRY_MILLIS;

        LighterSignerMath.Fp5 txHashField = hashCancelOrder(cancelRequest, accountIndex, apiKeyIndex, nonce,
                expiredAt);
        byte[] txHashBytes = txHashField.toLittleEndian40();
        LighterSignerMath.SchnorrSignature signature = LighterSignerMath.signHashedMessage(txHashField,
                apiPrivateKey, secureRandom);

        JSONObject txInfo = new JSONObject();
        txInfo.put("AccountIndex", accountIndex);
        txInfo.put("ApiKeyIndex", apiKeyIndex);
        txInfo.put("MarketIndex", cancelRequest.getMarketIndex());
        txInfo.put("Index", cancelRequest.getOrderIndex());
        txInfo.put("ExpiredAt", expiredAt);
        txInfo.put("Nonce", nonce);
        txInfo.put("Sig", Base64.getEncoder().encodeToString(signature.toBytes()));

        return new LighterSignedTransaction(TX_TYPE_L2_CANCEL_ORDER, txInfo, LighterSignerMath.toHex(txHashBytes),
                null);
    }

    @Override
    public LighterSignedTransaction signModifyOrder(LighterModifyOrderRequest modifyRequest) {
        if (modifyRequest == null) {
            throw new IllegalArgumentException("modifyRequest is required");
        }
        modifyRequest.validate();

        int apiKeyIndex = resolveApiKeyIndex(modifyRequest.getApiKeyIndex());
        long accountIndex = resolveAccountIndex(modifyRequest.getAccountIndex());
        validateSignerIdentity(apiKeyIndex, accountIndex);

        long nonce = resolveNonce(modifyRequest.getNonce(), accountIndex, apiKeyIndex);
        long expiredAt = nowMillis() + DEFAULT_TX_EXPIRY_MILLIS;

        LighterSignerMath.Fp5 txHashField = hashModifyOrder(modifyRequest, accountIndex, apiKeyIndex, nonce,
                expiredAt);
        byte[] txHashBytes = txHashField.toLittleEndian40();
        LighterSignerMath.SchnorrSignature signature = LighterSignerMath.signHashedMessage(txHashField,
                apiPrivateKey, secureRandom);

        JSONObject txInfo = new JSONObject();
        txInfo.put("AccountIndex", accountIndex);
        txInfo.put("ApiKeyIndex", apiKeyIndex);
        txInfo.put("MarketIndex", modifyRequest.getMarketIndex());
        txInfo.put("Index", modifyRequest.getOrderIndex());
        txInfo.put("BaseAmount", modifyRequest.getBaseAmount());
        txInfo.put("Price", Integer.toUnsignedLong(modifyRequest.getPrice()));
        txInfo.put("TriggerPrice", Integer.toUnsignedLong(modifyRequest.getTriggerPrice()));
        txInfo.put("ExpiredAt", expiredAt);
        txInfo.put("Nonce", nonce);
        txInfo.put("Sig", Base64.getEncoder().encodeToString(signature.toBytes()));

        return new LighterSignedTransaction(TX_TYPE_L2_MODIFY_ORDER, txInfo, LighterSignerMath.toHex(txHashBytes),
                null);
    }

    public LighterApiKey generateApiKey() {
        LighterSignerMath.Scalar privateKey = LighterSignerMath.sampleScalar(secureRandom);
        LighterSignerMath.Fp5 publicKey = LighterSignerMath.publicKeyFromPrivateKey(privateKey);

        return new LighterApiKey(
                prefixHex(LighterSignerMath.toHex(publicKey.toLittleEndian40())),
                prefixHex(LighterSignerMath.toHex(privateKey.toLittleEndian40())));
    }

    public String createAuthTokenWithExpiry(long timestamp, long expiry, int apiKeyIndex, long accountIndex) {
        int effectiveApiKeyIndex = resolveApiKeyIndex(apiKeyIndex);
        long effectiveAccountIndex = resolveAccountIndex(accountIndex);
        validateSignerIdentity(effectiveApiKeyIndex, effectiveAccountIndex);

        long deadline = expiry;
        long nowSeconds = nowSeconds();
        long baseSeconds = timestamp > 0 ? timestamp : nowSeconds;

        if (deadline <= 0) {
            deadline = baseSeconds + DEFAULT_AUTH_TOKEN_TTL_SECONDS;
        }
        if (deadline <= baseSeconds) {
            deadline = baseSeconds + DEFAULT_AUTH_TOKEN_TTL_SECONDS;
        }

        String message = deadline + ":" + effectiveAccountIndex + ":" + effectiveApiKeyIndex;
        LighterSignerMath.Fp5 messageHash = LighterSignerMath.hashAuthTokenMessage(message);
        LighterSignerMath.SchnorrSignature signature = LighterSignerMath.signHashedMessage(messageHash,
                apiPrivateKey, secureRandom);

        return message + ":" + LighterSignerMath.toHex(signature.toBytes());
    }

    public String getPublicKey(int apiKeyIndex, long accountIndex) {
        return getApiPublicKey(apiKeyIndex, accountIndex);
    }

    public String getApiPublicKey(int apiKeyIndex, long accountIndex) {
        int effectiveApiKeyIndex = resolveApiKeyIndex(apiKeyIndex);
        long effectiveAccountIndex = resolveAccountIndex(accountIndex);
        validateSignerIdentity(effectiveApiKeyIndex, effectiveAccountIndex);

        return LighterSignerMath.toHex(apiPublicKey.toLittleEndian40());
    }

    public String getApiPrivateKey(int apiKeyIndex, long accountIndex) {
        int effectiveApiKeyIndex = resolveApiKeyIndex(apiKeyIndex);
        long effectiveAccountIndex = resolveAccountIndex(accountIndex);
        validateSignerIdentity(effectiveApiKeyIndex, effectiveAccountIndex);

        return normalizedPrivateKey;
    }

    protected int resolveChainId(String restUrl) {
        String url = restUrl == null ? "" : restUrl.toLowerCase(Locale.ROOT);
        return url.contains("mainnet") ? MAINNET_CHAIN_ID : TESTNET_CHAIN_ID;
    }

    protected long nowMillis() {
        return System.currentTimeMillis();
    }

    protected long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    protected long fetchNextNonce(long accountIndex, int apiKeyIndex) {
        return restApi.getNextNonce(accountIndex, apiKeyIndex);
    }

    protected long resolveNonce(long nonce, long accountIndex, int apiKeyIndex) {
        if (nonce >= 0) {
            return nonce;
        }
        return fetchNextNonce(accountIndex, apiKeyIndex);
    }

    protected long resolveOrderExpiry(LighterCreateOrderRequest request) {
        long orderExpiry = request.getOrderExpiry();
        if (orderExpiry != LighterCreateOrderRequest.DEFAULT_ORDER_EXPIRY) {
            return orderExpiry;
        }

        if (request.getOrderType() == LighterOrderType.MARKET
                || request.getTimeInForce() == LighterTimeInForce.IOC) {
            return 0L;
        }
        return nowMillis() + DEFAULT_ORDER_EXPIRY_MILLIS;
    }

    protected int resolveApiKeyIndex(int requestedApiKeyIndex) {
        if (requestedApiKeyIndex == LighterCreateOrderRequest.DEFAULT_API_KEY_INDEX
                && configuredApiKeyIndex != LighterCreateOrderRequest.DEFAULT_API_KEY_INDEX) {
            return configuredApiKeyIndex;
        }
        return requestedApiKeyIndex;
    }

    protected long resolveAccountIndex(long requestedAccountIndex) {
        if (requestedAccountIndex == LighterCreateOrderRequest.DEFAULT_ACCOUNT_INDEX
                && configuredAccountIndex != LighterCreateOrderRequest.DEFAULT_ACCOUNT_INDEX) {
            return configuredAccountIndex;
        }
        return requestedAccountIndex;
    }

    protected void validateSignerIdentity(int apiKeyIndex, long accountIndex) {
        if (apiKeyIndex != configuredApiKeyIndex || accountIndex != configuredAccountIndex) {
            throw new IllegalArgumentException("Signer was initialized for accountIndex=" + configuredAccountIndex
                    + " apiKeyIndex=" + configuredApiKeyIndex + ", but request was accountIndex=" + accountIndex
                    + " apiKeyIndex=" + apiKeyIndex + ".");
        }
    }

    protected LighterSignerMath.Fp5 hashCreateOrder(LighterCreateOrderRequest request, long accountIndex,
            int apiKeyIndex, long nonce, long expiredAt, long orderExpiry) {
        List<LighterSignerMath.GoldilocksField> elems = new ArrayList<>(16);

        elems.add(unsigned32(chainId));
        elems.add(unsigned32(TX_TYPE_L2_CREATE_ORDER));
        elems.add(signed64(nonce));
        elems.add(signed64(expiredAt));

        elems.add(signed64(accountIndex));
        elems.add(unsigned32(apiKeyIndex));
        elems.add(unsigned32(request.getMarketIndex()));
        elems.add(signed64(request.getClientOrderIndex()));
        elems.add(signed64(request.getBaseAmount()));
        elems.add(unsigned32(Integer.toUnsignedLong(request.getPrice())));
        elems.add(unsigned32(request.isAsk() ? 1L : 0L));
        elems.add(unsigned32(request.getOrderType().getCode()));
        elems.add(unsigned32(request.getTimeInForce().getCode()));
        elems.add(unsigned32(request.isReduceOnly() ? 1L : 0L));
        elems.add(unsigned32(Integer.toUnsignedLong(request.getTriggerPrice())));
        elems.add(signed64(orderExpiry));

        return LighterSignerMath.hashToQuinticExtension(elems);
    }

    protected LighterSignerMath.Fp5 hashCancelOrder(LighterCancelOrderRequest request, long accountIndex,
            int apiKeyIndex, long nonce, long expiredAt) {
        List<LighterSignerMath.GoldilocksField> elems = new ArrayList<>(8);

        elems.add(unsigned32(chainId));
        elems.add(unsigned32(TX_TYPE_L2_CANCEL_ORDER));
        elems.add(signed64(nonce));
        elems.add(signed64(expiredAt));

        elems.add(signed64(accountIndex));
        elems.add(unsigned32(apiKeyIndex));
        elems.add(unsigned32(request.getMarketIndex()));
        elems.add(signed64(request.getOrderIndex()));

        return LighterSignerMath.hashToQuinticExtension(elems);
    }

    protected LighterSignerMath.Fp5 hashModifyOrder(LighterModifyOrderRequest request, long accountIndex,
            int apiKeyIndex, long nonce, long expiredAt) {
        List<LighterSignerMath.GoldilocksField> elems = new ArrayList<>(11);

        elems.add(unsigned32(chainId));
        elems.add(unsigned32(TX_TYPE_L2_MODIFY_ORDER));
        elems.add(signed64(nonce));
        elems.add(signed64(expiredAt));

        elems.add(signed64(accountIndex));
        elems.add(unsigned32(apiKeyIndex));
        elems.add(unsigned32(request.getMarketIndex()));
        elems.add(signed64(request.getOrderIndex()));
        elems.add(signed64(request.getBaseAmount()));
        elems.add(unsigned32(Integer.toUnsignedLong(request.getPrice())));
        elems.add(unsigned32(Integer.toUnsignedLong(request.getTriggerPrice())));

        return LighterSignerMath.hashToQuinticExtension(elems);
    }

    protected LighterSignerMath.GoldilocksField signed64(long value) {
        return LighterSignerMath.GoldilocksField.fromSignedLong(value);
    }

    protected LighterSignerMath.GoldilocksField unsigned32(long value) {
        return LighterSignerMath.GoldilocksField.fromUnsignedLong(value);
    }

    protected String normalizePrivateKey(String privateKey) {
        String value = trimToNull(privateKey);
        if (value == null) {
            throw new IllegalStateException("Missing private key configuration for Lighter signing.");
        }
        if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }
        return value;
    }

    protected String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String prefixHex(String hex) {
        if (hex == null || hex.isBlank()) {
            return "0x";
        }
        return hex.startsWith("0x") || hex.startsWith("0X") ? hex : "0x" + hex;
    }
}
