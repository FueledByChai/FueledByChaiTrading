package com.fueledbychai.grvt.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.DefaultExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilitiesProvider;

public class GrvtExchangeCapabilitiesProvider implements ExchangeCapabilitiesProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.GRVT;
    }

    @Override
    public ExchangeCapabilities getCapabilities() {
        return DefaultExchangeCapabilities.builder(Exchange.GRVT)
                .supportsStreaming(true)
                .supportsBrokerage(true)
                .supportsHistoricalData(true)
                .addInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .addInstrumentType(InstrumentType.FUTURES)
                .addInstrumentType(InstrumentType.CRYPTO_SPOT)
                .addInstrumentType(InstrumentType.OPTION)
                .build();
    }
}
