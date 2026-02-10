package com.fueledbychai.hyperliquid.ws;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.ExchangeRestApiFactory;

class HyperliquidInstrumentLookupTest {

    @Test
    void constructorRejectsNullApi() {
        assertThrows(IllegalArgumentException.class, () -> new HyperliquidInstrumentLookup(null));
    }

    @Test
    void lookupByExchangeSymbolDelegatesToApi() {
        IHyperliquidRestApi api = mock(IHyperliquidRestApi.class);
        InstrumentDescriptor descriptor = mock(InstrumentDescriptor.class);
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
        InstrumentDescriptor[] descriptors = { mock(InstrumentDescriptor.class) };
        when(api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)).thenReturn(descriptors);

        HyperliquidInstrumentLookup lookup = new HyperliquidInstrumentLookup(api);
        InstrumentDescriptor[] result = lookup.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);

        assertSame(descriptors, result);
        verify(api).getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
    }

    @Test
    void defaultConstructorUsesExchangeRestApiFactory() {
        IHyperliquidRestApi api = mock(IHyperliquidRestApi.class);
        InstrumentDescriptor descriptor = mock(InstrumentDescriptor.class);

        try (MockedStatic<ExchangeRestApiFactory> mockedFactory = Mockito.mockStatic(ExchangeRestApiFactory.class)) {
            mockedFactory.when(() -> ExchangeRestApiFactory.getPublicApi(Exchange.HYPERLIQUID,
                    IHyperliquidRestApi.class)).thenReturn(api);
            when(api.getInstrumentDescriptor("ETH")).thenReturn(descriptor);

            HyperliquidInstrumentLookup lookup = new HyperliquidInstrumentLookup();
            InstrumentDescriptor result = lookup.lookupByExchangeSymbol("ETH");

            assertSame(descriptor, result);
            mockedFactory.verify(
                    () -> ExchangeRestApiFactory.getPublicApi(Exchange.HYPERLIQUID, IHyperliquidRestApi.class));
            verify(api).getInstrumentDescriptor("ETH");
        }
    }
}
