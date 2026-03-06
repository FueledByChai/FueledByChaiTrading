package com.fueledbychai.okx.common.api.ws.model;

import java.math.BigDecimal;

public class OkxTrade {

    protected final String instrumentId;
    protected final String tradeId;
    protected final String side;
    protected final BigDecimal price;
    protected final BigDecimal size;
    protected final Long timestamp;

    public OkxTrade(String instrumentId, String tradeId, String side, BigDecimal price, BigDecimal size,
            Long timestamp) {
        this.instrumentId = instrumentId;
        this.tradeId = tradeId;
        this.side = side;
        this.price = price;
        this.size = size;
        this.timestamp = timestamp;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public String getTradeId() {
        return tradeId;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getSize() {
        return size;
    }

    public Long getTimestamp() {
        return timestamp;
    }
}
