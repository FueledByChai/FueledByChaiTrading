package com.fueledbychai.okx.common.api.ws.model;

import java.math.BigDecimal;

public class OkxFundingRateUpdate {

    protected final String instrumentId;
    protected final String instrumentType;
    protected final Long timestamp;
    protected final BigDecimal fundingRate;
    protected final BigDecimal nextFundingRate;
    protected final Long fundingTime;
    protected final Long nextFundingTime;

    public OkxFundingRateUpdate(String instrumentId, String instrumentType, Long timestamp, BigDecimal fundingRate,
            BigDecimal nextFundingRate, Long fundingTime, Long nextFundingTime) {
        this.instrumentId = instrumentId;
        this.instrumentType = instrumentType;
        this.timestamp = timestamp;
        this.fundingRate = fundingRate;
        this.nextFundingRate = nextFundingRate;
        this.fundingTime = fundingTime;
        this.nextFundingTime = nextFundingTime;
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

    public BigDecimal getFundingRate() {
        return fundingRate;
    }

    public BigDecimal getNextFundingRate() {
        return nextFundingRate;
    }

    public Long getFundingTime() {
        return fundingTime;
    }

    public Long getNextFundingTime() {
        return nextFundingTime;
    }
}
