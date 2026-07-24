package com.fueledbychai.grvt.common.api.model;

import java.math.BigDecimal;

/**
 * A GRVT tradable instrument as returned by the market-data {@code instruments} endpoint.
 * <p>
 * {@code instrumentHash} (e.g. {@code 0x030501}) is the {@code assetID} used when signing orders,
 * and {@code baseDecimals} is the size multiplier exponent (size is scaled by 10^baseDecimals).
 */
public class GrvtInstrument {

    private String instrument;
    private String instrumentHash;
    private String base;
    private String quote;
    private String kind;
    private int baseDecimals;
    private int quoteDecimals;
    private BigDecimal tickSize;
    private BigDecimal minSize;
    private int fundingIntervalHours;

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public String getInstrumentHash() {
        return instrumentHash;
    }

    public void setInstrumentHash(String instrumentHash) {
        this.instrumentHash = instrumentHash;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getQuote() {
        return quote;
    }

    public void setQuote(String quote) {
        this.quote = quote;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getBaseDecimals() {
        return baseDecimals;
    }

    public void setBaseDecimals(int baseDecimals) {
        this.baseDecimals = baseDecimals;
    }

    public int getQuoteDecimals() {
        return quoteDecimals;
    }

    public void setQuoteDecimals(int quoteDecimals) {
        this.quoteDecimals = quoteDecimals;
    }

    public BigDecimal getTickSize() {
        return tickSize;
    }

    public void setTickSize(BigDecimal tickSize) {
        this.tickSize = tickSize;
    }

    public BigDecimal getMinSize() {
        return minSize;
    }

    public void setMinSize(BigDecimal minSize) {
        this.minSize = minSize;
    }

    public int getFundingIntervalHours() {
        return fundingIntervalHours;
    }

    public void setFundingIntervalHours(int fundingIntervalHours) {
        this.fundingIntervalHours = fundingIntervalHours;
    }
}
