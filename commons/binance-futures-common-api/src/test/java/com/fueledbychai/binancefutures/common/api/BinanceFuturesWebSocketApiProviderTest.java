package com.fueledbychai.binancefutures.common.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

class BinanceFuturesWebSocketApiProviderTest {

    @Test
    void websocketProviderIsDiscoverableThroughServiceLoader() {
        boolean found = false;
        for (ExchangeWebSocketApiProvider provider : ServiceLoader.load(ExchangeWebSocketApiProvider.class)) {
            if (provider instanceof BinanceFuturesWebSocketApiProvider typedProvider) {
                found = true;
                assertEquals(Exchange.BINANCE_FUTURES, typedProvider.getExchange());
                assertEquals(IBinanceFuturesWebSocketApi.class, typedProvider.getApiType());

                IBinanceFuturesWebSocketApi api = typedProvider.getWebSocketApi();
                assertNotNull(api);
                assertDoesNotThrow(api::connect);
                assertDoesNotThrow(api::connectOrderEntryWebSocket);
                assertDoesNotThrow(api::disconnectAll);
            }
        }

        assertTrue(found, "Expected ServiceLoader to discover BinanceFuturesWebSocketApiProvider");
    }

    @Test
    void websocketApiRejectsBlankUrl() {
        assertThrows(IllegalArgumentException.class, () -> new BinanceFuturesWebSocketApi("  "));
    }
}
