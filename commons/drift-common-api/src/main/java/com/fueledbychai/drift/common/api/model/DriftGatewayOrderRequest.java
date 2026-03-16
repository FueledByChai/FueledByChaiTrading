package com.fueledbychai.drift.common.api.model;

import java.math.BigDecimal;

public class DriftGatewayOrderRequest {

    private final int marketIndex;
    private final DriftMarketType marketType;
    private final BigDecimal amount;
    private final BigDecimal price;
    private final boolean postOnly;
    private final String orderType;
    private final Integer userOrderId;
    private final boolean reduceOnly;
    private final Long maxTs;
    private final Long orderId;
    private final BigDecimal oraclePriceOffset;

    public DriftGatewayOrderRequest(int marketIndex, DriftMarketType marketType, BigDecimal amount, BigDecimal price,
            boolean postOnly, String orderType, Integer userOrderId, boolean reduceOnly, Long maxTs, Long orderId,
            BigDecimal oraclePriceOffset) {
        this.marketIndex = marketIndex;
        this.marketType = marketType;
        this.amount = amount;
        this.price = price;
        this.postOnly = postOnly;
        this.orderType = orderType;
        this.userOrderId = userOrderId;
        this.reduceOnly = reduceOnly;
        this.maxTs = maxTs;
        this.orderId = orderId;
        this.oraclePriceOffset = oraclePriceOffset;
    }

    public int getMarketIndex() {
        return marketIndex;
    }

    public DriftMarketType getMarketType() {
        return marketType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isPostOnly() {
        return postOnly;
    }

    public String getOrderType() {
        return orderType;
    }

    public Integer getUserOrderId() {
        return userOrderId;
    }

    public boolean isReduceOnly() {
        return reduceOnly;
    }

    public Long getMaxTs() {
        return maxTs;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getOraclePriceOffset() {
        return oraclePriceOffset;
    }
}
