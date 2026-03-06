package com.fueledbychai.okx.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.okx.common.api.IOkxRestApi;
import com.fueledbychai.util.ExchangeRestApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Okx REST API from the shared factory.
 */
public class OkxRestApiExample {

    public static void main(String[] args) {
        IOkxRestApi api = ExchangeRestApiFactory.getApi(Exchange.OKX,
                IOkxRestApi.class);

        InstrumentDescriptor[] spot = api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);
        InstrumentDescriptor[] swap = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
        InstrumentDescriptor[] futures = api.getAllInstrumentsForType(InstrumentType.FUTURES);
        InstrumentDescriptor[] options = api.getAllInstrumentsForType(InstrumentType.OPTION);

        System.out.println("OKX Spot instruments: " + spot.length);
        System.out.println("OKX Perpetual instruments: " + swap.length);
        System.out.println("OKX Futures instruments: " + futures.length);
        System.out.println("OKX Option instruments: " + options.length);
    }
}
