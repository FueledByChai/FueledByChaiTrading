package com.fueledbychai.paradex;

import java.math.BigDecimal;

import com.fueledbychai.data.ITickerBuilder;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public class ParadexTickerBuilder implements ITickerBuilder {

    @Override
    public Ticker buildTicker(InstrumentDescriptor descriptor) {
        if (descriptor.getInstrumentType() == InstrumentType.PERPETUAL_FUTURES) {
            Ticker ticker = new Ticker(descriptor.getExchangeSymbol());
            ticker.setContractMultiplier(BigDecimal.ONE).setCurrency(descriptor.getQuoteCurrency())
                    .setExchange(descriptor.getExchange()).setInstrumentType(descriptor.getInstrumentType())
                    .setMinimumTickSize(descriptor.getPriceTickSize())
                    .setOrderSizeIncrement(descriptor.getOrderSizeIncrement())
                    .setPrimaryExchange(descriptor.getExchange()).setSymbol(descriptor.getExchangeSymbol())
                    .setFundingRateInterval(descriptor.getFundingPeriodHours())
                    .setMinimumOrderSizeNotional(BigDecimal.valueOf(descriptor.getMinNotionalOrderSize()));

            return ticker;
        } else {
            throw new IllegalArgumentException("Unsupported instrument type: " + descriptor.getInstrumentType());
        }
    }
}
