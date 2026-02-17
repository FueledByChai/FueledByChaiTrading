package com.fueledbychai.broker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fueledbychai.broker.test.TestBroker;
import com.fueledbychai.data.Exchange;

public class BrokerFactoryTest {

    @Test
    public void testServiceLoaderRegistration() {
        assertTrue(BrokerFactory.isRegistered(Exchange.NYBOT));

        IBroker broker = BrokerFactory.getInstance(Exchange.NYBOT);
        assertNotNull(broker);
        assertEquals(TestBroker.class, broker.getClass());

        IBroker broker2 = BrokerFactory.getInstance(Exchange.NYBOT);
        assertSame(broker, broker2);
    }
}
