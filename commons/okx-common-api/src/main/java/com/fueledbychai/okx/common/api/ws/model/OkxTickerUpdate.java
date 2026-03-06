package com.fueledbychai.okx.common.api.ws.model;

import java.math.BigDecimal;

public class OkxTickerUpdate {

    protected final String instrumentId;
    protected final String instrumentType;
    protected final Long timestamp;
    protected final BigDecimal bestBidPrice;
    protected final BigDecimal bestBidSize;
    protected final BigDecimal bestAskPrice;
    protected final BigDecimal bestAskSize;
    protected final BigDecimal lastPrice;
    protected final BigDecimal lastSize;
    protected final BigDecimal markPrice;
    protected final BigDecimal openInterest;
    protected final BigDecimal volume;
    protected final BigDecimal volumeNotional;
    protected final BigDecimal fundingRate;
    protected final BigDecimal delta;
    protected final BigDecimal gamma;
    protected final BigDecimal theta;
    protected final BigDecimal vega;

    public OkxTickerUpdate(String instrumentId, String instrumentType, Long timestamp, BigDecimal bestBidPrice,
            BigDecimal bestBidSize, BigDecimal bestAskPrice, BigDecimal bestAskSize, BigDecimal lastPrice,
            BigDecimal lastSize, BigDecimal markPrice, BigDecimal openInterest, BigDecimal volume,
            BigDecimal volumeNotional, BigDecimal fundingRate, BigDecimal delta, BigDecimal gamma, BigDecimal theta,
            BigDecimal vega) {
        this.instrumentId = instrumentId;
        this.instrumentType = instrumentType;
        this.timestamp = timestamp;
        this.bestBidPrice = bestBidPrice;
        this.bestBidSize = bestBidSize;
        this.bestAskPrice = bestAskPrice;
        this.bestAskSize = bestAskSize;
        this.lastPrice = lastPrice;
        this.lastSize = lastSize;
        this.markPrice = markPrice;
        this.openInterest = openInterest;
        this.volume = volume;
        this.volumeNotional = volumeNotional;
        this.fundingRate = fundingRate;
        this.delta = delta;
        this.gamma = gamma;
        this.theta = theta;
        this.vega = vega;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public BigDecimal getBestBidPrice() {
        return bestBidPrice;
    }

    public BigDecimal getBestBidSize() {
        return bestBidSize;
    }

    public BigDecimal getBestAskPrice() {
        return bestAskPrice;
    }

    public BigDecimal getBestAskSize() {
        return bestAskSize;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public BigDecimal getLastSize() {
        return lastSize;
    }

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public BigDecimal getOpenInterest() {
        return openInterest;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public BigDecimal getVolumeNotional() {
        return volumeNotional;
    }

    public BigDecimal getFundingRate() {
        return fundingRate;
    }

    public BigDecimal getDelta() {
        return delta;
    }

    public BigDecimal getGamma() {
        return gamma;
    }

    public BigDecimal getTheta() {
        return theta;
    }

    public BigDecimal getVega() {
        return vega;
    }
}
