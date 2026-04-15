package com.fueledbychai.hibachi.common.api.ws.market;

import java.math.BigDecimal;

public class HibachiMarkPriceUpdate {

    private final String symbol;
    private final BigDecimal markPrice;
    private final long timestamp;

    public HibachiMarkPriceUpdate(String symbol, BigDecimal markPrice, long timestamp) {
        this.symbol = symbol;
        this.markPrice = markPrice;
        this.timestamp = timestamp;
    }

    public String getSymbol() { return symbol; }
    public BigDecimal getMarkPrice() { return markPrice; }
    public long getTimestamp() { return timestamp; }
}
