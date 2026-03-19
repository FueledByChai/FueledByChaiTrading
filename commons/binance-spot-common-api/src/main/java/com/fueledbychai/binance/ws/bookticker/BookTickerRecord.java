package com.fueledbychai.binance.ws.bookticker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BookTickerRecord {

    @JsonProperty("E")
    private long eventTime;

    @JsonProperty("u")
    private long updateId;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("b")
    private String bestBidPrice;

    @JsonProperty("B")
    private String bestBidQty;

    @JsonProperty("a")
    private String bestAskPrice;

    @JsonProperty("A")
    private String bestAskQty;

    public BookTickerRecord() {
    }

    public long getEventTime() {
        return eventTime;
    }

    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }

    public long getUpdateId() {
        return updateId;
    }

    public void setUpdateId(long updateId) {
        this.updateId = updateId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getBestBidPrice() {
        return bestBidPrice;
    }

    public void setBestBidPrice(String bestBidPrice) {
        this.bestBidPrice = bestBidPrice;
    }

    public String getBestBidQty() {
        return bestBidQty;
    }

    public void setBestBidQty(String bestBidQty) {
        this.bestBidQty = bestBidQty;
    }

    public String getBestAskPrice() {
        return bestAskPrice;
    }

    public void setBestAskPrice(String bestAskPrice) {
        this.bestAskPrice = bestAskPrice;
    }

    public String getBestAskQty() {
        return bestAskQty;
    }

    public void setBestAskQty(String bestAskQty) {
        this.bestAskQty = bestAskQty;
    }
}
