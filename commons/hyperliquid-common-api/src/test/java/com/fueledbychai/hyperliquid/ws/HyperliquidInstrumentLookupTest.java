package com.fueledbychai.hyperliquid.ws;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

class HyperliquidInstrumentLookupTest {

    private static InstrumentDescriptor createDescriptor(String commonSymbol, String exchangeSymbol) {
        return new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES, Exchange.HYPERLIQUID, commonSymbol,
                exchangeSymbol, commonSymbol, "USD", BigDecimal.ONE, new BigDecimal("0.1"),
                1, BigDecimal.ONE, 8, BigDecimal.ONE, 1, "");
    }

    @Test
    void constructorRejectsNullApi() {
        assertThrows(IllegalArgumentException.class, () -> new HyperliquidInstrumentLookup(null));
    }

    @Test
    void lookupByExchangeSymbolDelegatesToApi() {
        IHyperliquidRestApi api = mock(IHyperliquidRestApi.class);
        InstrumentDescriptor descriptor = createDescriptor("BTC", "BTC");
        when(api.getInstrumentDescriptor("BTC")).thenReturn(descriptor);

        HyperliquidInstrumentLookup lookup = new HyperliquidInstrumentLookup(api);
        InstrumentDescriptor result = lookup.lookupByExchangeSymbol("BTC");

        assertSame(descriptor, result);
        verify(api).getInstrumentDescriptor("BTC");
    }

    @Test
    void getAllInstrumentsForTypeRejectsUnsupportedType() {
        HyperliquidInstrumentLookup lookup = new HyperliquidInstrumentLookup(mock(IHyperliquidRestApi.class));

        assertThrows(IllegalArgumentException.class, () -> lookup.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT));
    }

    @Test
    void getAllInstrumentsForTypeDelegatesForPerp() {
        IHyperliquidRestApi api = mock(IHyperliquidRestApi.class);
        InstrumentDescriptor[] descriptors = { createDescriptor("BTC", "BTC") };
        when(api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)).thenReturn(descriptors);

        HyperliquidInstrumentLookup lookup = new HyperliquidInstrumentLookup(api);
        InstrumentDescriptor[] result = lookup.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);

        assertSame(descriptors, result);
        verify(api).getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
    }

    @Test
    void defaultConstructorUsesResolvedApi() {
        IHyperliquidRestApi mockApi = mock(IHyperliquidRestApi.class);
        InstrumentDescriptor descriptor = createDescriptor("ETH", "ETH");
        when(mockApi.getInstrumentDescriptor("ETH")).thenReturn(descriptor);

        HyperliquidInstrumentLookup lookup = new HyperliquidInstrumentLookup() {
            @Override
            protected IHyperliquidRestApi resolveApi() {
                return mockApi;
            }
        };

        InstrumentDescriptor result = lookup.lookupByExchangeSymbol("ETH");

        assertSame(descriptor, result);
        verify(mockApi).getInstrumentDescriptor("ETH");
    }
}
