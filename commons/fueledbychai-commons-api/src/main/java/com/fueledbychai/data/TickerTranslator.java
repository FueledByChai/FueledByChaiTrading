package com.fueledbychai.data;

import java.math.BigDecimal;

public class TickerTranslator implements ITickerTranslator {

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        Ticker ticker = new Ticker(descriptor.getExchangeSymbol());
        ticker.setInstrumentType(descriptor.getInstrumentType()).setContractMultiplier(BigDecimal.ONE)
                .setCurrency(descriptor.getQuoteCurrency()).setExchange(descriptor.getExchange())
                .setInstrumentType(descriptor.getInstrumentType()).setMinimumTickSize(descriptor.getPriceTickSize())
                .setOrderSizeIncrement(descriptor.getOrderSizeIncrement()).setPrimaryExchange(descriptor.getExchange())
                .setSymbol(descriptor.getExchangeSymbol()).setFundingRateInterval(descriptor.getFundingPeriodHours())
                .setMinimumOrderSizeNotional(BigDecimal.valueOf(descriptor.getMinNotionalOrderSize()))
                .setMinimumOrderSize(descriptor.getMinOrderSize()).setId(descriptor.getInstrumentId());

        return ticker;
    }
}
