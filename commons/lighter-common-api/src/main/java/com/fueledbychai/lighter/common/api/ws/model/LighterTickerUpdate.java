package com.fueledbychai.lighter.common.api.ws.model;

import java.math.BigDecimal;

public class LighterTickerUpdate {

    private final String channel;
    private final Long timestamp;
    private final Integer marketId;
    private BigDecimal bestBidPrice;
    private BigDecimal bestBidQuantity;
    private BigDecimal bestAskPrice;
    private BigDecimal bestAskQuantity;

    public LighterTickerUpdate(String channel, Long timestamp, Integer marketId) {
        this.channel = channel;
        this.timestamp = timestamp;
        this.marketId = marketId;
    }

    public String getChannel() {
        return channel;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public Integer getMarketId() {
        return marketId;
    }

    public BigDecimal getBestBidPrice() {
        return bestBidPrice;
    }

    public void setBestBidPrice(BigDecimal bestBidPrice) {
        this.bestBidPrice = bestBidPrice;
    }

    public BigDecimal getBestBidQuantity() {
        return bestBidQuantity;
    }

    public void setBestBidQuantity(BigDecimal bestBidQuantity) {
        this.bestBidQuantity = bestBidQuantity;
    }

    public BigDecimal getBestAskPrice() {
        return bestAskPrice;
    }

    public void setBestAskPrice(BigDecimal bestAskPrice) {
        this.bestAskPrice = bestAskPrice;
    }

    public BigDecimal getBestAskQuantity() {
        return bestAskQuantity;
    }

    public void setBestAskQuantity(BigDecimal bestAskQuantity) {
        this.bestAskQuantity = bestAskQuantity;
    }
}
