package com.fueledbychai.drift.common.api.model;

import java.math.BigDecimal;

public class DriftGatewayPosition {

    private final DriftMarketType marketType;
    private final int marketIndex;
    private final BigDecimal amount;
    private final String type;
    private final BigDecimal averageEntry;
    private final BigDecimal liquidationPrice;
    private final BigDecimal unrealizedPnl;
    private final BigDecimal unsettledPnl;
    private final BigDecimal oraclePrice;

    public DriftGatewayPosition(DriftMarketType marketType, int marketIndex, BigDecimal amount, String type,
            BigDecimal averageEntry, BigDecimal liquidationPrice, BigDecimal unrealizedPnl, BigDecimal unsettledPnl,
            BigDecimal oraclePrice) {
        this.marketType = marketType;
        this.marketIndex = marketIndex;
        this.amount = amount;
        this.type = type;
        this.averageEntry = averageEntry;
        this.liquidationPrice = liquidationPrice;
        this.unrealizedPnl = unrealizedPnl;
        this.unsettledPnl = unsettledPnl;
        this.oraclePrice = oraclePrice;
    }

    public DriftMarketType getMarketType() {
        return marketType;
    }

    public int getMarketIndex() {
        return marketIndex;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getAverageEntry() {
        return averageEntry;
    }

    public BigDecimal getLiquidationPrice() {
        return liquidationPrice;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public BigDecimal getUnsettledPnl() {
        return unsettledPnl;
    }

    public BigDecimal getOraclePrice() {
        return oraclePrice;
    }
}
