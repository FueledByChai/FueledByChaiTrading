package com.fueledbychai.hyperliquid.ws;

import java.math.BigDecimal;

import com.fueledbychai.data.ITickerTranslator;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

public class HyperliquidTickerBuilder implements ITickerTranslator {

    @Override
    public Ticker translateTicker(InstrumentDescriptor descriptor) {
        if (descriptor.getInstrumentType() == InstrumentType.PERPETUAL_FUTURES) {
            Ticker ticker = new Ticker(descriptor.getExchangeSymbol());
            ticker.setContractMultiplier(BigDecimal.ONE).setCurrency(descriptor.getQuoteCurrency())
                    .setExchange(descriptor.getExchange()).setInstrumentType(descriptor.getInstrumentType())
                    .setMinimumTickSize(descriptor.getPriceTickSize())
                    .setOrderSizeIncrement(descriptor.getOrderSizeIncrement())
                    .setPrimaryExchange(descriptor.getExchange()).setSymbol(descriptor.getExchangeSymbol())
                    .setFundingRateInterval(1).setId(descriptor.getInstrumentId());

            return ticker;
        } else {
            throw new IllegalArgumentException("Unsupported instrument type: " + descriptor.getInstrumentType());
        }
    }
}
