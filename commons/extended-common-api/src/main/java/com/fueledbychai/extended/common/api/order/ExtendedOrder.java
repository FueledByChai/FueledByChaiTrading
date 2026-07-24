package com.fueledbychai.extended.common.api.order;

import java.math.BigDecimal;

import com.google.gson.annotations.SerializedName;

/**
 * Data-transfer object for an Extended order. Carries both the fields needed to
 * submit a {@code POST /user/order} request and the fields returned when
 * reading orders back (REST {@code /user/orders} or the order WebSocket stream).
 */
public class ExtendedOrder {

    @SerializedName("id")
    private String id;

    @SerializedName("externalId")
    private String externalId;

    @SerializedName("market")
    private String market;

    @SerializedName("type")
    private OrderType type = OrderType.LIMIT;

    @SerializedName("side")
    private Side side;

    @SerializedName("qty")
    private BigDecimal qty;

    @SerializedName("price")
    private BigDecimal price;

    @SerializedName("timeInForce")
    private TimeInForce timeInForce = TimeInForce.GTT;

    @SerializedName("reduceOnly")
    private boolean reduceOnly;

    @SerializedName("postOnly")
    private boolean postOnly;

    @SerializedName("expiryEpochMillis")
    private long expiryEpochMillis;

    /** Taker fee rate applied when computing the signed fee amount (e.g. 0.0005). */
    @SerializedName("fee")
    private BigDecimal fee;

    // ---- response-only fields ----

    @SerializedName("status")
    private ExtendedOrderStatus status;

    @SerializedName("filledQty")
    private BigDecimal filledQty;

    @SerializedName("averagePrice")
    private BigDecimal averagePrice;

    @SerializedName("createdTime")
    private long createdTime;

    @SerializedName("updatedTime")
    private long updatedTime;

    public ExtendedOrder() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public TimeInForce getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(TimeInForce timeInForce) {
        this.timeInForce = timeInForce;
    }

    public boolean isReduceOnly() {
        return reduceOnly;
    }

    public void setReduceOnly(boolean reduceOnly) {
        this.reduceOnly = reduceOnly;
    }

    public boolean isPostOnly() {
        return postOnly;
    }

    public void setPostOnly(boolean postOnly) {
        this.postOnly = postOnly;
    }

    public long getExpiryEpochMillis() {
        return expiryEpochMillis;
    }

    public void setExpiryEpochMillis(long expiryEpochMillis) {
        this.expiryEpochMillis = expiryEpochMillis;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public ExtendedOrderStatus getStatus() {
        return status;
    }

    public void setStatus(ExtendedOrderStatus status) {
        this.status = status;
    }

    public BigDecimal getFilledQty() {
        return filledQty;
    }

    public void setFilledQty(BigDecimal filledQty) {
        this.filledQty = filledQty;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(BigDecimal averagePrice) {
        this.averagePrice = averagePrice;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(long updatedTime) {
        this.updatedTime = updatedTime;
    }

    @Override
    public String toString() {
        return "ExtendedOrder{id=" + id + ", externalId=" + externalId + ", market=" + market + ", type=" + type
                + ", side=" + side + ", qty=" + qty + ", price=" + price + ", tif=" + timeInForce + ", status=" + status
                + "}";
    }
}
