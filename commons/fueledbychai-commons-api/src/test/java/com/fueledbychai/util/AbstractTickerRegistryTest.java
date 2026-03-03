package com.fueledbychai.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.data.Ticker.Right;
import com.fueledbychai.data.TickerTranslator;

public class AbstractTickerRegistryTest {

    private static final DateTimeFormatter BASIC_ISO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private TestableTickerRegistry registry;

    @Before
    public void setUp() {
        registry = new TestableTickerRegistry();
        registry.seed(
                descriptor(InstrumentType.PERPETUAL_FUTURES, "BTC/USD", "BTC-USD-PERP", "BTC", "USD", "perp-btc"),
                descriptor(InstrumentType.OPTION, "BTC/USD-20260327-85000-C", "BTC-20260327-85000-C", "BTC", "USD",
                        "option-btc-1"),
                descriptor(InstrumentType.OPTION, "BTC/USD-20260327-90000-P", "BTC-20260327-90000-P", "BTC", "USD",
                        "option-btc"),
                descriptor(InstrumentType.OPTION, "BTC/USD-20260403-95000-C", "BTC-20260403-95000-C", "BTC", "USD",
                        "option-btc-2"),
                descriptor(InstrumentType.OPTION, "ETH/USD-20260327-2500-C", "ETH-20260327-2500-C", "ETH", "USD",
                        "option-eth"),
                descriptor(InstrumentType.CRYPTO_SPOT, "ETH/USDC", "ETHUSDC", "ETH", "USDC", "spot-eth"));
    }

    @Test
    public void testGetAllTickersReturnsEveryRegisteredTicker() {
        Ticker[] tickers = registry.getAllTickers();

        assertEquals(6, tickers.length);
        assertEquals(
                expectedSymbols("BTC-20260327-85000-C", "BTC-20260327-90000-P", "BTC-20260403-95000-C",
                        "BTC-USD-PERP", "ETH-20260327-2500-C", "ETHUSDC"),
                collectSymbols(tickers));
    }

    @Test
    public void testGetAllTickersForTypeReturnsOnlyMatchingTickers() {
        Ticker[] tickers = registry.getAllTickersForType(InstrumentType.OPTION);

        assertEquals(4, tickers.length);
        assertEquals(expectedSymbols("BTC-20260327-85000-C", "BTC-20260327-90000-P", "BTC-20260403-95000-C",
                "ETH-20260327-2500-C"), collectSymbols(tickers));
    }

    @Test
    public void testGetOptionChainByUnderlyingReturnsMatchingContracts() {
        Ticker[] tickers = registry.getOptionChain("BTC");

        assertEquals(3, tickers.length);
        assertEquals("BTC-20260327-85000-C", tickers[0].getSymbol());
        assertEquals("BTC-20260327-90000-P", tickers[1].getSymbol());
        assertEquals("BTC-20260403-95000-C", tickers[2].getSymbol());
    }

    @Test
    public void testGetOptionChainByExpiryReturnsMatchingContracts() {
        Ticker[] tickers = registry.getOptionChain("BTC", 2026, 3, 27);

        assertEquals(2, tickers.length);
        assertEquals("BTC-20260327-85000-C", tickers[0].getSymbol());
        assertEquals("BTC-20260327-90000-P", tickers[1].getSymbol());
    }

    @Test
    public void testGetOptionChainByRightReturnsOnlyCalls() {
        Ticker[] tickers = registry.getOptionChain("BTC", 0, 0, 0, ITickerRegistry.OptionRightFilter.CALL);

        assertEquals(2, tickers.length);
        assertEquals("BTC-20260327-85000-C", tickers[0].getSymbol());
        assertEquals("BTC-20260403-95000-C", tickers[1].getSymbol());
    }

    @Test
    public void testGetOptionChainAcceptsPartialExpiryFilters() {
        Ticker[] tickers = registry.getOptionChain("BTC", 2026, 3, 0);

        assertEquals(2, tickers.length);
        assertEquals("BTC-20260327-85000-C", tickers[0].getSymbol());
        assertEquals("BTC-20260327-90000-P", tickers[1].getSymbol());
    }

    @Test
    public void testGetOptionChainForUnsupportedUnderlyingReturnsEmptyArray() {
        assertEquals(0, registry.getOptionChain("SOL").length);
    }

    @Test
    public void testGetOptionChainRejectsInvalidInputs() {
        assertIllegalArgument(() -> registry.getOptionChain(null));
        assertIllegalArgument(() -> registry.getOptionChain("   "));
        assertIllegalArgument(() -> registry.getOptionChain("BTC", -1, 0, 0));
        assertIllegalArgument(() -> registry.getOptionChain("BTC", 2026, 13, 0));
        assertIllegalArgument(() -> registry.getOptionChain("BTC", 2026, 3, 32));
    }

    @Test
    public void testGetAllTickersForUnsupportedTypeReturnsEmptyArray() {
        assertEquals(0, registry.getAllTickersForType(InstrumentType.STOCK).length);
    }

    @Test
    public void testGetAllTickersForTypeRejectsNullInstrumentType() {
        assertIllegalArgument(() -> registry.getAllTickersForType(null));
    }

    private void assertIllegalArgument(Runnable runnable) {
        try {
            runnable.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private Set<String> expectedSymbols(String... symbols) {
        return new HashSet<>(Arrays.asList(symbols));
    }

    private Set<String> collectSymbols(Ticker[] tickers) {
        Set<String> symbols = new HashSet<>();
        for (Ticker ticker : tickers) {
            symbols.add(ticker.getSymbol());
        }
        return symbols;
    }

    private InstrumentDescriptor descriptor(InstrumentType instrumentType, String commonSymbol, String exchangeSymbol,
            String baseCurrency, String quoteCurrency, String instrumentId) {
        return new InstrumentDescriptor(instrumentType, Exchange.NYMEX, commonSymbol, exchangeSymbol, baseCurrency,
                quoteCurrency, BigDecimal.ONE, new BigDecimal("0.01"), 1, BigDecimal.ONE, 8, BigDecimal.ONE, 10,
                instrumentId);
    }

    private static class TestableTickerRegistry extends AbstractTickerRegistry {

        TestableTickerRegistry() {
            super(new OptionAwareTickerTranslator());
        }

        void seed(InstrumentDescriptor... descriptors) {
            registerDescriptors(descriptors);
        }

        @Override
        protected boolean supportsInstrumentType(InstrumentType instrumentType) {
            return instrumentType == InstrumentType.PERPETUAL_FUTURES
                    || instrumentType == InstrumentType.OPTION
                    || instrumentType == InstrumentType.CRYPTO_SPOT;
        }

        @Override
        public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
            return commonSymbol;
        }
    }

    private static class OptionAwareTickerTranslator extends TickerTranslator {

        @Override
        public Ticker translateTicker(InstrumentDescriptor descriptor) {
            Ticker ticker = super.translateTicker(descriptor);
            if (descriptor == null || descriptor.getInstrumentType() != InstrumentType.OPTION) {
                return ticker;
            }

            String symbol = descriptor.getExchangeSymbol();
            if (symbol == null || symbol.isEmpty()) {
                return ticker;
            }

            String[] parts = symbol.split("-");
            if (parts.length < 4) {
                return ticker;
            }

            LocalDate expiry = LocalDate.parse(parts[1], BASIC_ISO_DATE);
            ticker.setExpiryYear(expiry.getYear());
            ticker.setExpiryMonth(expiry.getMonthValue());
            ticker.setExpiryDay(expiry.getDayOfMonth());
            ticker.setStrike(new BigDecimal(parts[2]));

            if ("C".equals(parts[3])) {
                ticker.setRight(Right.CALL);
            } else if ("P".equals(parts[3])) {
                ticker.setRight(Right.PUT);
            }

            return ticker;
        }
    }
}
