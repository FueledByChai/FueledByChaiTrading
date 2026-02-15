package com.fueledbychai.lighter.common.api;

import java.util.concurrent.atomic.AtomicLong;

import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterOrderType;
import com.fueledbychai.lighter.common.api.order.LighterTimeInForce;
import com.fueledbychai.lighter.common.api.signer.ILighterTransactionSigner;
import com.fueledbychai.lighter.common.api.signer.LighterApiKey;
import com.fueledbychai.lighter.common.api.signer.LighterNativeTransactionSigner;
import com.fueledbychai.lighter.common.api.signer.LighterSignedTransaction;
import com.fueledbychai.lighter.common.api.ws.LighterSendTxResponse;

/**
 * Java counterpart of Lighter's Python SignerClient workflow:
 * 1) sign transaction payload with native signer
 * 2) submit signed tx using jsonapi/sendtx
 */
public class LighterSignerClient {

    // Order types (from lighter-python signer_client.py)
    public static final int ORDER_TYPE_LIMIT = LighterOrderType.LIMIT.getCode();
    public static final int ORDER_TYPE_MARKET = LighterOrderType.MARKET.getCode();
    public static final int ORDER_TYPE_STOP_LOSS = LighterOrderType.STOP_LOSS.getCode();
    public static final int ORDER_TYPE_STOP_LOSS_LIMIT = LighterOrderType.STOP_LOSS_LIMIT.getCode();
    public static final int ORDER_TYPE_TAKE_PROFIT = LighterOrderType.TAKE_PROFIT.getCode();
    public static final int ORDER_TYPE_TAKE_PROFIT_LIMIT = LighterOrderType.TAKE_PROFIT_LIMIT.getCode();
    public static final int ORDER_TYPE_TWAP = LighterOrderType.TWAP.getCode();

    // Time in force (from lighter-python signer_client.py)
    public static final int TIF_IOC = LighterTimeInForce.IOC.getCode();
    public static final int TIF_GTT = LighterTimeInForce.GTT.getCode();
    public static final int TIF_POST_ONLY = LighterTimeInForce.POST_ONLY.getCode();

    // Transaction type constants (from lighter-python signer_client.py)
    public static final int TX_TYPE_CREATE_ORDER = 10;
    public static final int TX_TYPE_CANCEL_ORDER = 11;
    public static final int TX_TYPE_CANCEL_ALL_ORDERS = 12;
    public static final int TX_TYPE_MODIFY_ORDER = 13;
    public static final int TX_TYPE_CREATE_ORDER_GROUP = 14;
    public static final int TX_TYPE_SET_REFERRER = 15;
    public static final int TX_TYPE_UPDATE_ACCOUNT_MARGIN = 16;
    public static final int TX_TYPE_UPDATE_LEVERAGE = 17;
    public static final int TX_TYPE_DELEGATE_SIGNER = 18;
    public static final int TX_TYPE_REVOKE_SIGNER = 19;
    public static final int TX_TYPE_ADD_API_KEY = 20;
    public static final int TX_TYPE_REMOVE_API_KEY = 21;
    public static final int TX_TYPE_WITHDRAW = 22;
    public static final int TX_TYPE_CONVERT_USDC = 23;
    public static final int TX_TYPE_CREATE_SUB_ACCOUNT = 24;
    public static final int TX_TYPE_SUB_ACCOUNT_TRANSFER = 25;
    public static final int TX_TYPE_ADD_SUB_ACCOUNT_SIGNER = 26;
    public static final int TX_TYPE_REMOVE_SUB_ACCOUNT_SIGNER = 27;
    public static final int TX_TYPE_DELETE_SUB_ACCOUNT = 28;
    public static final int TX_TYPE_SET_SUB_ACCOUNT_MARGIN_TYPE = 29;
    public static final int TX_TYPE_SET_SUB_ACCOUNT_VALUE = 30;

    private final ILighterWebSocketApi webSocketApi;
    private final ILighterTransactionSigner signer;
    private final AtomicLong clientOrderIndexGenerator = new AtomicLong(System.currentTimeMillis());

    public LighterSignerClient() {
        this(new LighterWebSocketApi(),
                new LighterNativeTransactionSigner(LighterConfiguration.getInstance()));
    }

    public LighterSignerClient(ILighterWebSocketApi webSocketApi, ILighterTransactionSigner signer) {
        if (webSocketApi == null) {
            throw new IllegalArgumentException("webSocketApi is required");
        }
        if (signer == null) {
            throw new IllegalArgumentException("signer is required");
        }
        this.webSocketApi = webSocketApi;
        this.signer = signer;
    }

    public LighterSignedTransaction signCreateOrder(LighterCreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        request.validate();
        return signer.signCreateOrder(request);
    }

    public LighterSendTxResponse createOrder(LighterCreateOrderRequest request) {
        LighterSignedTransaction signed = signCreateOrder(request);
        return webSocketApi.sendSignedTransaction(signed.getTxType(), signed.getTxInfo());
    }

    public LighterSignedTransaction signCancelOrder(LighterCancelOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        request.validate();
        return signer.signCancelOrder(request);
    }

    public LighterSendTxResponse cancelOrder(LighterCancelOrderRequest request) {
        LighterSignedTransaction signed = signCancelOrder(request);
        return webSocketApi.sendSignedTransaction(signed.getTxType(), signed.getTxInfo());
    }

    public LighterSignedTransaction signModifyOrder(LighterModifyOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        request.validate();
        return signer.signModifyOrder(request);
    }

    public LighterSendTxResponse modifyOrder(LighterModifyOrderRequest request) {
        LighterSignedTransaction signed = signModifyOrder(request);
        return webSocketApi.sendSignedTransaction(signed.getTxType(), signed.getTxInfo());
    }

    public LighterSendTxResponse createMarketOrder(int marketIndex, long clientOrderIndex, long baseAmount,
            int aggressivePrice, boolean isAsk) {
        LighterCreateOrderRequest request = LighterCreateOrderRequest.marketOrder(marketIndex, clientOrderIndex,
                baseAmount, aggressivePrice, isAsk);
        return createOrder(request);
    }

    public LighterSendTxResponse createMarketOrder(int marketIndex, long baseAmount, int aggressivePrice,
            boolean isAsk) {
        long clientOrderIndex = clientOrderIndexGenerator.incrementAndGet();
        return createMarketOrder(marketIndex, clientOrderIndex, baseAmount, aggressivePrice, isAsk);
    }

    public LighterApiKey generateApiKey() {
        return getNativeSigner().generateApiKey();
    }

    public String createAuthTokenWithExpiry(long timestamp, long expiry, int apiKeyIndex, long accountIndex) {
        return getNativeSigner().createAuthTokenWithExpiry(timestamp, expiry, apiKeyIndex, accountIndex);
    }

    public String getPublicKey(int apiKeyIndex, long accountIndex) {
        return getNativeSigner().getPublicKey(apiKeyIndex, accountIndex);
    }

    public String getApiPublicKey(int apiKeyIndex, long accountIndex) {
        return getNativeSigner().getApiPublicKey(apiKeyIndex, accountIndex);
    }

    public String getApiPrivateKey(int apiKeyIndex, long accountIndex) {
        return getNativeSigner().getApiPrivateKey(apiKeyIndex, accountIndex);
    }

    protected LighterNativeTransactionSigner getNativeSigner() {
        if (signer instanceof LighterNativeTransactionSigner nativeSigner) {
            return nativeSigner;
        }
        throw new IllegalStateException(
                "Configured signer does not support native signer client operations.");
    }
}
