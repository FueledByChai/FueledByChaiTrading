package com.fueledbychai.paradex.example.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fueledbychai.broker.BrokerFactory;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.historicaldata.HistoricalDataProviderFactory;
import com.fueledbychai.historicaldata.IHistoricalDataProvider;
import com.fueledbychai.marketdata.QuoteEngine;

public class ExchangeFactoryWiringIntegrationTest {

    @Test
    public void serviceLoaderFactoriesResolveConfiguredProviders() {
        assertTrue(QuoteEngine.isRegistered(Exchange.HYPERLIQUID));
        QuoteEngine quoteEngine1 = QuoteEngine.getInstance(Exchange.HYPERLIQUID);
        QuoteEngine quoteEngine2 = QuoteEngine.getInstance(Exchange.HYPERLIQUID);
        assertNotNull(quoteEngine1);
        assertSame(quoteEngine1, quoteEngine2);

        // Broker providers can require authenticated websocket initialization.
        // Validate ServiceLoader registration without forcing live broker construction.
        assertTrue(BrokerFactory.isRegistered(Exchange.HYPERLIQUID));

        assertTrue(HistoricalDataProviderFactory.isRegistered(Exchange.PARADEX));
        IHistoricalDataProvider historical1 = HistoricalDataProviderFactory.getInstance(Exchange.PARADEX);
        IHistoricalDataProvider historical2 = HistoricalDataProviderFactory.getInstance(Exchange.PARADEX);
        assertNotNull(historical1);
        assertSame(historical1, historical2);
    }
}
