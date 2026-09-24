package com.fueledbychai.qfex.common.api.example;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.qfex.common.api.IQfexRestApi;
import com.fueledbychai.util.ExchangeRestApiFactory;

/**
 * Minimal example showing how strategy or integration code should obtain the
 * Qfex REST API from the shared factory.
 */
public class QfexRestApiExample {

    public static void main(String[] args) {
        IQfexRestApi api = ExchangeRestApiFactory.getApi(Exchange.QFEX,
                IQfexRestApi.class);
        api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
    }
}
