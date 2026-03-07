package com.fueledbychai.bybit.common.api.example;

import com.fueledbychai.bybit.common.api.IBybitRestApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.ExchangeRestApiFactory;

public class BybitRestApiExample {

    public static void main(String[] args) {
        IBybitRestApi api = ExchangeRestApiFactory.getApi(Exchange.BYBIT, IBybitRestApi.class);

        InstrumentDescriptor[] spot = api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);
        InstrumentDescriptor[] perps = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
        InstrumentDescriptor[] futures = api.getAllInstrumentsForType(InstrumentType.FUTURES);
        InstrumentDescriptor[] options = api.getAllInstrumentsForType(InstrumentType.OPTION);

        System.out.println("Bybit spot instruments: " + spot.length);
        System.out.println("Bybit perpetual instruments: " + perps.length);
        System.out.println("Bybit futures instruments: " + futures.length);
        System.out.println("Bybit options instruments: " + options.length);
    }
}
