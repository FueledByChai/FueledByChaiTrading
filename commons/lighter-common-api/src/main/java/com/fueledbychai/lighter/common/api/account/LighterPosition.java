package com.fueledbychai.lighter.common.api.account;

import java.math.BigDecimal;

import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;

public class LighterPosition {

    public enum Status {
        OPEN, CLOSED
    }

    protected Ticker ticker;
    protected BigDecimal size;
    protected BigDecimal averageEntryPrice;
    protected BigDecimal liquidationPrice;
    protected Side side;
    protected Status status;

    public LighterPosition() {
    }

    public LighterPosition(Ticker ticker) {
        this.ticker = ticker;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public LighterPosition setTicker(Ticker ticker) {
        this.ticker = ticker;
        return this;
    }

    public BigDecimal getSize() {
        return size;
    }

    public LighterPosition setSize(BigDecimal size) {
        this.size = size;
        return this;
    }

    public BigDecimal getAverageEntryPrice() {
        return averageEntryPrice;
    }

    public LighterPosition setAverageEntryPrice(BigDecimal averageEntryPrice) {
        this.averageEntryPrice = averageEntryPrice;
        return this;
    }

    public BigDecimal getLiquidationPrice() {
        return liquidationPrice;
    }

    public LighterPosition setLiquidationPrice(BigDecimal liquidationPrice) {
        this.liquidationPrice = liquidationPrice;
        return this;
    }

    public Side getSide() {
        return side;
    }

    public LighterPosition setSide(Side side) {
        this.side = side;
        return this;
    }

    public Status getStatus() {
        return status;
    }

    public LighterPosition setStatus(Status status) {
        this.status = status;
        return this;
    }

    @Override
    public String toString() {
        return "LighterPosition [ticker=" + ticker + ", size=" + size + ", averageEntryPrice=" + averageEntryPrice
                + ", liquidationPrice=" + liquidationPrice + ", side=" + side + ", status=" + status + "]";
    }
}
