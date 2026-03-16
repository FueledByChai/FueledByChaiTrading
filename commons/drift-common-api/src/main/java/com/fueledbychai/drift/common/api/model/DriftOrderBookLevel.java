package com.fueledbychai.drift.common.api.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

public class DriftOrderBookLevel {

    private final BigDecimal price;
    private final BigDecimal size;
    private final Map<String, BigDecimal> sources;

    public DriftOrderBookLevel(BigDecimal price, BigDecimal size, Map<String, BigDecimal> sources) {
        this.price = price;
        this.size = size;
        this.sources = sources == null ? Collections.emptyMap() : Collections.unmodifiableMap(sources);
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getSize() {
        return size;
    }

    public Map<String, BigDecimal> getSources() {
        return sources;
    }
}
