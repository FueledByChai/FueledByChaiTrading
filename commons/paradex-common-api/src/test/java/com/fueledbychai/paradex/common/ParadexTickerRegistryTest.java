package com.fueledbychai.paradex.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.paradex.common.api.IParadexRestApi;

/**
 * Unit tests for ParadexTickerRegistry.
 */
public class ParadexTickerRegistryTest {

    private ParadexTickerRegistry tickerRegistry;

    @BeforeEach
    public void setUp() {
        // Create a test instance without calling the constructor that initializes from
        // API
        // We'll test the method in isolation
        tickerRegistry = new TestableParadexTickerRegistry();
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_BTC_USD() {
        // Test BTC/USD -> BTC-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC/USD");
        assertEquals("BTC-USD-PERP", result, "BTC/USD should convert to BTC-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_BTC_USDT() {
        // Test BTC/USDT -> BTC-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC/USDT");
        assertEquals("BTC-USD-PERP", result, "BTC/USDT should convert to BTC-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_BTC_USDC() {
        // Test BTC/USDC -> BTC-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC/USDC");
        assertEquals("BTC-USD-PERP", result, "BTC/USDC should convert to BTC-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_ETH_USD() {
        // Test ETH/USD -> ETH-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "ETH/USD");
        assertEquals("ETH-USD-PERP", result, "ETH/USD should convert to ETH-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_ETH_USDT() {
        // Test ETH/USDT -> ETH-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "ETH/USDT");
        assertEquals("ETH-USD-PERP", result, "ETH/USDT should convert to ETH-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_ETH_USDC() {
        // Test ETH/USDC -> ETH-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "ETH/USDC");
        assertEquals("ETH-USD-PERP", result, "ETH/USDC should convert to ETH-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_Other_Pairs() {
        // Test other currency pairs
        String result1 = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "SOL/USDT");
        assertEquals("SOL-USD-PERP", result1, "SOL/USDT should convert to SOL-USD-PERP");

        String result2 = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "AVAX/USDC");
        assertEquals("AVAX-USD-PERP", result2, "AVAX/USDC should convert to AVAX-USD-PERP");

        String result3 = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "DOGE/USD");
        assertEquals("DOGE-USD-PERP", result3, "DOGE/USD should convert to DOGE-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_EdgeCases() {
        // Test edge cases and different formats
        String result1 = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC/EUR");
        assertEquals("BTC-EUR-PERP", result1, "BTC/EUR should convert to BTC-EUR-PERP (not USD)");

        String result2 = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "BTCUSD");
        assertEquals("BTCUSD-PERP", result2, "BTCUSD should convert to BTCUSD-PERP (no slash)");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_SpotPairs() {
        String result1 = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.CRYPTO_SPOT, "ETH/USD");
        assertEquals("ETH-USD", result1, "ETH/USD should convert to ETH-USD for spot");

        String result2 = tickerRegistry.commonSymbolToExchangeSymbol(InstrumentType.CRYPTO_SPOT, "ETH/USDT");
        assertEquals("ETH-USD", result2, "ETH/USDT should normalize and convert to ETH-USD for spot");
    }

    @Test
    public void testInitializeRegistersPerpAndSpotDescriptors() {
        InstrumentDescriptor perpDescriptor = new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES,
                Exchange.PARADEX, "BTC", "BTC-USD-PERP", "BTC", "USD", new BigDecimal("0.001"), new BigDecimal("0.1"),
                10, BigDecimal.ONE, 8, BigDecimal.ONE, 50, "");
        InstrumentDescriptor spotDescriptor = new InstrumentDescriptor(InstrumentType.CRYPTO_SPOT, Exchange.PARADEX,
                "ETH", "ETH-USD", "ETH", "USD", new BigDecimal("0.0001"), new BigDecimal("0.01"), 10, BigDecimal.ONE,
                0, BigDecimal.ONE, 1, "");
        AtomicInteger callCount = new AtomicInteger();
        IParadexRestApi api = (IParadexRestApi) Proxy.newProxyInstance(
                IParadexRestApi.class.getClassLoader(),
                new Class[] { IParadexRestApi.class },
                (proxy, method, args) -> {
                    if ("getAllInstrumentsForTypes".equals(method.getName())) {
                        callCount.incrementAndGet();
                        return new InstrumentDescriptor[] { perpDescriptor, spotDescriptor };
                    }
                    throw new UnsupportedOperationException("Unexpected call in test: " + method.getName());
                });

        TestableParadexTickerRegistry registry = new TestableParadexTickerRegistry();
        registry.initializeWithApi(api);

        assertNotNull(registry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC-USD-PERP"));
        assertNotNull(registry.lookupByBrokerSymbol(InstrumentType.CRYPTO_SPOT, "ETH-USD"));
        assertNotNull(registry.lookupByCommonSymbol(InstrumentType.CRYPTO_SPOT, "ETH/USDT"));
        assertEquals(1, callCount.get(), "Expected getAllInstrumentsForTypes() to be called once");
    }

    /**
     * Testable subclass that doesn't initialize from the REST API. This allows us
     * to test the commonSymbolToExchangeSymbol method in isolation without needing
     * to mock the REST API or have network connectivity.
     */
    private static class TestableParadexTickerRegistry extends ParadexTickerRegistry {

        public TestableParadexTickerRegistry() {
            super();
        }

        void initializeWithApi(IParadexRestApi api) {
            this.restApi = api;
            super.initialize();
        }

        @Override
        protected void initialize() {
            // Skip API initialization for tests.
        }
    }
}
