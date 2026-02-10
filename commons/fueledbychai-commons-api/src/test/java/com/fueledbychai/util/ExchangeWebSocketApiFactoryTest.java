package com.fueledbychai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.test.TestExchangeWebSocketApi;

public class ExchangeWebSocketApiFactoryTest {

    @Test
    public void testServiceLoaderRegistrationAndCaching() {
        assertTrue(ExchangeWebSocketApiFactory.isRegistered(Exchange.CFE));

        TestExchangeWebSocketApi api1 = ExchangeWebSocketApiFactory.getApi(Exchange.CFE, TestExchangeWebSocketApi.class);
        TestExchangeWebSocketApi api2 = ExchangeWebSocketApiFactory.getApi(Exchange.CFE, TestExchangeWebSocketApi.class);

        assertSame(api1, api2);
        assertEquals("streaming", api1.getKind());
    }

    @Test
    public void testTypeValidation() {
        assertThrows(IllegalStateException.class, () -> ExchangeWebSocketApiFactory.getApi(Exchange.CFE, String.class));
    }
}
