package com.fueledbychai.bybit.common.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

class BybitWebSocketApiProviderTest {

    @Test
    void websocketProviderIsDiscoverableThroughServiceLoader() {
        boolean found = false;
        for (ExchangeWebSocketApiProvider provider : ServiceLoader.load(ExchangeWebSocketApiProvider.class)) {
            if (provider instanceof BybitWebSocketApiProvider typedProvider) {
                found = true;
                assertEquals(Exchange.BYBIT, typedProvider.getExchange());
                assertEquals(IBybitWebSocketApi.class, typedProvider.getApiType());

                IBybitWebSocketApi api = typedProvider.getWebSocketApi();
                assertNotNull(api);
                assertDoesNotThrow(api::disconnectAll);
            }
        }

        assertTrue(found, "Expected ServiceLoader to discover BybitWebSocketApiProvider");
    }
}
