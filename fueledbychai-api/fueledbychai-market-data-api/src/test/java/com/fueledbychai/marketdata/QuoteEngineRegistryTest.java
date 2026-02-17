package com.fueledbychai.marketdata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.test.TestQuoteEngine;

public class QuoteEngineRegistryTest {

    @Test
    public void testServiceLoaderRegistration() {
        assertTrue(QuoteEngine.isRegistered(Exchange.BOX));

        QuoteEngine engine = QuoteEngine.getInstance(Exchange.BOX);
        assertNotNull(engine);
        assertEquals(TestQuoteEngine.class, engine.getClass());

        QuoteEngine engine2 = QuoteEngine.getInstance(Exchange.BOX);
        assertSame(engine, engine2);
    }
}
