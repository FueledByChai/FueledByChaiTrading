package com.fueledbychai.lighter.common.api.ws;

import java.math.BigDecimal;
import java.util.Objects;

public class LighterOrderBookLevel {

    private final BigDecimal price;
    private final BigDecimal size;

    public LighterOrderBookLevel(BigDecimal price, BigDecimal size) {
        this.price = price;
        this.size = size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getSize() {
        return size;
    }

    @Override
    public int hashCode() {
        return Objects.hash(price, size);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LighterOrderBookLevel other)) {
            return false;
        }
        return Objects.equals(price, other.price) && Objects.equals(size, other.size);
    }

    @Override
    public String toString() {
        return "LighterOrderBookLevel{price=" + price + ", size=" + size + "}";
    }
}
