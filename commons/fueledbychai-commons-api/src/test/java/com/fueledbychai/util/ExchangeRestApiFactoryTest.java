package com.fueledbychai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.test.TestExchangeRestApi;

public class ExchangeRestApiFactoryTest {

    @Test
    public void testServiceLoaderRegistrationAndCaching() {
        assertTrue(ExchangeRestApiFactory.isRegistered(Exchange.CBOE));
        assertTrue(ExchangeRestApiFactory.isPrivateApiAvailable(Exchange.CBOE));

        TestExchangeRestApi publicApi1 = ExchangeRestApiFactory.getPublicApi(Exchange.CBOE, TestExchangeRestApi.class);
        TestExchangeRestApi publicApi2 = ExchangeRestApiFactory.getPublicApi(Exchange.CBOE, TestExchangeRestApi.class);
        assertSame(publicApi1, publicApi2);
        assertEquals("public", publicApi1.getKind());

        TestExchangeRestApi defaultApi1 = ExchangeRestApiFactory.getApi(Exchange.CBOE, TestExchangeRestApi.class);
        TestExchangeRestApi defaultApi2 = ExchangeRestApiFactory.getApi(Exchange.CBOE, TestExchangeRestApi.class);
        assertSame(defaultApi1, defaultApi2);
        assertEquals("default", defaultApi1.getKind());

        TestExchangeRestApi privateApi1 = ExchangeRestApiFactory.getPrivateApi(Exchange.CBOE, TestExchangeRestApi.class);
        TestExchangeRestApi privateApi2 = ExchangeRestApiFactory.getPrivateApi(Exchange.CBOE, TestExchangeRestApi.class);
        assertSame(privateApi1, privateApi2);
        assertEquals("private", privateApi1.getKind());
    }

    @Test
    public void testTypeValidation() {
        assertThrows(IllegalStateException.class, () -> ExchangeRestApiFactory.getApi(Exchange.CBOE, String.class));
    }
}
