package com.fueledbychai.lighter.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.ILighterRestApi;

@ExtendWith(MockitoExtension.class)
class LighterTickerRegistryTest {

    @Mock
    private ILighterRestApi restApi;

    @Test
    void registerDescriptorsCachesInstrumentIdOnTickerAndLookupById() {
        InstrumentDescriptor perpDescriptor = new InstrumentDescriptor(
                InstrumentType.PERPETUAL_FUTURES,
                Exchange.LIGHTER,
                "BTC/USDC",
                "BTC",
                "BTC",
                "USDC",
                new BigDecimal("0.001"),
                new BigDecimal("0.1"),
                10,
                new BigDecimal("0.01"),
                8,
                BigDecimal.ONE,
                50,
                "123");

        when(restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES))
                .thenReturn(new InstrumentDescriptor[] { perpDescriptor });
        when(restApi.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT))
                .thenReturn(new InstrumentDescriptor[0]);

        LighterTickerRegistry registry = new LighterTickerRegistry(restApi);
        Ticker bySymbol = registry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC");
        Ticker byId = registry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "123");

        assertNotNull(bySymbol);
        assertEquals("123", bySymbol.getId());
        assertNotNull(byId);
        assertSame(bySymbol, byId);
    }
}
