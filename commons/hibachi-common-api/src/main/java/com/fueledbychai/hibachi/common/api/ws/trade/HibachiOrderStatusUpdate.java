package com.fueledbychai.hibachi.common.api.ws.trade;

import java.math.BigDecimal;

import com.fueledbychai.hibachi.common.api.order.HibachiSide;

public class HibachiOrderStatusUpdate {

    private final String orderId;
    private final String clientOrderId;
    private final String symbol;
    private final HibachiSide side;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final BigDecimal filledQuantity;
    private final BigDecimal averageFillPrice;
    private final HibachiOrderStatus status;
    private final String reason;
    private final long timestamp;

    public HibachiOrderStatusUpdate(String orderId, String clientOrderId, String symbol, HibachiSide side,
                                    BigDecimal price, BigDecimal quantity, BigDecimal filledQuantity,
                                    BigDecimal averageFillPrice, HibachiOrderStatus status, String reason,
                                    long timestamp) {
        this.orderId = orderId;
        this.clientOrderId = clientOrderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.filledQuantity = filledQuantity;
        this.averageFillPrice = averageFillPrice;
        this.status = status;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public String getClientOrderId() { return clientOrderId; }
    public String getSymbol() { return symbol; }
    public HibachiSide getSide() { return side; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getFilledQuantity() { return filledQuantity; }
    public BigDecimal getAverageFillPrice() { return averageFillPrice; }
    public HibachiOrderStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }
}
