package com.fueledbychai.extended.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.DefaultExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilitiesProvider;

public class ExtendedExchangeCapabilitiesProvider implements ExchangeCapabilitiesProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.EXTENDED;
    }

    @Override
    public ExchangeCapabilities getCapabilities() {
        return DefaultExchangeCapabilities.builder(Exchange.EXTENDED)
                .supportsStreaming(true)
                .supportsBrokerage(true)
                .supportsHistoricalData(true)
                .addInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .build();
    }
}
