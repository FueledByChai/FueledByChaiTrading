package com.fueledbychai.drift.common.api.model;

import java.math.BigDecimal;

public class DriftGatewayOrder {

    private final String orderType;
    private final int marketIndex;
    private final DriftMarketType marketType;
    private final BigDecimal amount;
    private final BigDecimal filled;
    private final BigDecimal price;
    private final boolean postOnly;
    private final boolean reduceOnly;
    private final Integer userOrderId;
    private final Long orderId;
    private final BigDecimal oraclePriceOffset;
    private final String direction;

    public DriftGatewayOrder(String orderType, int marketIndex, DriftMarketType marketType, BigDecimal amount,
            BigDecimal filled, BigDecimal price, boolean postOnly, boolean reduceOnly, Integer userOrderId,
            Long orderId, BigDecimal oraclePriceOffset, String direction) {
        this.orderType = orderType;
        this.marketIndex = marketIndex;
        this.marketType = marketType;
        this.amount = amount;
        this.filled = filled;
        this.price = price;
        this.postOnly = postOnly;
        this.reduceOnly = reduceOnly;
        this.userOrderId = userOrderId;
        this.orderId = orderId;
        this.oraclePriceOffset = oraclePriceOffset;
        this.direction = direction;
    }

    public String getOrderType() {
        return orderType;
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

    public BigDecimal getFilled() {
        return filled;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isPostOnly() {
        return postOnly;
    }

    public boolean isReduceOnly() {
        return reduceOnly;
    }

    public Integer getUserOrderId() {
        return userOrderId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getOraclePriceOffset() {
        return oraclePriceOffset;
    }

    public String getDirection() {
        return direction;
    }
}
