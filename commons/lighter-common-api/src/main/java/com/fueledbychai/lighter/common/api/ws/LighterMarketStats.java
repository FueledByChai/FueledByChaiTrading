package com.fueledbychai.lighter.common.api.ws;

import java.math.BigDecimal;

public class LighterMarketStats {

    private Integer marketId;
    private BigDecimal markPrice;
    private BigDecimal indexPrice;
    // Base-token units (e.g. BTC for BTC market)
    private BigDecimal dailyBaseVolume;
    // Quote-token notional (e.g. USDC)
    private BigDecimal dailyQuoteVolume;
    private BigDecimal openInterest;
    // Estimated upcoming funding payment rate (% per hour).
    private BigDecimal currentFundingRate;
    // Most recent settled funding payment rate (% per hour).
    private BigDecimal lastFundingRate;
    // Timestamp (epoch millis) when lastFundingRate was settled.
    private Long fundingTimestamp;
    private BigDecimal dailyLow;
    private BigDecimal dailyHigh;
    private BigDecimal dailyPriceChange24h;
    private BigDecimal dailyPriceChangePercent24h;
    private BigDecimal lastPrice;

    public Integer getMarketId() {
        return marketId;
    }

    public void setMarketId(Integer marketId) {
        this.marketId = marketId;
    }

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public void setMarkPrice(BigDecimal markPrice) {
        this.markPrice = markPrice;
    }

    public BigDecimal getIndexPrice() {
        return indexPrice;
    }

    public void setIndexPrice(BigDecimal indexPrice) {
        this.indexPrice = indexPrice;
    }

    public BigDecimal getDailyBaseVolume() {
        return dailyBaseVolume;
    }

    public void setDailyBaseVolume(BigDecimal dailyBaseVolume) {
        this.dailyBaseVolume = dailyBaseVolume;
    }

    public BigDecimal getDailyQuoteVolume() {
        return dailyQuoteVolume;
    }

    public void setDailyQuoteVolume(BigDecimal dailyQuoteVolume) {
        this.dailyQuoteVolume = dailyQuoteVolume;
    }

    public BigDecimal getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(BigDecimal openInterest) {
        this.openInterest = openInterest;
    }

    public BigDecimal getCurrentFundingRate() {
        return currentFundingRate;
    }

    public void setCurrentFundingRate(BigDecimal currentFundingRate) {
        this.currentFundingRate = currentFundingRate;
    }

    public BigDecimal getLastFundingRate() {
        return lastFundingRate;
    }

    public void setLastFundingRate(BigDecimal lastFundingRate) {
        this.lastFundingRate = lastFundingRate;
    }

    public Long getFundingTimestamp() {
        return fundingTimestamp;
    }

    public void setFundingTimestamp(Long fundingTimestamp) {
        this.fundingTimestamp = fundingTimestamp;
    }

    public BigDecimal getDailyLow() {
        return dailyLow;
    }

    public void setDailyLow(BigDecimal dailyLow) {
        this.dailyLow = dailyLow;
    }

    public BigDecimal getDailyHigh() {
        return dailyHigh;
    }

    public void setDailyHigh(BigDecimal dailyHigh) {
        this.dailyHigh = dailyHigh;
    }

    public BigDecimal getDailyPriceChange24h() {
        return dailyPriceChange24h;
    }

    public void setDailyPriceChange24h(BigDecimal dailyPriceChange24h) {
        this.dailyPriceChange24h = dailyPriceChange24h;
    }

    public BigDecimal getDailyPriceChangePercent24h() {
        return dailyPriceChangePercent24h;
    }

    public void setDailyPriceChangePercent24h(BigDecimal dailyPriceChangePercent24h) {
        this.dailyPriceChangePercent24h = dailyPriceChangePercent24h;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getDailyBaseVolumeUnits() {
        return dailyBaseVolume;
    }

    public BigDecimal getDailyQuoteVolumeNotional() {
        return dailyQuoteVolume;
    }
}
