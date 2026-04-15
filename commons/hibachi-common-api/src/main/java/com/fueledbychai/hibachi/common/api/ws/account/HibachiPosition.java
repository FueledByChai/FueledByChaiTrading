package com.fueledbychai.hibachi.common.api.ws.account;

import java.math.BigDecimal;

public class HibachiPosition {

    public enum Direction { LONG, SHORT }

    private final String symbol;
    private final Direction direction;
    private final BigDecimal quantity;
    private final BigDecimal openPrice;
    private final BigDecimal markPrice;
    private final BigDecimal entryNotional;
    private final BigDecimal notionalValue;
    private final BigDecimal unrealizedTradingPnl;
    private final BigDecimal unrealizedFundingPnl;

    public HibachiPosition(String symbol, Direction direction, BigDecimal quantity, BigDecimal openPrice,
                           BigDecimal markPrice, BigDecimal entryNotional, BigDecimal notionalValue,
                           BigDecimal unrealizedTradingPnl, BigDecimal unrealizedFundingPnl) {
        this.symbol = symbol;
        this.direction = direction;
        this.quantity = quantity;
        this.openPrice = openPrice;
        this.markPrice = markPrice;
        this.entryNotional = entryNotional;
        this.notionalValue = notionalValue;
        this.unrealizedTradingPnl = unrealizedTradingPnl;
        this.unrealizedFundingPnl = unrealizedFundingPnl;
    }

    public String getSymbol() { return symbol; }
    public Direction getDirection() { return direction; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getMarkPrice() { return markPrice; }
    public BigDecimal getEntryNotional() { return entryNotional; }
    public BigDecimal getNotionalValue() { return notionalValue; }
    public BigDecimal getUnrealizedTradingPnl() { return unrealizedTradingPnl; }
    public BigDecimal getUnrealizedFundingPnl() { return unrealizedFundingPnl; }
}
