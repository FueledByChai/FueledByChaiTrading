package com.fueledbychai.bybit.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.fueledbychai.bybit.common.api.IBybitRestApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

class BybitTickerRegistryTest {

    @Test
    void getOptionChainLoadsMissingUnderlyingOnDemand() {
        StubBybitRestApi restApi = new StubBybitRestApi();
        BybitTickerRegistry registry = new BybitTickerRegistry(restApi);

        assertEquals(1, registry.getOptionChain("BTC").length);
        assertEquals(0, registry.getOptionChain("SOL").length);

        assertEquals(1, registry.getOptionChain("ETH").length);
        assertEquals(1, restApi.optionRequestsByBaseCoin.getOrDefault("ETH", 0).intValue());

        assertEquals(1, registry.getOptionChain("ETH").length);
        assertEquals(1, restApi.optionRequestsByBaseCoin.getOrDefault("ETH", 0).intValue());
    }

    private static class StubBybitRestApi implements IBybitRestApi {

        private final Map<String, Integer> optionRequestsByBaseCoin = new ConcurrentHashMap<>();

        @Override
        public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
            if (instrumentType == InstrumentType.OPTION) {
                return new InstrumentDescriptor[] {
                        optionDescriptor("BTC", "BTC-07MAR26-60000-C", "BTC/USD-20260307-60000-C") };
            }
            return new InstrumentDescriptor[0];
        }

        @Override
        public InstrumentDescriptor[] getOptionInstrumentsForBaseCoin(String baseCoin) {
            String normalized = baseCoin == null ? "" : baseCoin.trim().toUpperCase(Locale.US);
            optionRequestsByBaseCoin.merge(normalized, 1, Integer::sum);
            if ("ETH".equals(normalized)) {
                return new InstrumentDescriptor[] {
                        optionDescriptor("ETH", "ETH-07MAR26-3500-C", "ETH/USD-20260307-3500-C") };
            }
            return new InstrumentDescriptor[0];
        }

        @Override
        public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
            return null;
        }

        @Override
        public boolean isPublicApiOnly() {
            return true;
        }

        private InstrumentDescriptor optionDescriptor(String baseCurrency, String exchangeSymbol, String commonSymbol) {
            return new InstrumentDescriptor(InstrumentType.OPTION, Exchange.BYBIT, commonSymbol, exchangeSymbol,
                    baseCurrency, "USD", new BigDecimal("0.01"), new BigDecimal("0.1"), 1, new BigDecimal("0.01"), 0,
                    BigDecimal.ONE, 1, exchangeSymbol);
        }
    }
}
