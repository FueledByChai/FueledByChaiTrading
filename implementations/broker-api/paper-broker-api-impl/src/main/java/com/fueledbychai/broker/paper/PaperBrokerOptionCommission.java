package com.fueledbychai.broker.paper;

import java.math.BigDecimal;

import com.fueledbychai.data.Ticker;

public class PaperBrokerOptionCommission {

    public enum FeeBasis {
        UNDERLYING_NOTIONAL, PREMIUM_NOTIONAL, CONTRACT_MULTIPLIER
    }

    protected final double makerFeeBps;
    protected final double takerFeeBps;
    protected final Double makerPremiumCapPercent;
    protected final Double takerPremiumCapPercent;
    protected final FeeBasis feeBasis;
    protected final boolean fallbackToTradePriceWhenReferenceUnavailable;

    public PaperBrokerOptionCommission(double makerFeeBps, double takerFeeBps, Double makerPremiumCapPercent,
            Double takerPremiumCapPercent) {
        this(makerFeeBps, takerFeeBps, makerPremiumCapPercent, takerPremiumCapPercent,
                FeeBasis.UNDERLYING_NOTIONAL, true);
    }

    public PaperBrokerOptionCommission(double makerFeeBps, double takerFeeBps, Double makerPremiumCapPercent,
            Double takerPremiumCapPercent, FeeBasis feeBasis,
            boolean fallbackToTradePriceWhenReferenceUnavailable) {
        this.makerFeeBps = makerFeeBps;
        this.takerFeeBps = takerFeeBps;
        this.makerPremiumCapPercent = makerPremiumCapPercent;
        this.takerPremiumCapPercent = takerPremiumCapPercent;
        this.feeBasis = feeBasis == null ? FeeBasis.UNDERLYING_NOTIONAL : feeBasis;
        this.fallbackToTradePriceWhenReferenceUnavailable = fallbackToTradePriceWhenReferenceUnavailable;
    }

    public double getMakerFeeBps() {
        return makerFeeBps;
    }

    public double getTakerFeeBps() {
        return takerFeeBps;
    }

    public Double getMakerPremiumCapPercent() {
        return makerPremiumCapPercent;
    }

    public Double getTakerPremiumCapPercent() {
        return takerPremiumCapPercent;
    }

    public FeeBasis getFeeBasis() {
        return feeBasis;
    }

    public boolean isFallbackToTradePriceWhenReferenceUnavailable() {
        return fallbackToTradePriceWhenReferenceUnavailable;
    }

    public double calculateFee(Ticker ticker, double executionPrice, double size, boolean isMaker, Double underlyingPrice) {
        double feeRate = (isMaker ? makerFeeBps : takerFeeBps) / 10000.0;
        double feeBasisValue = Math.abs(resolveFeeBasisValue(ticker, executionPrice, size, underlyingPrice));
        double fee = feeBasisValue * feeRate;

        Double premiumCapPercent = isMaker ? makerPremiumCapPercent : takerPremiumCapPercent;
        if (premiumCapPercent == null || premiumCapPercent <= 0.0 || fee >= 0.0) {
            return fee;
        }

        double premiumNotional = Math.abs(executionPrice * size * resolveContractMultiplier(ticker));
        double feeCap = premiumNotional * (premiumCapPercent / 100.0);
        return Math.copySign(Math.min(Math.abs(fee), feeCap), fee);
    }

    protected double resolveFeeBasisValue(Ticker ticker, double executionPrice, double size, Double underlyingPrice) {
        double contractMultiplier = resolveContractMultiplier(ticker);
        return switch (feeBasis) {
            case PREMIUM_NOTIONAL -> executionPrice * size * contractMultiplier;
            case CONTRACT_MULTIPLIER -> size * contractMultiplier;
            case UNDERLYING_NOTIONAL -> resolveUnderlyingPrice(executionPrice, underlyingPrice) * size * contractMultiplier;
        };
    }

    protected double resolveUnderlyingPrice(double executionPrice, Double underlyingPrice) {
        if (underlyingPrice != null && underlyingPrice.doubleValue() > 0.0) {
            return underlyingPrice.doubleValue();
        }

        if (fallbackToTradePriceWhenReferenceUnavailable) {
            return executionPrice;
        }

        throw new IllegalStateException("Underlying price is required for option commission calculation");
    }

    protected double resolveContractMultiplier(Ticker ticker) {
        if (ticker == null) {
            return 1.0;
        }

        BigDecimal multiplier = ticker.getContractMultiplier();
        if (multiplier == null || multiplier.signum() <= 0) {
            return 1.0;
        }

        return multiplier.doubleValue();
    }

    @Override
    public String toString() {
        return "PaperBrokerOptionCommission{" + "makerFeeBps=" + makerFeeBps + ", takerFeeBps=" + takerFeeBps
                + ", makerPremiumCapPercent=" + makerPremiumCapPercent + ", takerPremiumCapPercent="
                + takerPremiumCapPercent + ", feeBasis=" + feeBasis
                + ", fallbackToTradePriceWhenReferenceUnavailable=" + fallbackToTradePriceWhenReferenceUnavailable
                + '}';
    }
}
