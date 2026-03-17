package com.fueledbychai.aster.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.aster.common.api.IAsterRestApi;
import com.fueledbychai.util.ExchangeRestApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Aster REST API from the shared factory.
 */
public class AsterRestApiExample {

    public static void main(String[] args) {
        IAsterRestApi api = ExchangeRestApiFactory.getApi(Exchange.ASTER,
                IAsterRestApi.class);
        api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
    }
}
