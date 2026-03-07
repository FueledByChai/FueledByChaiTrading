package com.fueledbychai.bybit.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.DefaultExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilitiesProvider;

public class BybitExchangeCapabilitiesProvider implements ExchangeCapabilitiesProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.BYBIT;
    }

    @Override
    public ExchangeCapabilities getCapabilities() {
        return DefaultExchangeCapabilities.builder(Exchange.BYBIT)
                .supportsStreaming(true)
                .supportsBrokerage(true)
                .supportsHistoricalData(false)
                .addInstrumentType(InstrumentType.CRYPTO_SPOT)
                .addInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .addInstrumentType(InstrumentType.FUTURES)
                .addInstrumentType(InstrumentType.OPTION)
                .build();
    }
}
