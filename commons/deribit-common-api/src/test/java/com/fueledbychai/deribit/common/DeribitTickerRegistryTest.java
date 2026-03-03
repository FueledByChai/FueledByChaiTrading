package com.fueledbychai.deribit.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.deribit.common.api.IDeribitRestApi;

class DeribitTickerRegistryTest {

    @Test
    void translatesOptionMetadataIntoTickerFields() {
        DeribitTickerRegistry registry = new DeribitTickerRegistry(new StubDeribitRestApi());

        Ticker ticker = registry.lookupByCommonSymbol(InstrumentType.OPTION, "BTC/USD-20260327-90000-C");
        assertNotNull(ticker);
        assertEquals(InstrumentType.OPTION, ticker.getInstrumentType());
        assertEquals(2026, ticker.getExpiryYear());
        assertEquals(3, ticker.getExpiryMonth());
        assertEquals(27, ticker.getExpiryDay());
        assertEquals(0, new BigDecimal("90000").compareTo(ticker.getStrike()));
        assertEquals(Ticker.Right.CALL, ticker.getRight());
    }

    @Test
    void convertsCommonSymbolsToExchangeSymbols() {
        DeribitTickerRegistry registry = new DeribitTickerRegistry(new StubDeribitRestApi());

        assertEquals("BTC_USDC", registry.commonSymbolToExchangeSymbol(InstrumentType.CRYPTO_SPOT, "BTC/USDC"));
        assertEquals("BTC-PERPETUAL",
                registry.commonSymbolToExchangeSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC/USD"));
        assertEquals("BTC-27MAR26-90000-C",
                registry.commonSymbolToExchangeSymbol(InstrumentType.OPTION, "BTC/USD-20260327-90000-C"));
    }

    private static class StubDeribitRestApi implements IDeribitRestApi {

        @Override
        public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
            if (instrumentType == InstrumentType.CRYPTO_SPOT) {
                return new InstrumentDescriptor[] {
                        new InstrumentDescriptor(InstrumentType.CRYPTO_SPOT, Exchange.DERIBIT, "BTC/USDC", "BTC_USDC",
                                "BTC", "USDC", new BigDecimal("0.0001"), new BigDecimal("0.01"), 0,
                                new BigDecimal("0.0001"), 0, BigDecimal.ONE, 1, "1")
                };
            }
            if (instrumentType == InstrumentType.PERPETUAL_FUTURES) {
                return new InstrumentDescriptor[] {
                        new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES, Exchange.DERIBIT, "BTC/USD",
                                "BTC-PERPETUAL", "BTC", "USD", new BigDecimal("10"), new BigDecimal("0.5"), 0,
                                new BigDecimal("10"), 8, new BigDecimal("10"), 1, "2")
                };
            }
            return new InstrumentDescriptor[] {
                    new InstrumentDescriptor(InstrumentType.OPTION, Exchange.DERIBIT, "BTC/USD-20260327-90000-C",
                            "BTC-27MAR26-90000-C", "BTC", "USD", new BigDecimal("0.1"),
                            new BigDecimal("0.0005"), 0, new BigDecimal("0.1"), 0, BigDecimal.ONE, 1, "3")
            };
        }

        @Override
        public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
            return null;
        }

        @Override
        public boolean isPublicApiOnly() {
            return true;
        }
    }
}
