package com.fueledbychai.binance.ws.partialbook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a Binance order book snapshot. This corresponds to the REST API
 * /api/v3/depth endpoint or similar orderbook data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBookSnapshot {

    @JsonProperty("lastUpdateId")
    private long lastUpdateId;

    @JsonProperty("bids")
    private List<PriceLevel> bids;

    @JsonProperty("asks")
    private List<PriceLevel> asks;

    // Default constructor for Jackson
    public OrderBookSnapshot() {
    }

    public long getLastUpdateId() {
        return lastUpdateId;
    }

    public void setLastUpdateId(long lastUpdateId) {
        this.lastUpdateId = lastUpdateId;
    }

    public List<PriceLevel> getBids() {
        return bids;
    }

    public void setBids(List<PriceLevel> bids) {
        this.bids = bids;
    }

    public List<PriceLevel> getAsks() {
        return asks;
    }

    public void setAsks(List<PriceLevel> asks) {
        this.asks = asks;
    }

    /**
     * Get the best bid price (highest bid).
     */
    public PriceLevel getBestBid() {
        return bids != null && !bids.isEmpty() ? bids.get(0) : null;
    }

    /**
     * Get the best ask price (lowest ask).
     */
    public PriceLevel getBestAsk() {
        return asks != null && !asks.isEmpty() ? asks.get(0) : null;
    }

    /**
     * Calculate the spread between best bid and best ask.
     */
    public String getSpread() {
        PriceLevel bestBid = getBestBid();
        PriceLevel bestAsk = getBestAsk();

        if (bestBid == null || bestAsk == null) {
            return null;
        }

        return bestAsk.getPriceAsDecimal().subtract(bestBid.getPriceAsDecimal()).toPlainString();
    }

    /**
     * Get the mid-point price between best bid and best ask.
     */
    public String getMidPrice() {
        PriceLevel bestBid = getBestBid();
        PriceLevel bestAsk = getBestAsk();

        if (bestBid == null || bestAsk == null) {
            return null;
        }

        return bestBid.getPriceAsDecimal().add(bestAsk.getPriceAsDecimal()).divide(new java.math.BigDecimal("2"))
                .toPlainString();
    }

    @Override
    public String toString() {
        return "OrderBookSnapshot{" + "lastUpdateId=" + lastUpdateId + ", bids=" + bids + ", asks=" + asks + '}';
    }
}
