package com.fueledbychai.deribit.common.api.ws.model;

import java.math.BigDecimal;

public class DeribitTrade {

    protected final String instrumentName;
    protected final String direction;
    protected final BigDecimal price;
    protected final BigDecimal amount;
    protected final Long timestamp;

    public DeribitTrade(String instrumentName, String direction, BigDecimal price, BigDecimal amount, Long timestamp) {
        this.instrumentName = instrumentName;
        this.direction = direction;
        this.price = price;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public String getDirection() {
        return direction;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getTimestamp() {
        return timestamp;
    }
}
