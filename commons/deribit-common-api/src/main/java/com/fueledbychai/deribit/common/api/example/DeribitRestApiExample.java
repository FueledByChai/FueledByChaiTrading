package com.fueledbychai.deribit.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.deribit.common.api.IDeribitRestApi;
import com.fueledbychai.util.ExchangeRestApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Deribit REST API from the shared factory.
 */
public class DeribitRestApiExample {

    public static void main(String[] args) {
        IDeribitRestApi api = ExchangeRestApiFactory.getApi(Exchange.DERIBIT,
                IDeribitRestApi.class);
        api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
    }
}
