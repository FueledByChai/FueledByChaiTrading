package com.fueledbychai.qfex.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.DefaultExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilitiesProvider;

public class QfexExchangeCapabilitiesProvider implements ExchangeCapabilitiesProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.QFEX;
    }

    @Override
    public ExchangeCapabilities getCapabilities() {
        return DefaultExchangeCapabilities.builder(Exchange.QFEX)
                .supportsStreaming(true)
                .supportsBrokerage(true)
                .supportsHistoricalData(false)
                .addInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .build();
    }
}
