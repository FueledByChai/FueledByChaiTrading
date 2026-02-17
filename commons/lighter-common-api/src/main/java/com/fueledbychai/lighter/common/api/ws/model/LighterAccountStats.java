package com.fueledbychai.lighter.common.api.ws.model;

import java.math.BigDecimal;

public class LighterAccountStats {

    private BigDecimal collateral;
    private BigDecimal portfolioValue;
    private BigDecimal leverage;
    private BigDecimal availableBalance;
    private BigDecimal marginUsage;
    private BigDecimal buyingPower;
    private LighterAccountStatsBreakdown crossStats;
    private LighterAccountStatsBreakdown totalStats;

    public BigDecimal getCollateral() {
        return collateral;
    }

    public void setCollateral(BigDecimal collateral) {
        this.collateral = collateral;
    }

    public BigDecimal getPortfolioValue() {
        return portfolioValue;
    }

    public void setPortfolioValue(BigDecimal portfolioValue) {
        this.portfolioValue = portfolioValue;
    }

    public BigDecimal getLeverage() {
        return leverage;
    }

    public void setLeverage(BigDecimal leverage) {
        this.leverage = leverage;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getMarginUsage() {
        return marginUsage;
    }

    public void setMarginUsage(BigDecimal marginUsage) {
        this.marginUsage = marginUsage;
    }

    public BigDecimal getBuyingPower() {
        return buyingPower;
    }

    public void setBuyingPower(BigDecimal buyingPower) {
        this.buyingPower = buyingPower;
    }

    public LighterAccountStatsBreakdown getCrossStats() {
        return crossStats;
    }

    public void setCrossStats(LighterAccountStatsBreakdown crossStats) {
        this.crossStats = crossStats;
    }

    public LighterAccountStatsBreakdown getTotalStats() {
        return totalStats;
    }

    public void setTotalStats(LighterAccountStatsBreakdown totalStats) {
        this.totalStats = totalStats;
    }
}
