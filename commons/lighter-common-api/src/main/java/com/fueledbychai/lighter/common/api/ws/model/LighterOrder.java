package com.fueledbychai.lighter.common.api.ws.model;

import java.math.BigDecimal;

public class LighterOrder {

    private Long orderIndex;
    private Long clientOrderIndex;
    private String orderId;
    private String clientOrderId;
    private Integer marketIndex;
    private Long ownerAccountIndex;
    private BigDecimal initialBaseAmount;
    private BigDecimal price;
    private Long nonce;
    private BigDecimal remainingBaseAmount;
    private Boolean ask;
    private Long baseSize;
    private Long basePrice;
    private BigDecimal filledBaseAmount;
    private BigDecimal filledQuoteAmount;
    private String side;
    private String type;
    private String timeInForce;
    private Boolean reduceOnly;
    private BigDecimal triggerPrice;
    private Long orderExpiry;
    private String status;
    private String triggerStatus;
    private Long triggerTime;
    private Long parentOrderIndex;
    private String parentOrderId;
    private String toTriggerOrderId0;
    private String toTriggerOrderId1;
    private String toCancelOrderId0;
    private Long blockHeight;
    private Long timestamp;
    private Long createdAt;
    private Long updatedAt;
    private Long transactionTime;

    public Long getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Long orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Long getClientOrderIndex() {
        return clientOrderIndex;
    }

    public void setClientOrderIndex(Long clientOrderIndex) {
        this.clientOrderIndex = clientOrderIndex;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public Integer getMarketIndex() {
        return marketIndex;
    }

    public void setMarketIndex(Integer marketIndex) {
        this.marketIndex = marketIndex;
    }

    public Long getOwnerAccountIndex() {
        return ownerAccountIndex;
    }

    public void setOwnerAccountIndex(Long ownerAccountIndex) {
        this.ownerAccountIndex = ownerAccountIndex;
    }

    public BigDecimal getInitialBaseAmount() {
        return initialBaseAmount;
    }

    public void setInitialBaseAmount(BigDecimal initialBaseAmount) {
        this.initialBaseAmount = initialBaseAmount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getNonce() {
        return nonce;
    }

    public void setNonce(Long nonce) {
        this.nonce = nonce;
    }

    public BigDecimal getRemainingBaseAmount() {
        return remainingBaseAmount;
    }

    public void setRemainingBaseAmount(BigDecimal remainingBaseAmount) {
        this.remainingBaseAmount = remainingBaseAmount;
    }

    public Boolean getAsk() {
        return ask;
    }

    public void setAsk(Boolean ask) {
        this.ask = ask;
    }

    public Long getBaseSize() {
        return baseSize;
    }

    public void setBaseSize(Long baseSize) {
        this.baseSize = baseSize;
    }

    public Long getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(Long basePrice) {
        this.basePrice = basePrice;
    }

    public BigDecimal getFilledBaseAmount() {
        return filledBaseAmount;
    }

    public void setFilledBaseAmount(BigDecimal filledBaseAmount) {
        this.filledBaseAmount = filledBaseAmount;
    }

    public BigDecimal getFilledQuoteAmount() {
        return filledQuoteAmount;
    }

    public void setFilledQuoteAmount(BigDecimal filledQuoteAmount) {
        this.filledQuoteAmount = filledQuoteAmount;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public Boolean getReduceOnly() {
        return reduceOnly;
    }

    public void setReduceOnly(Boolean reduceOnly) {
        this.reduceOnly = reduceOnly;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public Long getOrderExpiry() {
        return orderExpiry;
    }

    public void setOrderExpiry(Long orderExpiry) {
        this.orderExpiry = orderExpiry;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTriggerStatus() {
        return triggerStatus;
    }

    public void setTriggerStatus(String triggerStatus) {
        this.triggerStatus = triggerStatus;
    }

    public Long getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(Long triggerTime) {
        this.triggerTime = triggerTime;
    }

    public Long getParentOrderIndex() {
        return parentOrderIndex;
    }

    public void setParentOrderIndex(Long parentOrderIndex) {
        this.parentOrderIndex = parentOrderIndex;
    }

    public String getParentOrderId() {
        return parentOrderId;
    }

    public void setParentOrderId(String parentOrderId) {
        this.parentOrderId = parentOrderId;
    }

    public String getToTriggerOrderId0() {
        return toTriggerOrderId0;
    }

    public void setToTriggerOrderId0(String toTriggerOrderId0) {
        this.toTriggerOrderId0 = toTriggerOrderId0;
    }

    public String getToTriggerOrderId1() {
        return toTriggerOrderId1;
    }

    public void setToTriggerOrderId1(String toTriggerOrderId1) {
        this.toTriggerOrderId1 = toTriggerOrderId1;
    }

    public String getToCancelOrderId0() {
        return toCancelOrderId0;
    }

    public void setToCancelOrderId0(String toCancelOrderId0) {
        this.toCancelOrderId0 = toCancelOrderId0;
    }

    public Long getBlockHeight() {
        return blockHeight;
    }

    public void setBlockHeight(Long blockHeight) {
        this.blockHeight = blockHeight;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getTransactionTime() {
        return transactionTime;
    }

    public void setTransactionTime(Long transactionTime) {
        this.transactionTime = transactionTime;
    }
}
