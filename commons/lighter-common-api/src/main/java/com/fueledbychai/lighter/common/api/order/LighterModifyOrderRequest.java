package com.fueledbychai.lighter.common.api.order;

/**
 * Low-level request for Lighter modify-order signing.
 */
public class LighterModifyOrderRequest {

    public static final int DEFAULT_API_KEY_INDEX = LighterCreateOrderRequest.DEFAULT_API_KEY_INDEX;
    public static final long DEFAULT_ACCOUNT_INDEX = LighterCreateOrderRequest.DEFAULT_ACCOUNT_INDEX;
    public static final long DEFAULT_NONCE = LighterCreateOrderRequest.DEFAULT_NONCE;
    public static final long DEFAULT_ORDER_EXPIRY = LighterCreateOrderRequest.DEFAULT_ORDER_EXPIRY;
    public static final int DEFAULT_TRIGGER_PRICE = LighterCreateOrderRequest.DEFAULT_TRIGGER_PRICE;

    private int marketIndex;
    private long orderIndex;
    private long baseAmount;
    private int price;
    private boolean ask;
    private LighterOrderType orderType = LighterOrderType.LIMIT;
    private LighterTimeInForce timeInForce = LighterTimeInForce.GTT;
    private boolean reduceOnly;
    private int triggerPrice = DEFAULT_TRIGGER_PRICE;
    private long orderExpiry = DEFAULT_ORDER_EXPIRY;
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

    public long getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(long baseAmount) {
        this.baseAmount = baseAmount;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public boolean isAsk() {
        return ask;
    }

    public void setAsk(boolean ask) {
        this.ask = ask;
    }

    public LighterOrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(LighterOrderType orderType) {
        if (orderType == null) {
            throw new IllegalArgumentException("orderType is required");
        }
        this.orderType = orderType;
    }

    public LighterTimeInForce getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(LighterTimeInForce timeInForce) {
        if (timeInForce == null) {
            throw new IllegalArgumentException("timeInForce is required");
        }
        this.timeInForce = timeInForce;
    }

    public boolean isReduceOnly() {
        return reduceOnly;
    }

    public void setReduceOnly(boolean reduceOnly) {
        this.reduceOnly = reduceOnly;
    }

    public int getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(int triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public long getOrderExpiry() {
        return orderExpiry;
    }

    public void setOrderExpiry(long orderExpiry) {
        this.orderExpiry = orderExpiry;
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
        if (baseAmount <= 0) {
            throw new IllegalArgumentException("baseAmount must be > 0");
        }
        if (price < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("orderType is required");
        }
        if (timeInForce == null) {
            throw new IllegalArgumentException("timeInForce is required");
        }
    }
}
