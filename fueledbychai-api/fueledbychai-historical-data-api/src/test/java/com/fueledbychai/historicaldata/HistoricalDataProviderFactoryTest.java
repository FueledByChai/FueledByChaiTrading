package com.fueledbychai.historicaldata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.historicaldata.test.TestHistoricalDataProvider;

public class HistoricalDataProviderFactoryTest {

    @Test
    public void testServiceLoaderRegistration() {
        assertTrue(HistoricalDataProviderFactory.isRegistered(Exchange.SGX));

        IHistoricalDataProvider provider = HistoricalDataProviderFactory.getInstance(Exchange.SGX);
        assertNotNull(provider);
        assertEquals(TestHistoricalDataProvider.class, provider.getClass());

        IHistoricalDataProvider provider2 = HistoricalDataProviderFactory.getInstance(Exchange.SGX);
        assertSame(provider, provider2);
    }
}
