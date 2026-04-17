package com.fueledbychai.hibachi.common.api.ws.market;

import java.math.BigDecimal;

import com.fueledbychai.hibachi.common.api.order.HibachiSide;

public class HibachiTradeUpdate {

    private final String symbol;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final HibachiSide side;
    private final long timestamp;
    private final String tradeId;

    public HibachiTradeUpdate(String symbol, BigDecimal price, BigDecimal quantity,
                              HibachiSide side, long timestamp, String tradeId) {
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.side = side;
        this.timestamp = timestamp;
        this.tradeId = tradeId;
    }

    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public HibachiSide getSide() { return side; }
    public long getTimestamp() { return timestamp; }
    public String getTradeId() { return tradeId; }
}
