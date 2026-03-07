package com.fueledbychai.bybit.common.api.ws.model;

import java.math.BigDecimal;

public class BybitTickerUpdate {

    protected final String instrumentId;
    protected final String category;
    protected final Long timestamp;

    protected final BigDecimal bestBidPrice;
    protected final BigDecimal bestBidSize;
    protected final BigDecimal bestAskPrice;
    protected final BigDecimal bestAskSize;

    protected final BigDecimal lastPrice;
    protected final BigDecimal lastSize;
    protected final BigDecimal markPrice;
    protected final BigDecimal indexPrice;
    protected final BigDecimal underlyingPrice;

    protected final BigDecimal openInterest;
    protected final BigDecimal volume;
    protected final BigDecimal volumeNotional;
    protected final BigDecimal fundingRate;

    protected final BigDecimal bidIv;
    protected final BigDecimal askIv;
    protected final BigDecimal markIv;
    protected final BigDecimal delta;
    protected final BigDecimal gamma;
    protected final BigDecimal theta;
    protected final BigDecimal vega;

    public BybitTickerUpdate(String instrumentId, String category, Long timestamp, BigDecimal bestBidPrice,
            BigDecimal bestBidSize, BigDecimal bestAskPrice, BigDecimal bestAskSize, BigDecimal lastPrice,
            BigDecimal lastSize, BigDecimal markPrice, BigDecimal indexPrice, BigDecimal underlyingPrice,
            BigDecimal openInterest, BigDecimal volume, BigDecimal volumeNotional, BigDecimal fundingRate,
            BigDecimal bidIv, BigDecimal askIv, BigDecimal markIv, BigDecimal delta, BigDecimal gamma,
            BigDecimal theta, BigDecimal vega) {
        this.instrumentId = instrumentId;
        this.category = category;
        this.timestamp = timestamp;
        this.bestBidPrice = bestBidPrice;
        this.bestBidSize = bestBidSize;
        this.bestAskPrice = bestAskPrice;
        this.bestAskSize = bestAskSize;
        this.lastPrice = lastPrice;
        this.lastSize = lastSize;
        this.markPrice = markPrice;
        this.indexPrice = indexPrice;
        this.underlyingPrice = underlyingPrice;
        this.openInterest = openInterest;
        this.volume = volume;
        this.volumeNotional = volumeNotional;
        this.fundingRate = fundingRate;
        this.bidIv = bidIv;
        this.askIv = askIv;
        this.markIv = markIv;
        this.delta = delta;
        this.gamma = gamma;
        this.theta = theta;
        this.vega = vega;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public String getCategory() {
        return category;
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

    public BigDecimal getIndexPrice() {
        return indexPrice;
    }

    public BigDecimal getUnderlyingPrice() {
        return underlyingPrice;
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

    public BigDecimal getBidIv() {
        return bidIv;
    }

    public BigDecimal getAskIv() {
        return askIv;
    }

    public BigDecimal getMarkIv() {
        return markIv;
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
