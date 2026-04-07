package com.fueledbychai.marketdata.paradex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.util.ITickerRegistry;

import static org.mockito.Mockito.mock;

class ParadexQuoteEngineTest {

    @Test
    void newSummaryUpdateSpotWithEmptyFundingRatePublishesQuote() throws Exception {
        Ticker spotTicker = newTicker("ETH-USD", InstrumentType.CRYPTO_SPOT, "0.01", 0);
        ParadexQuoteEngine engine = new ParadexQuoteEngine(mock(IParadexRestApi.class), registryWith(null, spotTicker));
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
        ParadexQuoteEngine engine = new ParadexQuoteEngine(mock(IParadexRestApi.class), registryWith(perpTicker, null));
        ILevel1Quote quote = captureNextLevel1Quote(engine,
                () -> engine.newSummaryUpdate(1771375190278L, "ETH-USD-PERP", "1992.9", "1994.45", "1991.76",
                        "1994.62", "12.5", "329825.6947310004", "1994.62", "0.0008"));

        assertNotNull(quote);
        assertEquals(1.0, quote.getValue(QuoteType.FUNDING_RATE_HOURLY_BPS).doubleValue(), 1.0e-9);
        assertEquals(87.6, quote.getValue(QuoteType.FUNDING_RATE_APR).doubleValue(), 1.0e-9);
    }

    @Test
    void newSummaryUpdateOptionWithEmptyFieldsDoesNotThrow() throws Exception {
        // Newly-listed/idle option markets emit empty strings for last/mark/oi/volume
        // until there's activity. We must publish a quote without throwing.
        Ticker optionTicker = newTicker("BTC-USD-26JUN26-67000-C", InstrumentType.OPTION, "0.01", 0);
        ParadexQuoteEngine engine = new ParadexQuoteEngine(mock(IParadexRestApi.class),
                optionRegistryByBrokerSymbol(optionTicker));

        ILevel1Quote quote = captureNextLevel1Quote(engine, () -> engine.newSummaryUpdate(1771375190278L,
                "BTC-USD-26JUN26-67000-C", "", "", "", "", "", "", "", ""));

        assertNotNull(quote);
        assertEquals("BTC-USD-26JUN26-67000-C", quote.getTicker().getSymbol());
        assertFalse(quote.containsType(QuoteType.LAST));
        assertFalse(quote.containsType(QuoteType.MARK_PRICE));
        assertFalse(quote.containsType(QuoteType.OPEN_INTEREST));
        assertFalse(quote.containsType(QuoteType.VOLUME));
        assertFalse(quote.containsType(QuoteType.UNDERLYING_PRICE));
        assertFalse(quote.containsType(QuoteType.VOLUME_NOTIONAL));
        assertFalse(quote.containsType(QuoteType.OPEN_INTEREST_NOTIONAL));
    }

    @Test
    void newSummaryUpdateOptionWithPartialFieldsPopulatesOnlyAvailableQuotes() throws Exception {
        Ticker optionTicker = newTicker("BTC-USD-26JUN26-67000-C", InstrumentType.OPTION, "0.01", 0);
        ParadexQuoteEngine engine = new ParadexQuoteEngine(mock(IParadexRestApi.class),
                optionRegistryByBrokerSymbol(optionTicker));

        // Only last/mark/volume populated; openInterest and underlyingPrice empty.
        ILevel1Quote quote = captureNextLevel1Quote(engine, () -> engine.newSummaryUpdate(1771375190278L,
                "BTC-USD-26JUN26-67000-C", "", "", "1500.5", "1505.0", "", "10", "", ""));

        assertNotNull(quote);
        assertEquals(new BigDecimal("1500.50"), quote.getValue(QuoteType.LAST));
        assertEquals(new BigDecimal("1505.00"), quote.getValue(QuoteType.MARK_PRICE));
        assertEquals(new BigDecimal("10"), quote.getValue(QuoteType.VOLUME));
        // Notional volume = lastPrice * volume24h, both available
        assertEquals(new BigDecimal("15005.00"), quote.getValue(QuoteType.VOLUME_NOTIONAL));
        // Open interest fields not present
        assertFalse(quote.containsType(QuoteType.OPEN_INTEREST));
        assertFalse(quote.containsType(QuoteType.OPEN_INTEREST_NOTIONAL));
        assertFalse(quote.containsType(QuoteType.UNDERLYING_PRICE));
    }

    private static ITickerRegistry optionRegistryByBrokerSymbol(Ticker optionTicker) {
        return new ITickerRegistry() {
            @Override
            public Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString) {
                if (instrumentType == InstrumentType.OPTION && optionTicker.getSymbol().equals(tickerString)) {
                    return optionTicker;
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

    @Test
    void requestLevel1SnapshotResolvesCommonSymbolToExchangeSymbol() {
        // Common-symbol form (with slash). The engine must resolve it via the
        // ticker registry to the proper Paradex exchange symbol before calling
        // getBBO; otherwise the slash splits the URL and Paradex returns 401.
        Ticker commonSymbolTicker = new Ticker("BTC/USD-20260626-67000-C")
                .setExchange(Exchange.PARADEX)
                .setInstrumentType(InstrumentType.OPTION)
                .setMinimumTickSize(new BigDecimal("0.01"));
        Ticker resolvedTicker = newTicker("BTC-USD-26JUN26-67000-C", InstrumentType.OPTION, "0.01", 0);

        IParadexRestApi restApi = mock(IParadexRestApi.class);
        when(restApi.getBBO("BTC-USD-26JUN26-67000-C"))
                .thenReturn(com.google.gson.JsonParser
                        .parseString("{\"market\":\"BTC-USD-26JUN26-67000-C\",\"bid\":\"100\",\"bid_size\":\"1\","
                                + "\"ask\":\"110\",\"ask_size\":\"2\"}")
                        .getAsJsonObject());

        ITickerRegistry registry = optionRegistry(commonSymbolTicker.getSymbol(), resolvedTicker);
        ParadexQuoteEngine engine = new ParadexQuoteEngine(restApi, registry);

        ILevel1Quote quote = engine.requestLevel1Snapshot(commonSymbolTicker);

        verify(restApi).getBBO(eq("BTC-USD-26JUN26-67000-C"));
        assertNotNull(quote);
    }

    @Test
    void requestLevel1SnapshotPassesExchangeSymbolThrough() {
        // Exchange-symbol form (no slash). The engine should pass it directly.
        Ticker perpTicker = newTicker("BTC-USD-PERP", InstrumentType.PERPETUAL_FUTURES, "0.1", 8);

        IParadexRestApi restApi = mock(IParadexRestApi.class);
        when(restApi.getBBO("BTC-USD-PERP"))
                .thenReturn(com.google.gson.JsonParser
                        .parseString("{\"market\":\"BTC-USD-PERP\",\"bid\":\"100\",\"bid_size\":\"1\","
                                + "\"ask\":\"110\",\"ask_size\":\"2\"}")
                        .getAsJsonObject());

        ParadexQuoteEngine engine = new ParadexQuoteEngine(restApi, registryWith(perpTicker, null));

        ILevel1Quote quote = engine.requestLevel1Snapshot(perpTicker);

        verify(restApi).getBBO(eq("BTC-USD-PERP"));
        assertNotNull(quote);
    }

    @Test
    void requestLevel1SnapshotResolvesSlashSeparatedOptionSymbol() {
        // Slash-separated mirror of the Paradex exchange symbol — the form
        // some upstream services pass because '/' is more URL-friendly.
        Ticker badTicker = new Ticker("BTC/USD/24APR26/75000/C")
                .setExchange(Exchange.PARADEX)
                .setInstrumentType(InstrumentType.OPTION)
                .setMinimumTickSize(new BigDecimal("0.01"));
        Ticker canonicalTicker = newTicker("BTC-USD-24APR26-75000-C", InstrumentType.OPTION, "0.01", 0);

        IParadexRestApi restApi = mock(IParadexRestApi.class);
        when(restApi.getBBO("BTC-USD-24APR26-75000-C"))
                .thenReturn(com.google.gson.JsonParser
                        .parseString("{\"market\":\"BTC-USD-24APR26-75000-C\",\"bid\":\"100\",\"bid_size\":\"1\","
                                + "\"ask\":\"110\",\"ask_size\":\"2\"}")
                        .getAsJsonObject());

        // Registry that returns the canonical ticker via lookupByCommonSymbol
        // for the slash-separated input.
        ITickerRegistry registry = optionRegistry("BTC/USD/24APR26/75000/C", canonicalTicker);
        ParadexQuoteEngine engine = new ParadexQuoteEngine(restApi, registry);

        ILevel1Quote quote = engine.requestLevel1Snapshot(badTicker);

        verify(restApi).getBBO(eq("BTC-USD-24APR26-75000-C"));
        assertNotNull(quote);
        // The published quote should carry the canonical Ticker, so downstream
        // listeners see the proper Paradex exchange symbol, not the user's input.
        assertEquals("BTC-USD-24APR26-75000-C", quote.getTicker().getSymbol());
    }

    @Test
    void canonicalizePrefersBrokerSymbolLookupBeforeCommonSymbolLookup() {
        // A ticker whose symbol already matches a registered broker symbol
        // should be canonicalized via the fast (lookupByBrokerSymbol) path,
        // even if the input ticker instance is a different object than the
        // registered one. This guarantees downstream maps key on the
        // registry-cached canonical Ticker.
        Ticker registered = newTicker("BTC-USD-PERP", InstrumentType.PERPETUAL_FUTURES, "0.1", 8);
        Ticker userInput = newTicker("BTC-USD-PERP", InstrumentType.PERPETUAL_FUTURES, "0.1", 8);

        IParadexRestApi restApi = mock(IParadexRestApi.class);
        when(restApi.getBBO("BTC-USD-PERP"))
                .thenReturn(com.google.gson.JsonParser
                        .parseString("{\"market\":\"BTC-USD-PERP\",\"bid\":\"100\",\"bid_size\":\"1\","
                                + "\"ask\":\"110\",\"ask_size\":\"2\"}")
                        .getAsJsonObject());

        ParadexQuoteEngine engine = new ParadexQuoteEngine(restApi, registryWith(registered, null));
        ILevel1Quote quote = engine.requestLevel1Snapshot(userInput);

        verify(restApi).getBBO(eq("BTC-USD-PERP"));
        assertNotNull(quote);
        // The published quote's ticker should be the registry-cached instance,
        // not the user's input — same symbol, same instance.
        assertSame(registered, quote.getTicker());
    }

    private static ITickerRegistry optionRegistry(String commonSymbol, Ticker resolved) {
        return new ITickerRegistry() {
            @Override
            public Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString) {
                return null;
            }

            @Override
            public Ticker lookupByCommonSymbol(InstrumentType instrumentType, String requestedCommonSymbol) {
                if (instrumentType == InstrumentType.OPTION && commonSymbol.equals(requestedCommonSymbol)) {
                    return resolved;
                }
                return null;
            }

            @Override
            public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String requestedCommonSymbol) {
                return null;
            }
        };
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
