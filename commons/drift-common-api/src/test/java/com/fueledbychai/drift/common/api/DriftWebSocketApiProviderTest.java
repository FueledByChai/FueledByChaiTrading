package com.fueledbychai.drift.common.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

class DriftWebSocketApiProviderTest {

    @Test
    void websocketProviderIsDiscoverableThroughServiceLoader() {
        boolean found = false;
        for (ExchangeWebSocketApiProvider provider : ServiceLoader.load(ExchangeWebSocketApiProvider.class)) {
            if (provider instanceof DriftWebSocketApiProvider typedProvider) {
                found = true;
                assertEquals(Exchange.DRIFT, typedProvider.getExchange());
                assertEquals(IDriftWebSocketApi.class, typedProvider.getApiType());

                IDriftWebSocketApi api = typedProvider.getWebSocketApi();
                assertNotNull(api);
                assertDoesNotThrow(api::disconnectAll);
            }
        }

        assertTrue(found, "Expected ServiceLoader to discover DriftWebSocketApiProvider");
    }

    @Test
    void websocketApiRejectsBlankUrl() {
        assertThrows(IllegalArgumentException.class, () -> new DriftWebSocketApi("  ", "ws://gateway.unit.test"));
    }
}
