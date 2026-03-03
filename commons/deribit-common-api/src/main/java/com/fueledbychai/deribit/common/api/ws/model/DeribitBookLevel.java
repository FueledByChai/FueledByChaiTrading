package com.fueledbychai.deribit.common.api.ws.model;

import java.math.BigDecimal;

public class DeribitBookLevel {

    protected final String action;
    protected final BigDecimal price;
    protected final BigDecimal amount;

    public DeribitBookLevel(String action, BigDecimal price, BigDecimal amount) {
        this.action = action;
        this.price = price;
        this.amount = amount;
    }

    public String getAction() {
        return action;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
