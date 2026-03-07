package com.fueledbychai.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class TickerTranslatorTest {

    @Test
    void translateTickerCopiesContractMultiplierFromDescriptor() {
        InstrumentDescriptor descriptor = new InstrumentDescriptor(InstrumentType.OPTION, Exchange.OKX,
                "BTC/USD-20260327-120000-C", "BTC-USD-260327-120000-C", "BTC", "USD", BigDecimal.ONE,
                new BigDecimal("0.1"), 1, BigDecimal.ONE, 8, new BigDecimal("0.01"), 1, "btc-option");

        Ticker ticker = new TickerTranslator().translateTicker(descriptor);

        assertEquals(0, new BigDecimal("0.01").compareTo(ticker.getContractMultiplier()));
    }
}
