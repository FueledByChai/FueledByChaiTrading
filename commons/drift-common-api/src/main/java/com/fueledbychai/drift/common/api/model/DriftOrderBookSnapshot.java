package com.fueledbychai.drift.common.api.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class DriftOrderBookSnapshot {

    private final String marketName;
    private final DriftMarketType marketType;
    private final int marketIndex;
    private final long timestampMillis;
    private final long slot;
    private final BigDecimal markPrice;
    private final BigDecimal bestBidPrice;
    private final BigDecimal bestAskPrice;
    private final BigDecimal oraclePrice;
    private final List<DriftOrderBookLevel> bids;
    private final List<DriftOrderBookLevel> asks;

    public DriftOrderBookSnapshot(String marketName, DriftMarketType marketType, int marketIndex, long timestampMillis,
            long slot, BigDecimal markPrice, BigDecimal bestBidPrice, BigDecimal bestAskPrice, BigDecimal oraclePrice,
            List<DriftOrderBookLevel> bids, List<DriftOrderBookLevel> asks) {
        this.marketName = marketName;
        this.marketType = marketType;
        this.marketIndex = marketIndex;
        this.timestampMillis = timestampMillis;
        this.slot = slot;
        this.markPrice = markPrice;
        this.bestBidPrice = bestBidPrice;
        this.bestAskPrice = bestAskPrice;
        this.oraclePrice = oraclePrice;
        this.bids = bids == null ? Collections.emptyList() : Collections.unmodifiableList(bids);
        this.asks = asks == null ? Collections.emptyList() : Collections.unmodifiableList(asks);
    }

    public String getMarketName() {
        return marketName;
    }

    public DriftMarketType getMarketType() {
        return marketType;
    }

    public int getMarketIndex() {
        return marketIndex;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public long getSlot() {
        return slot;
    }

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public BigDecimal getBestBidPrice() {
        return bestBidPrice;
    }

    public BigDecimal getBestAskPrice() {
        return bestAskPrice;
    }

    public BigDecimal getOraclePrice() {
        return oraclePrice;
    }

    public List<DriftOrderBookLevel> getBids() {
        return bids;
    }

    public List<DriftOrderBookLevel> getAsks() {
        return asks;
    }
}
