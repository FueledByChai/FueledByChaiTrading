package com.fueledbychai.broker.paper;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public class PaperBrokerCommission {
    public static final PaperBrokerCommission PARADEX_COMMISSION = new PaperBrokerCommission(-0.2, -2.0);
    public static final PaperBrokerCommission HYPERLIQUID_COMMISSION = new PaperBrokerCommission(-1.5, -4.5);
    public static final PaperBrokerCommission BYBIT_COMMISSION = new PaperBrokerCommission(0.0, 0.0,
            new PaperBrokerOptionCommission(-2.0, -3.0, 7.0, 7.0,
                    PaperBrokerOptionCommission.FeeBasis.UNDERLYING_NOTIONAL, true));
    public static final PaperBrokerCommission OKX_COMMISSION = new PaperBrokerCommission(0.0, 0.0,
            new PaperBrokerOptionCommission(-3.0, -3.0, 7.0, 7.0,
                    PaperBrokerOptionCommission.FeeBasis.CONTRACT_MULTIPLIER, true));
    public static final PaperBrokerCommission DERIBIT_LINEAR_COMMISSION = new PaperBrokerCommission(0.0, -5.0,
            new PaperBrokerOptionCommission(-3.0, -3.0, 12.5, 12.5,
                    PaperBrokerOptionCommission.FeeBasis.UNDERLYING_NOTIONAL, true));
    public static final PaperBrokerCommission DERIBIT_INVERSE_COMMISSION = new PaperBrokerCommission(0.0, -5.0,
            new PaperBrokerOptionCommission(-3.0, -3.0, 12.5, 12.5,
                    PaperBrokerOptionCommission.FeeBasis.CONTRACT_MULTIPLIER, true));
    public static final PaperBrokerCommission DEFAULT_COMMISSION = new PaperBrokerCommission(0.0, 0.0);

    protected double makerFeeBps = 0.0;
    protected double takerFeeBps = 0.0;
    protected PaperBrokerOptionCommission optionCommission;

    /**
     * Constructor for PaperBrokerCommission.
     *
     * @param makerFeeBps The maker fee in basis points. (rebates are positive, fees
     *                    are negative)
     * @param takerFeeBps The taker fee in basis points. (rebates are positive, fees
     *                    are negative)
     */
    public PaperBrokerCommission(double makerFeeBps, double takerFeeBps) {
        this(makerFeeBps, takerFeeBps, null);
    }

    public PaperBrokerCommission(double makerFeeBps, double takerFeeBps,
            PaperBrokerOptionCommission optionCommission) {
        this.makerFeeBps = makerFeeBps;
        this.takerFeeBps = takerFeeBps;
        this.optionCommission = optionCommission;
    }

    public double getMakerFeeBps() {
        return makerFeeBps;
    }

    public double getTakerFeeBps() {
        return takerFeeBps;
    }

    public static PaperBrokerCommission forTicker(Ticker ticker) {
        if (ticker == null || ticker.getExchange() == null) {
            return DEFAULT_COMMISSION;
        }

        Exchange exchange = ticker.getExchange();
        if (exchange.equals(Exchange.PARADEX)) {
            return PARADEX_COMMISSION;
        }
        if (exchange.equals(Exchange.HYPERLIQUID)) {
            return HYPERLIQUID_COMMISSION;
        }
        if (exchange.equals(Exchange.BYBIT)) {
            return BYBIT_COMMISSION;
        }
        if (exchange.equals(Exchange.OKX)) {
            return OKX_COMMISSION;
        }
        if (exchange.equals(Exchange.DERIBIT)) {
            return isDeribitInverseOption(ticker) ? DERIBIT_INVERSE_COMMISSION : DERIBIT_LINEAR_COMMISSION;
        }

        return DEFAULT_COMMISSION;
    }

    public PaperBrokerOptionCommission getOptionCommission() {
        return optionCommission;
    }

    public double calculateFee(Ticker ticker, double executionPrice, double size, boolean isMaker, Double underlyingPrice) {
        if (isOptionTicker(ticker) && optionCommission != null) {
            return optionCommission.calculateFee(ticker, executionPrice, size, isMaker, underlyingPrice);
        }

        double notional = Math.abs(executionPrice * size);
        double feeRate = (isMaker ? makerFeeBps : takerFeeBps) / 10000.0;
        return notional * feeRate;
    }

    protected boolean isOptionTicker(Ticker ticker) {
        if (ticker == null || ticker.getInstrumentType() == null) {
            return false;
        }

        InstrumentType instrumentType = ticker.getInstrumentType();
        return instrumentType == InstrumentType.OPTION || instrumentType == InstrumentType.PERPETUAL_OPTION;
    }

    protected static boolean isDeribitInverseOption(Ticker ticker) {
        if (ticker == null || ticker.getInstrumentType() != InstrumentType.OPTION) {
            return false;
        }

        String symbol = ticker.getSymbol();
        if (symbol != null && symbol.toUpperCase().contains("_USDC")) {
            return false;
        }

        String currency = ticker.getCurrency();
        if (currency == null || currency.isBlank()) {
            return true;
        }

        String normalized = currency.trim().toUpperCase();
        return "USD".equals(normalized) || "BTC".equals(normalized) || "ETH".equals(normalized);
    }

    @Override
    public String toString() {
        return "PaperBrokerCommission{" + "makerFeeBps=" + makerFeeBps + ", takerFeeBps=" + takerFeeBps
                + ", optionCommission=" + optionCommission + '}';
    }

}
