package com.fueledbychai.aster.common.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

class AsterWebSocketApiProviderTest {

    @Test
    void websocketProviderIsDiscoverableThroughServiceLoader() {
        boolean found = false;
        for (ExchangeWebSocketApiProvider provider : ServiceLoader.load(ExchangeWebSocketApiProvider.class)) {
            if (provider instanceof AsterWebSocketApiProvider typedProvider) {
                found = true;
                assertEquals(Exchange.ASTER, typedProvider.getExchange());
                assertEquals(IAsterWebSocketApi.class, typedProvider.getApiType());

                IAsterWebSocketApi api = typedProvider.getWebSocketApi();
                assertNotNull(api);
                assertDoesNotThrow(api::connect);
                assertDoesNotThrow(api::connectOrderEntryWebSocket);
                assertDoesNotThrow(api::disconnectAll);
            }
        }

        assertTrue(found, "Expected ServiceLoader to discover AsterWebSocketApiProvider");
    }

    @Test
    void websocketApiRejectsBlankUrl() {
        assertThrows(IllegalArgumentException.class, () -> new AsterWebSocketApi("  "));
    }
}
