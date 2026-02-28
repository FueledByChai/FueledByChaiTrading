package com.fueledbychai.marketdata.paradex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;

class ParadexQuoteEngineTest {

    @Test
    void newSummaryUpdateSpotWithEmptyFundingRatePublishesQuote() throws Exception {
        Ticker spotTicker = newTicker("ETH-USD", InstrumentType.CRYPTO_SPOT, "0.01", 0);
        ParadexQuoteEngine engine = new ParadexQuoteEngine(registryWith(null, spotTicker));
        ILevel1Quote quote = captureNextLevel1Quote(engine, () -> engine.newSummaryUpdate(1771375190278L, "ETH-USD",
                "1992.9", "1994.45", "1991.76", "1994.62", "0", "329825.6947310004", "1994.62", ""));

        assertNotNull(quote);
        assertEquals("ETH-USD", quote.getTicker().getSymbol());
        assertFalse(quote.containsType(QuoteType.FUNDING_RATE_APR));
        assertFalse(quote.containsType(QuoteType.FUNDING_RATE_HOURLY_BPS));
    }

    @Test
    void newSummaryUpdatePerpWithFundingRatePublishesFundingQuotes() throws Exception {
        Ticker perpTicker = newTicker("ETH-USD-PERP", InstrumentType.PERPETUAL_FUTURES, "0.01", 8);
        ParadexQuoteEngine engine = new ParadexQuoteEngine(registryWith(perpTicker, null));
        ILevel1Quote quote = captureNextLevel1Quote(engine,
                () -> engine.newSummaryUpdate(1771375190278L, "ETH-USD-PERP", "1992.9", "1994.45", "1991.76",
                        "1994.62", "12.5", "329825.6947310004", "1994.62", "0.0008"));

        assertNotNull(quote);
        assertEquals(1.0, quote.getValue(QuoteType.FUNDING_RATE_HOURLY_BPS).doubleValue(), 1.0e-9);
        assertEquals(87.6, quote.getValue(QuoteType.FUNDING_RATE_APR).doubleValue(), 1.0e-9);
    }

    private static ILevel1Quote captureNextLevel1Quote(ParadexQuoteEngine engine, Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ILevel1Quote> quoteRef = new AtomicReference<>();
        Level1QuoteListener listener = quote -> {
            quoteRef.set(quote);
            latch.countDown();
        };
        engine.subscribeGlobalLevel1(listener);
        try {
            action.run();
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Expected a level1 quote notification");
            return quoteRef.get();
        } finally {
            engine.unsubscribeGlobalLevel1(listener);
        }
    }

    private static Ticker newTicker(String symbol, InstrumentType instrumentType, String minTick, int fundingInterval) {
        return new Ticker(symbol)
                .setExchange(Exchange.PARADEX)
                .setInstrumentType(instrumentType)
                .setMinimumTickSize(new BigDecimal(minTick))
                .setFundingRateInterval(fundingInterval);
    }

    private static ITickerRegistry registryWith(Ticker perpTicker, Ticker spotTicker) {
        return new ITickerRegistry() {
            @Override
            public Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString) {
                if (instrumentType == InstrumentType.PERPETUAL_FUTURES && perpTicker != null
                        && perpTicker.getSymbol().equals(tickerString)) {
                    return perpTicker;
                }
                if (instrumentType == InstrumentType.CRYPTO_SPOT && spotTicker != null
                        && spotTicker.getSymbol().equals(tickerString)) {
                    return spotTicker;
                }
                return null;
            }

            @Override
            public Ticker lookupByCommonSymbol(InstrumentType instrumentType, String commonSymbol) {
                return null;
            }

            @Override
            public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
                return null;
            }
        };
    }

}
