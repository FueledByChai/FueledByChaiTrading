package com.fueledbychai.deribit.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

class DeribitTickerTranslatorTest {

    @Test
    void usesCommonSymbolExpiryWhenExchangeSymbolDateCannotBeParsed() {
        InstrumentDescriptor descriptor = new InstrumentDescriptor(InstrumentType.OPTION, Exchange.DERIBIT,
                "BTC/USD-20260304-90000-C", "BTC-BADDATE-90000-C", "BTC", "USD", new BigDecimal("0.1"),
                new BigDecimal("0.0005"), 0, new BigDecimal("0.1"), 0, BigDecimal.ONE, 1, "3");

        Ticker ticker = new DeribitTickerTranslator().translateTicker(descriptor);

        assertEquals(2026, ticker.getExpiryYear());
        assertEquals(3, ticker.getExpiryMonth());
        assertEquals(4, ticker.getExpiryDay());
        assertEquals(0, new BigDecimal("90000").compareTo(ticker.getStrike()));
        assertEquals(Ticker.Right.CALL, ticker.getRight());
    }
}
