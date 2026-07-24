package com.fueledbychai.grvt.common.api.model;

import java.math.BigDecimal;

/**
 * A single leg of a GRVT order. GRVT orders are multi-leg capable; perpetual / spot orders use a
 * single leg.
 */
public class GrvtOrderLeg {

    private String instrument;
    private BigDecimal size;
    private BigDecimal limitPrice;
    private boolean buyingAsset;

    public GrvtOrderLeg() {
    }

    public GrvtOrderLeg(String instrument, BigDecimal size, BigDecimal limitPrice, boolean buyingAsset) {
        this.instrument = instrument;
        this.size = size;
        this.limitPrice = limitPrice;
        this.buyingAsset = buyingAsset;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public BigDecimal getSize() {
        return size;
    }

    public void setSize(BigDecimal size) {
        this.size = size;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    public boolean isBuyingAsset() {
        return buyingAsset;
    }

    public void setBuyingAsset(boolean buyingAsset) {
        this.buyingAsset = buyingAsset;
    }
}
