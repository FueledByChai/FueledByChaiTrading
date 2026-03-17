package com.fueledbychai.aster.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.DefaultExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilitiesProvider;

public class AsterExchangeCapabilitiesProvider implements ExchangeCapabilitiesProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.ASTER;
    }

    @Override
    public ExchangeCapabilities getCapabilities() {
        return DefaultExchangeCapabilities.builder(Exchange.ASTER)
                .supportsStreaming(true)
                .supportsBrokerage(true)
                .supportsHistoricalData(false)
                .addInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .build();
    }
}
