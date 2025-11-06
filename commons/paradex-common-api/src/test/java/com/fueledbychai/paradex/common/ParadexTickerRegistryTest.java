package com.fueledbychai.paradex.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        String result = tickerRegistry.commonSymbolToExchangeSymbol("BTC/USD");
        assertEquals("BTC-USD-PERP", result, "BTC/USD should convert to BTC-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_BTC_USDT() {
        // Test BTC/USDT -> BTC-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol("BTC/USDT");
        assertEquals("BTC-USD-PERP", result, "BTC/USDT should convert to BTC-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_BTC_USDC() {
        // Test BTC/USDC -> BTC-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol("BTC/USDC");
        assertEquals("BTC-USD-PERP", result, "BTC/USDC should convert to BTC-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_ETH_USD() {
        // Test ETH/USD -> ETH-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol("ETH/USD");
        assertEquals("ETH-USD-PERP", result, "ETH/USD should convert to ETH-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_ETH_USDT() {
        // Test ETH/USDT -> ETH-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol("ETH/USDT");
        assertEquals("ETH-USD-PERP", result, "ETH/USDT should convert to ETH-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_ETH_USDC() {
        // Test ETH/USDC -> ETH-USD-PERP
        String result = tickerRegistry.commonSymbolToExchangeSymbol("ETH/USDC");
        assertEquals("ETH-USD-PERP", result, "ETH/USDC should convert to ETH-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_Other_Pairs() {
        // Test other currency pairs
        String result1 = tickerRegistry.commonSymbolToExchangeSymbol("SOL/USDT");
        assertEquals("SOL-USD-PERP", result1, "SOL/USDT should convert to SOL-USD-PERP");

        String result2 = tickerRegistry.commonSymbolToExchangeSymbol("AVAX/USDC");
        assertEquals("AVAX-USD-PERP", result2, "AVAX/USDC should convert to AVAX-USD-PERP");

        String result3 = tickerRegistry.commonSymbolToExchangeSymbol("DOGE/USD");
        assertEquals("DOGE-USD-PERP", result3, "DOGE/USD should convert to DOGE-USD-PERP");
    }

    @Test
    public void testCommonSymbolToExchangeSymbol_EdgeCases() {
        // Test edge cases and different formats
        String result1 = tickerRegistry.commonSymbolToExchangeSymbol("BTC/EUR");
        assertEquals("BTC-EUR-PERP", result1, "BTC/EUR should convert to BTC-EUR-PERP (not USD)");

        String result2 = tickerRegistry.commonSymbolToExchangeSymbol("BTCUSD");
        assertEquals("BTCUSD-PERP", result2, "BTCUSD should convert to BTCUSD-PERP (no slash)");
    }

    /**
     * Testable subclass that doesn't initialize from the REST API. This allows us
     * to test the commonSymbolToExchangeSymbol method in isolation without needing
     * to mock the REST API or have network connectivity.
     */
    private static class TestableParadexTickerRegistry extends ParadexTickerRegistry {

        // Override the constructor to prevent API initialization
        public TestableParadexTickerRegistry() {
            // Don't call super() to avoid API initialization
            // Just initialize the necessary fields for our test
            super.tickerMap = new java.util.HashMap<>();
            super.commonSymbolMap = new java.util.HashMap<>();
            super.descriptorMap = new java.util.HashMap<>();
        }
    }
}