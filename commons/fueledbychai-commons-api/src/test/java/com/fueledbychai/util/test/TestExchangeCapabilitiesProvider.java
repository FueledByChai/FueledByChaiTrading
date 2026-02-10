package com.fueledbychai.util.test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.DefaultExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilitiesProvider;

public class TestExchangeCapabilitiesProvider implements ExchangeCapabilitiesProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.SEHKNTL;
    }

    @Override
    public ExchangeCapabilities getCapabilities() {
        return DefaultExchangeCapabilities.builder(Exchange.SEHKNTL)
                .supportsStreaming(true)
                .supportsBrokerage(false)
                .supportsHistoricalData(true)
                .addInstrumentType(InstrumentType.CRYPTO_SPOT)
                .build();
    }
}
