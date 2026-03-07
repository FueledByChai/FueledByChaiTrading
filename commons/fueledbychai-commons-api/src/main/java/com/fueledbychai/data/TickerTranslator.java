package com.fueledbychai.data;

import java.math.BigDecimal;

public class TickerTranslator implements ITickerTranslator {

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        BigDecimal contractMultiplier = descriptor.getContractMultiplier() == null ? BigDecimal.ONE
                : descriptor.getContractMultiplier();
        Ticker ticker = new Ticker(descriptor.getExchangeSymbol());
        ticker.setInstrumentType(descriptor.getInstrumentType()).setContractMultiplier(contractMultiplier)
                .setCurrency(descriptor.getQuoteCurrency()).setExchange(descriptor.getExchange())
                .setInstrumentType(descriptor.getInstrumentType()).setMinimumTickSize(descriptor.getPriceTickSize())
                .setOrderSizeIncrement(descriptor.getOrderSizeIncrement()).setPrimaryExchange(descriptor.getExchange())
                .setSymbol(descriptor.getExchangeSymbol()).setFundingRateInterval(descriptor.getFundingPeriodHours())
                .setMinimumOrderSizeNotional(BigDecimal.valueOf(descriptor.getMinNotionalOrderSize()))
                .setMinimumOrderSize(descriptor.getMinOrderSize()).setId(descriptor.getInstrumentId());

        return ticker;
    }
}
