package com.fueledbychai.qfex.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

class QfexWebSocketApiProviderTest {

    @Test
    @SuppressWarnings("rawtypes")
    void websocketProviderIsDiscoverableThroughServiceLoader() {
        boolean found = false;
        for (ExchangeWebSocketApiProvider provider : ServiceLoader.load(ExchangeWebSocketApiProvider.class)) {
            if (provider instanceof QfexWebSocketApiProvider typedProvider) {
                found = true;
                assertEquals(Exchange.QFEX, typedProvider.getExchange());
                assertEquals(IQfexWebSocketApi.class, typedProvider.getApiType());
                IQfexWebSocketApi api = typedProvider.getWebSocketApi();
                assertNotNull(api);
                assertNotNull(api.marketData());
            }
        }
        assertTrue(found, "Expected ServiceLoader to discover QfexWebSocketApiProvider");
    }
}
