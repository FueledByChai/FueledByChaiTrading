package com.fueledbychai.deribit.common.api.ws.model;

import java.math.BigDecimal;

public class DeribitTickerUpdate {

    protected final String instrumentName;
    protected final Long timestamp;
    protected final BigDecimal bestBidPrice;
    protected final BigDecimal bestBidAmount;
    protected final BigDecimal bestAskPrice;
    protected final BigDecimal bestAskAmount;
    protected final BigDecimal lastPrice;
    protected final BigDecimal markPrice;
    protected final BigDecimal openInterest;
    protected final BigDecimal volume;
    protected final BigDecimal volumeNotional;
    protected final BigDecimal underlyingPrice;
    protected final BigDecimal currentFunding;
    protected final BigDecimal bidIv;
    protected final BigDecimal askIv;
    protected final BigDecimal markIv;
    protected final BigDecimal delta;
    protected final BigDecimal gamma;
    protected final BigDecimal theta;
    protected final BigDecimal vega;
    protected final BigDecimal rho;
    protected final BigDecimal interestRate;

    public DeribitTickerUpdate(String instrumentName, Long timestamp, BigDecimal bestBidPrice, BigDecimal bestBidAmount,
            BigDecimal bestAskPrice, BigDecimal bestAskAmount, BigDecimal lastPrice, BigDecimal markPrice,
            BigDecimal openInterest, BigDecimal volume, BigDecimal volumeNotional, BigDecimal underlyingPrice,
            BigDecimal currentFunding, BigDecimal bidIv, BigDecimal askIv, BigDecimal markIv, BigDecimal delta,
            BigDecimal gamma, BigDecimal theta, BigDecimal vega, BigDecimal rho, BigDecimal interestRate) {
        this.instrumentName = instrumentName;
        this.timestamp = timestamp;
        this.bestBidPrice = bestBidPrice;
        this.bestBidAmount = bestBidAmount;
        this.bestAskPrice = bestAskPrice;
        this.bestAskAmount = bestAskAmount;
        this.lastPrice = lastPrice;
        this.markPrice = markPrice;
        this.openInterest = openInterest;
        this.volume = volume;
        this.volumeNotional = volumeNotional;
        this.underlyingPrice = underlyingPrice;
        this.currentFunding = currentFunding;
        this.bidIv = bidIv;
        this.askIv = askIv;
        this.markIv = markIv;
        this.delta = delta;
        this.gamma = gamma;
        this.theta = theta;
        this.vega = vega;
        this.rho = rho;
        this.interestRate = interestRate;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public BigDecimal getBestBidPrice() {
        return bestBidPrice;
    }

    public BigDecimal getBestBidAmount() {
        return bestBidAmount;
    }

    public BigDecimal getBestAskPrice() {
        return bestAskPrice;
    }

    public BigDecimal getBestAskAmount() {
        return bestAskAmount;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
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

    public BigDecimal getUnderlyingPrice() {
        return underlyingPrice;
    }

    public BigDecimal getCurrentFunding() {
        return currentFunding;
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

    public BigDecimal getRho() {
        return rho;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }
}
