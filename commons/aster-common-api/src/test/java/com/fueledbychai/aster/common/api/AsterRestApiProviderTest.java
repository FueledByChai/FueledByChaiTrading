package com.fueledbychai.aster.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

class AsterRestApiProviderTest {

    @Test
    void restProviderIsDiscoverableThroughServiceLoader() {
        boolean found = false;
        for (ExchangeRestApiProvider provider : ServiceLoader.load(ExchangeRestApiProvider.class)) {
            if (provider instanceof AsterRestApiProvider typedProvider) {
                found = true;
                assertEquals(Exchange.ASTER, typedProvider.getExchange());
                assertEquals(IAsterRestApi.class, typedProvider.getApiType());
                assertNotNull(typedProvider.getPublicApi());
                assertNotNull(typedProvider.getApi());
                if (typedProvider.isPrivateApiAvailable()) {
                    assertNotNull(typedProvider.getPrivateApi());
                }
            }
        }

        assertTrue(found, "Expected ServiceLoader to discover AsterRestApiProvider");
    }

    @Test
    void restApiRejectsBlankBaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> new AsterRestApi("  "));
    }
}
