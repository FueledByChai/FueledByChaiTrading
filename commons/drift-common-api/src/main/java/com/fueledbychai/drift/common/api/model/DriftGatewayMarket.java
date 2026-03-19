package com.fueledbychai.drift.common.api.model;

import java.math.BigDecimal;

public class DriftGatewayMarket {

    private final int marketIndex;
    private final DriftMarketType marketType;
    private final String symbol;
    private final BigDecimal priceStep;
    private final BigDecimal amountStep;
    private final BigDecimal minOrderSize;
    private final BigDecimal initialMarginRatio;
    private final BigDecimal maintenanceMarginRatio;

    public DriftGatewayMarket(int marketIndex, DriftMarketType marketType, String symbol, BigDecimal priceStep,
            BigDecimal amountStep, BigDecimal minOrderSize, BigDecimal initialMarginRatio,
            BigDecimal maintenanceMarginRatio) {
        this.marketIndex = marketIndex;
        this.marketType = marketType;
        this.symbol = symbol;
        this.priceStep = priceStep;
        this.amountStep = amountStep;
        this.minOrderSize = minOrderSize;
        this.initialMarginRatio = initialMarginRatio;
        this.maintenanceMarginRatio = maintenanceMarginRatio;
    }

    public int getMarketIndex() {
        return marketIndex;
    }

    public DriftMarketType getMarketType() {
        return marketType;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPriceStep() {
        return priceStep;
    }

    public BigDecimal getAmountStep() {
        return amountStep;
    }

    public BigDecimal getMinOrderSize() {
        return minOrderSize;
    }

    public BigDecimal getInitialMarginRatio() {
        return initialMarginRatio;
    }

    public BigDecimal getMaintenanceMarginRatio() {
        return maintenanceMarginRatio;
    }
}
