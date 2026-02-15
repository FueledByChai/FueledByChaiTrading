package com.fueledbychai.lighter.common.api.order;

/**
 * Low-level request for Lighter create-order signing.
 *
 * Numeric fields are encoded in Lighter's native integer precision expected by
 * the signer.
 */
public class LighterCreateOrderRequest {

    public static final int DEFAULT_API_KEY_INDEX = 255;
    public static final long DEFAULT_ACCOUNT_INDEX = 0L;
    public static final long DEFAULT_NONCE = -1L;
    public static final long DEFAULT_ORDER_EXPIRY = -1L;
    public static final int DEFAULT_TRIGGER_PRICE = 0;

    private int marketIndex;
    private long clientOrderIndex;
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

    public long getClientOrderIndex() {
        return clientOrderIndex;
    }

    public void setClientOrderIndex(long clientOrderIndex) {
        this.clientOrderIndex = clientOrderIndex;
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
        if (clientOrderIndex < 0) {
            throw new IllegalArgumentException("clientOrderIndex must be >= 0");
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

    public static LighterCreateOrderRequest marketOrder(int marketIndex, long clientOrderIndex, long baseAmount,
            int price, boolean isAsk) {
        LighterCreateOrderRequest request = new LighterCreateOrderRequest();
        request.setMarketIndex(marketIndex);
        request.setClientOrderIndex(clientOrderIndex);
        request.setBaseAmount(baseAmount);
        request.setPrice(price);
        request.setAsk(isAsk);
        request.setOrderType(LighterOrderType.MARKET);
        request.setTimeInForce(LighterTimeInForce.IOC);
        return request;
    }
}
