package com.fueledbychai.paradex.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.data.Ticker.Right;

class ParadexTickerTranslatorTest {

    private ParadexTickerTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new ParadexTickerTranslator();
    }

    @Test
    void testTranslateOptionCall() {
        InstrumentDescriptor descriptor = new InstrumentDescriptor(InstrumentType.OPTION, Exchange.PARADEX,
                "BTC/USD-20260626-67000-C", "BTC-USD-26JUN26-67000-C", "BTC", "USD", new BigDecimal("0.001"),
                new BigDecimal("0.01"), 20, BigDecimal.ZERO, 0, BigDecimal.ONE, 1, "");

        Ticker ticker = translator.translateTicker(descriptor);

        assertNotNull(ticker);
        assertEquals("BTC-USD-26JUN26-67000-C", ticker.getSymbol());
        assertEquals(InstrumentType.OPTION, ticker.getInstrumentType());
        assertEquals(2026, ticker.getExpiryYear());
        assertEquals(6, ticker.getExpiryMonth());
        assertEquals(26, ticker.getExpiryDay());
        assertEquals(new BigDecimal("67000"), ticker.getStrike());
        assertEquals(Right.CALL, ticker.getRight());
    }

    @Test
    void testTranslateOptionPut() {
        InstrumentDescriptor descriptor = new InstrumentDescriptor(InstrumentType.OPTION, Exchange.PARADEX,
                "ETH/USD-20260101-3500-P", "ETH-USD-1JAN26-3500-P", "ETH", "USD", new BigDecimal("0.001"),
                new BigDecimal("0.01"), 20, BigDecimal.ZERO, 0, BigDecimal.ONE, 1, "");

        Ticker ticker = translator.translateTicker(descriptor);

        assertEquals(2026, ticker.getExpiryYear());
        assertEquals(1, ticker.getExpiryMonth());
        assertEquals(1, ticker.getExpiryDay());
        assertEquals(new BigDecimal("3500"), ticker.getStrike());
        assertEquals(Right.PUT, ticker.getRight());
    }

    @Test
    void testTranslateNonOptionLeavesOptionFieldsUnset() {
        InstrumentDescriptor descriptor = new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES, Exchange.PARADEX,
                "BTC", "BTC-USD-PERP", "BTC", "USD", new BigDecimal("0.00001"), new BigDecimal("0.1"), 100,
                BigDecimal.ZERO, 8, BigDecimal.ONE, 50, "");

        Ticker ticker = translator.translateTicker(descriptor);

        assertNotNull(ticker);
        assertEquals(InstrumentType.PERPETUAL_FUTURES, ticker.getInstrumentType());
        assertEquals(0, ticker.getExpiryYear());
        assertEquals(0, ticker.getExpiryMonth());
        assertEquals(0, ticker.getExpiryDay());
        assertNull(ticker.getStrike());
        assertEquals(Right.NONE, ticker.getRight());
    }

    @Test
    void testTranslateOptionMalformedSymbolFallsBackGracefully() {
        // Common symbol does not encode option metadata - translator should
        // not blow up; it just leaves option fields unset.
        InstrumentDescriptor descriptor = new InstrumentDescriptor(InstrumentType.OPTION, Exchange.PARADEX,
                "BTC", "BTC-USD-26JUN26-67000-C", "BTC", "USD", new BigDecimal("0.001"), new BigDecimal("0.01"), 20,
                BigDecimal.ZERO, 0, BigDecimal.ONE, 1, "");

        Ticker ticker = translator.translateTicker(descriptor);

        assertNotNull(ticker);
        assertEquals(InstrumentType.OPTION, ticker.getInstrumentType());
        assertEquals(0, ticker.getExpiryYear());
        assertNull(ticker.getStrike());
        assertEquals(Right.NONE, ticker.getRight());
    }
}
