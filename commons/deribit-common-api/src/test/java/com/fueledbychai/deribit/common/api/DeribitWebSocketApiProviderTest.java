package com.fueledbychai.deribit.common.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

class DeribitWebSocketApiProviderTest {

    @Test
    void websocketProviderIsDiscoverableThroughServiceLoader() {
        boolean found = false;
        for (ExchangeWebSocketApiProvider provider : ServiceLoader.load(ExchangeWebSocketApiProvider.class)) {
            if (provider instanceof DeribitWebSocketApiProvider typedProvider) {
                found = true;
                assertEquals(Exchange.DERIBIT, typedProvider.getExchange());
                assertEquals(IDeribitWebSocketApi.class, typedProvider.getApiType());

                IDeribitWebSocketApi api = typedProvider.getWebSocketApi();
                assertNotNull(api);
                assertDoesNotThrow(api::disconnectAll);
            }
        }

        assertTrue(found, "Expected ServiceLoader to discover DeribitWebSocketApiProvider");
    }

    @Test
    void websocketApiRejectsBlankUrl() {
        assertThrows(IllegalArgumentException.class, () -> new DeribitWebSocketApi("  "));
    }
}
