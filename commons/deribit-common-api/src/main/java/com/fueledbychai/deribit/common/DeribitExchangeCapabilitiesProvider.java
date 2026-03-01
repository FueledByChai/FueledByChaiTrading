package com.fueledbychai.deribit.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.DefaultExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilitiesProvider;

public class DeribitExchangeCapabilitiesProvider implements ExchangeCapabilitiesProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.DERIBIT;
    }

    @Override
    public ExchangeCapabilities getCapabilities() {
        return DefaultExchangeCapabilities.builder(Exchange.DERIBIT)
                .supportsStreaming(true)
                .supportsBrokerage(false)
                .supportsHistoricalData(false)
                .addInstrumentType(InstrumentType.CRYPTO_SPOT)
                .addInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .addInstrumentType(InstrumentType.OPTION)
                .build();
    }
}
