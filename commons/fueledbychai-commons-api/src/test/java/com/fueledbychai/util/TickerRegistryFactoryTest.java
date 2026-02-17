package com.fueledbychai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.test.TestTickerRegistry;

public class TickerRegistryFactoryTest {

    @Test
    public void testServiceLoaderRegistration() {
        assertTrue(TickerRegistryFactory.isRegistered(Exchange.NYMEX));

        ITickerRegistry registry = TickerRegistryFactory.getInstance(Exchange.NYMEX);
        assertNotNull(registry);
        assertEquals(TestTickerRegistry.class, registry.getClass());

        ITickerRegistry registry2 = TickerRegistryFactory.getInstance(Exchange.NYMEX);
        assertSame(registry, registry2);
    }
}
