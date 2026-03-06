package com.fueledbychai.okx.common.api.ws.model;

import java.math.BigDecimal;

public class OkxOrderBookLevel {

    protected final BigDecimal price;
    protected final BigDecimal size;
    protected final Integer orderCount;

    public OkxOrderBookLevel(BigDecimal price, BigDecimal size, Integer orderCount) {
        this.price = price;
        this.size = size;
        this.orderCount = orderCount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getSize() {
        return size;
    }

    public Integer getOrderCount() {
        return orderCount;
    }
}
