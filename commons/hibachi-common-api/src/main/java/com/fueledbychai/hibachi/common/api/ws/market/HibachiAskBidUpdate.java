package com.fueledbychai.hibachi.common.api.ws.market;

import java.math.BigDecimal;

public class HibachiAskBidUpdate {

    private final String symbol;
    private final BigDecimal bidPrice;
    private final BigDecimal bidSize;
    private final BigDecimal askPrice;
    private final BigDecimal askSize;
    private final long timestamp;

    public HibachiAskBidUpdate(String symbol, BigDecimal bidPrice, BigDecimal bidSize,
                               BigDecimal askPrice, BigDecimal askSize, long timestamp) {
        this.symbol = symbol;
        this.bidPrice = bidPrice;
        this.bidSize = bidSize;
        this.askPrice = askPrice;
        this.askSize = askSize;
        this.timestamp = timestamp;
    }

    public String getSymbol() { return symbol; }
    public BigDecimal getBidPrice() { return bidPrice; }
    public BigDecimal getBidSize() { return bidSize; }
    public BigDecimal getAskPrice() { return askPrice; }
    public BigDecimal getAskSize() { return askSize; }
    public long getTimestamp() { return timestamp; }
}
