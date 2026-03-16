package com.fueledbychai.drift.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.drift.common.api.IDriftRestApi;
import com.fueledbychai.util.ExchangeRestApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Drift REST API from the shared factory.
 */
public class DriftRestApiExample {

    public static void main(String[] args) {
        IDriftRestApi api = ExchangeRestApiFactory.getApi(Exchange.DRIFT,
                IDriftRestApi.class);
        api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
    }
}
