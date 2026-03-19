package com.fueledbychai.drift.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.DefaultExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilitiesProvider;

public class DriftExchangeCapabilitiesProvider implements ExchangeCapabilitiesProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.DRIFT;
    }

    @Override
    public ExchangeCapabilities getCapabilities() {
        return DefaultExchangeCapabilities.builder(Exchange.DRIFT)
                .supportsStreaming(true)
                .supportsBrokerage(true)
                .supportsHistoricalData(false)
                .addInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .addInstrumentType(InstrumentType.CRYPTO_SPOT)
                .build();
    }
}
