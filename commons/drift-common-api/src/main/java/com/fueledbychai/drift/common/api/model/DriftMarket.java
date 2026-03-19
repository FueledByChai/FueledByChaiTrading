package com.fueledbychai.drift.common.api.model;

import java.math.BigDecimal;

public class DriftMarket {

    private final String symbol;
    private final int marketIndex;
    private final DriftMarketType marketType;
    private final String baseAsset;
    private final String quoteAsset;
    private final String status;
    private final int precision;
    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;
    private final BigDecimal minLeverage;
    private final BigDecimal maxLeverage;
    private final BigDecimal makerFee;
    private final BigDecimal takerFee;
    private final BigDecimal oraclePrice;
    private final BigDecimal markPrice;
    private final BigDecimal lastPrice;
    private final BigDecimal baseVolume;
    private final BigDecimal quoteVolume;
    private final BigDecimal openInterestLong;
    private final BigDecimal openInterestShort;
    private final BigDecimal fundingRateLong;
    private final BigDecimal fundingRateShort;
    private final BigDecimal fundingRate24h;
    private final Long fundingRateUpdateTs;
    private final BigDecimal priceChange24h;
    private final BigDecimal priceChange24hPercent;
    private final BigDecimal priceHigh;
    private final BigDecimal priceLow;

    public DriftMarket(String symbol, int marketIndex, DriftMarketType marketType, String baseAsset, String quoteAsset,
            String status, int precision, BigDecimal minAmount, BigDecimal maxAmount, BigDecimal minLeverage,
            BigDecimal maxLeverage, BigDecimal makerFee, BigDecimal takerFee, BigDecimal oraclePrice,
            BigDecimal markPrice, BigDecimal lastPrice, BigDecimal baseVolume, BigDecimal quoteVolume,
            BigDecimal openInterestLong, BigDecimal openInterestShort, BigDecimal fundingRateLong,
            BigDecimal fundingRateShort, BigDecimal fundingRate24h, Long fundingRateUpdateTs,
            BigDecimal priceChange24h, BigDecimal priceChange24hPercent, BigDecimal priceHigh, BigDecimal priceLow) {
        this.symbol = symbol;
        this.marketIndex = marketIndex;
        this.marketType = marketType;
        this.baseAsset = baseAsset;
        this.quoteAsset = quoteAsset;
        this.status = status;
        this.precision = precision;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.minLeverage = minLeverage;
        this.maxLeverage = maxLeverage;
        this.makerFee = makerFee;
        this.takerFee = takerFee;
        this.oraclePrice = oraclePrice;
        this.markPrice = markPrice;
        this.lastPrice = lastPrice;
        this.baseVolume = baseVolume;
        this.quoteVolume = quoteVolume;
        this.openInterestLong = openInterestLong;
        this.openInterestShort = openInterestShort;
        this.fundingRateLong = fundingRateLong;
        this.fundingRateShort = fundingRateShort;
        this.fundingRate24h = fundingRate24h;
        this.fundingRateUpdateTs = fundingRateUpdateTs;
        this.priceChange24h = priceChange24h;
        this.priceChange24hPercent = priceChange24hPercent;
        this.priceHigh = priceHigh;
        this.priceLow = priceLow;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getMarketIndex() {
        return marketIndex;
    }

    public DriftMarketType getMarketType() {
        return marketType;
    }

    public String getBaseAsset() {
        return baseAsset;
    }

    public String getQuoteAsset() {
        return quoteAsset;
    }

    public String getStatus() {
        return status;
    }

    public int getPrecision() {
        return precision;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public BigDecimal getMinLeverage() {
        return minLeverage;
    }

    public BigDecimal getMaxLeverage() {
        return maxLeverage;
    }

    public BigDecimal getMakerFee() {
        return makerFee;
    }

    public BigDecimal getTakerFee() {
        return takerFee;
    }

    public BigDecimal getOraclePrice() {
        return oraclePrice;
    }

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public BigDecimal getBaseVolume() {
        return baseVolume;
    }

    public BigDecimal getQuoteVolume() {
        return quoteVolume;
    }

    public BigDecimal getOpenInterestLong() {
        return openInterestLong;
    }

    public BigDecimal getOpenInterestShort() {
        return openInterestShort;
    }

    public BigDecimal getFundingRateLong() {
        return fundingRateLong;
    }

    public BigDecimal getFundingRateShort() {
        return fundingRateShort;
    }

    public BigDecimal getFundingRate24h() {
        return fundingRate24h;
    }

    public Long getFundingRateUpdateTs() {
        return fundingRateUpdateTs;
    }

    public BigDecimal getPriceChange24h() {
        return priceChange24h;
    }

    public BigDecimal getPriceChange24hPercent() {
        return priceChange24hPercent;
    }

    public BigDecimal getPriceHigh() {
        return priceHigh;
    }

    public BigDecimal getPriceLow() {
        return priceLow;
    }
}
