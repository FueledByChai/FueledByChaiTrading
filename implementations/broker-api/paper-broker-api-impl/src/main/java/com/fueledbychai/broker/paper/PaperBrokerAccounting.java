package com.fueledbychai.broker.paper;

import java.math.BigDecimal;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public class PaperBrokerAccounting {

    public enum PnlModelType {
        LINEAR, INVERSE
    }

    public static final PaperBrokerAccounting LINEAR_ACCOUNTING = new PaperBrokerAccounting(PnlModelType.LINEAR);
    public static final PaperBrokerAccounting INVERSE_ACCOUNTING = new PaperBrokerAccounting(PnlModelType.INVERSE);

    protected final PnlModelType pnlModelType;

    public PaperBrokerAccounting(PnlModelType pnlModelType) {
        this.pnlModelType = pnlModelType == null ? PnlModelType.LINEAR : pnlModelType;
    }

    public PnlModelType getPnlModelType() {
        return pnlModelType;
    }

    public static PaperBrokerAccounting forTicker(Ticker ticker) {
        if (ticker == null || ticker.getExchange() == null || ticker.getInstrumentType() == null) {
            return LINEAR_ACCOUNTING;
        }

        Exchange exchange = ticker.getExchange();
        InstrumentType instrumentType = ticker.getInstrumentType();
        if (exchange.equals(Exchange.DERIBIT)
                && (instrumentType == InstrumentType.PERPETUAL_FUTURES || instrumentType == InstrumentType.FUTURES)
                && isUsdQuoted(ticker)) {
            return INVERSE_ACCOUNTING;
        }
        if (exchange.equals(Exchange.BYBIT)
                && (instrumentType == InstrumentType.PERPETUAL_FUTURES || instrumentType == InstrumentType.FUTURES)
                && isUsdQuoted(ticker) && looksLikeBybitInverseContract(ticker)) {
            return INVERSE_ACCOUNTING;
        }
        if (exchange.equals(Exchange.OKX)
                && (instrumentType == InstrumentType.PERPETUAL_FUTURES || instrumentType == InstrumentType.FUTURES)
                && looksLikeOkxInverseContract(ticker)) {
            return INVERSE_ACCOUNTING;
        }

        return LINEAR_ACCOUNTING;
    }

    public double calculateRealizedPnl(Ticker ticker, double entryPrice, double exitPrice, double closingSize,
            double signedPositionBeforeClose) {
        if (!isPositivePrice(entryPrice) || !isPositivePrice(exitPrice) || closingSize <= 0.0
                || signedPositionBeforeClose == 0.0) {
            return 0.0;
        }

        double multiplier = resolveContractMultiplier(ticker);
        double direction = Math.signum(signedPositionBeforeClose);
        if (pnlModelType == PnlModelType.INVERSE) {
            return direction * closingSize * multiplier * ((1.0 / entryPrice) - (1.0 / exitPrice));
        }
        return direction * (exitPrice - entryPrice) * closingSize * multiplier;
    }

    public double calculateUnrealizedPnl(Ticker ticker, double entryPrice, double markPrice, double signedPosition) {
        if (!isPositivePrice(entryPrice) || !isPositivePrice(markPrice) || signedPosition == 0.0) {
            return 0.0;
        }

        double multiplier = resolveContractMultiplier(ticker);
        if (pnlModelType == PnlModelType.INVERSE) {
            return signedPosition * multiplier * ((1.0 / entryPrice) - (1.0 / markPrice));
        }
        return (markPrice - entryPrice) * signedPosition * multiplier;
    }

    public double calculateFunding(Ticker ticker, double markPrice, double signedPosition, double fundingRate,
            double hours) {
        if (!isPositivePrice(markPrice) || signedPosition == 0.0 || fundingRate == 0.0 || hours <= 0.0) {
            return 0.0;
        }

        double multiplier = resolveContractMultiplier(ticker);
        if (pnlModelType == PnlModelType.INVERSE) {
            return ((signedPosition * multiplier) / markPrice) * (-fundingRate) * hours;
        }
        return (markPrice * signedPosition * multiplier) * (-fundingRate) * hours;
    }

    public double calculateVolume(Ticker ticker, double executionPrice, double size) {
        if (!isPositivePrice(executionPrice) || size == 0.0) {
            return 0.0;
        }

        double multiplier = resolveContractMultiplier(ticker);
        if (pnlModelType == PnlModelType.INVERSE) {
            return Math.abs(size * multiplier);
        }
        return Math.abs(executionPrice * size * multiplier);
    }

    public double blendAverageEntryPrice(Ticker ticker, double currentAbsoluteSize, double currentAverageEntryPrice,
            double additionalSize, double fillPrice) {
        if (additionalSize <= 0.0 || !isPositivePrice(fillPrice)) {
            return currentAverageEntryPrice;
        }
        if (currentAbsoluteSize <= 0.0 || !isPositivePrice(currentAverageEntryPrice)) {
            return fillPrice;
        }

        if (pnlModelType == PnlModelType.INVERSE) {
            double reciprocalAverage = (currentAbsoluteSize / currentAverageEntryPrice) + (additionalSize / fillPrice);
            if (reciprocalAverage <= 0.0) {
                return fillPrice;
            }
            return (currentAbsoluteSize + additionalSize) / reciprocalAverage;
        }

        return ((currentAverageEntryPrice * currentAbsoluteSize) + (fillPrice * additionalSize))
                / (currentAbsoluteSize + additionalSize);
    }

    protected double resolveContractMultiplier(Ticker ticker) {
        if (ticker == null) {
            return 1.0;
        }

        BigDecimal contractMultiplier = ticker.getContractMultiplier();
        if (contractMultiplier == null || contractMultiplier.signum() <= 0) {
            return 1.0;
        }
        return contractMultiplier.doubleValue();
    }

    protected boolean isPositivePrice(double value) {
        return value > 0.0 && Double.isFinite(value);
    }

    protected static boolean isUsdQuoted(Ticker ticker) {
        if (ticker == null || ticker.getCurrency() == null) {
            return false;
        }
        return "USD".equalsIgnoreCase(ticker.getCurrency().trim());
    }

    protected static boolean looksLikeBybitInverseContract(Ticker ticker) {
        if (ticker == null || ticker.getSymbol() == null) {
            return false;
        }
        String symbol = ticker.getSymbol().trim().toUpperCase();
        return !symbol.contains("-") && symbol.contains("USD");
    }

    protected static boolean looksLikeOkxInverseContract(Ticker ticker) {
        if (ticker == null || ticker.getSymbol() == null || ticker.getCurrency() == null) {
            return false;
        }

        String symbol = ticker.getSymbol().trim().toUpperCase();
        String settlementCurrency = ticker.getCurrency().trim().toUpperCase();
        return symbol.contains("-USD-") && !"USD".equals(settlementCurrency) && !"USDT".equals(settlementCurrency)
                && !"USDC".equals(settlementCurrency);
    }

    @Override
    public String toString() {
        return "PaperBrokerAccounting{" + "pnlModelType=" + pnlModelType + '}';
    }
}
