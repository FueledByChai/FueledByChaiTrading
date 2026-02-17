package com.fueledbychai.lighter.common.api.order;

/**
 * Low-level request for Lighter cancel-order signing.
 */
public class LighterCancelOrderRequest {

    public static final int DEFAULT_API_KEY_INDEX = LighterCreateOrderRequest.DEFAULT_API_KEY_INDEX;
    public static final long DEFAULT_ACCOUNT_INDEX = LighterCreateOrderRequest.DEFAULT_ACCOUNT_INDEX;
    public static final long DEFAULT_NONCE = LighterCreateOrderRequest.DEFAULT_NONCE;

    private int marketIndex;
    private long orderIndex;
    private long nonce = DEFAULT_NONCE;
    private int apiKeyIndex = DEFAULT_API_KEY_INDEX;
    private long accountIndex = DEFAULT_ACCOUNT_INDEX;

    public int getMarketIndex() {
        return marketIndex;
    }

    public void setMarketIndex(int marketIndex) {
        this.marketIndex = marketIndex;
    }

    public long getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(long orderIndex) {
        this.orderIndex = orderIndex;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    public int getApiKeyIndex() {
        return apiKeyIndex;
    }

    public void setApiKeyIndex(int apiKeyIndex) {
        this.apiKeyIndex = apiKeyIndex;
    }

    public long getAccountIndex() {
        return accountIndex;
    }

    public void setAccountIndex(long accountIndex) {
        this.accountIndex = accountIndex;
    }

    public void validate() {
        if (marketIndex < 0) {
            throw new IllegalArgumentException("marketIndex must be >= 0");
        }
        if (orderIndex < 0) {
            throw new IllegalArgumentException("orderIndex must be >= 0");
        }
    }
}
