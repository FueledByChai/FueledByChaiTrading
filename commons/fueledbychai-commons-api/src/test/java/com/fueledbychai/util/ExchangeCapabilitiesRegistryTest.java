package com.fueledbychai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;

public class ExchangeCapabilitiesRegistryTest {

    @Test
    public void testServiceLoaderRegistration() {
        assertTrue(ExchangeCapabilitiesRegistry.isRegistered(Exchange.SEHKNTL));

        ExchangeCapabilities capabilities = ExchangeCapabilitiesRegistry.getCapabilities(Exchange.SEHKNTL);
        assertEquals(Exchange.SEHKNTL, capabilities.getExchange());
        assertTrue(capabilities.supportsStreaming());
        assertTrue(capabilities.supportsHistoricalData());
        assertTrue(capabilities.getInstrumentTypes().contains(InstrumentType.CRYPTO_SPOT));
    }
}
