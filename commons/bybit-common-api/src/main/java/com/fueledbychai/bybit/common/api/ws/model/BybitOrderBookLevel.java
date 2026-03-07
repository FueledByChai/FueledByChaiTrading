package com.fueledbychai.bybit.common.api.ws.model;

import java.math.BigDecimal;

public class BybitOrderBookLevel {

    protected final BigDecimal price;
    protected final BigDecimal size;

    public BybitOrderBookLevel(BigDecimal price, BigDecimal size) {
        this.price = price;
        this.size = size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getSize() {
        return size;
    }
}
