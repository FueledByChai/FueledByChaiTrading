package com.fueledbychai.bybit.common.api.ws.model;

import java.math.BigDecimal;

public class BybitTrade {

    protected final String instrumentId;
    protected final String tradeId;
    protected final String side;
    protected final BigDecimal price;
    protected final BigDecimal size;
    protected final Long timestamp;
    protected final BigDecimal markPrice;
    protected final BigDecimal indexPrice;
    protected final BigDecimal markIv;
    protected final BigDecimal iv;

    public BybitTrade(String instrumentId, String tradeId, String side, BigDecimal price, BigDecimal size,
            Long timestamp, BigDecimal markPrice, BigDecimal indexPrice, BigDecimal markIv, BigDecimal iv) {
        this.instrumentId = instrumentId;
        this.tradeId = tradeId;
        this.side = side;
        this.price = price;
        this.size = size;
        this.timestamp = timestamp;
        this.markPrice = markPrice;
        this.indexPrice = indexPrice;
        this.markIv = markIv;
        this.iv = iv;
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

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public BigDecimal getIndexPrice() {
        return indexPrice;
    }

    public BigDecimal getMarkIv() {
        return markIv;
    }

    public BigDecimal getIv() {
        return iv;
    }
}
