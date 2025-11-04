package com.fueledbychai.binance.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Binance rate limit configuration.
 */
public class BinanceRateLimit {
    
    @JsonProperty("rateLimitType")
    private String rateLimitType;
    
    @JsonProperty("interval")
    private String interval;
    
    @JsonProperty("intervalNum")
    private int intervalNum;
    
    @JsonProperty("limit")
    private int limit;
    
    // Default constructor for JSON deserialization
    public BinanceRateLimit() {}
    
    // Getters and setters
    public String getRateLimitType() {
        return rateLimitType;
    }
    
    public void setRateLimitType(String rateLimitType) {
        this.rateLimitType = rateLimitType;
    }
    
    public String getInterval() {
        return interval;
    }
    
    public void setInterval(String interval) {
        this.interval = interval;
    }
    
    public int getIntervalNum() {
        return intervalNum;
    }
    
    public void setIntervalNum(int intervalNum) {
        this.intervalNum = intervalNum;
    }
    
    public int getLimit() {
        return limit;
    }
    
    public void setLimit(int limit) {
        this.limit = limit;
    }
    
    @Override
    public String toString() {
        return "BinanceRateLimit{" +
                "rateLimitType='" + rateLimitType + '\'' +
                ", interval='" + interval + '\'' +
                ", intervalNum=" + intervalNum +
                ", limit=" + limit +
                '}';
    }
}