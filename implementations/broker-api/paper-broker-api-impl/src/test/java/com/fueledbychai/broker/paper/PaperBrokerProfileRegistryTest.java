package com.fueledbychai.broker.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;

public class PaperBrokerProfileRegistryTest {

    private static final Set<Exchange> EXPECTED_CRYPTO_EXCHANGES = Set.of(Exchange.DYDX, Exchange.HYPERLIQUID,
            Exchange.PARADEX, Exchange.DRIFT, Exchange.LIGHTER, Exchange.BINANCE_SPOT, Exchange.BINANCE_FUTURES,
            Exchange.DERIBIT, Exchange.OKX, Exchange.BYBIT);

    private static final Set<InstrumentType> EXPECTED_COMMISSION_TYPES = Set.of(InstrumentType.PERPETUAL_FUTURES,
            InstrumentType.CRYPTO_SPOT, InstrumentType.OPTION);

    @Test
    public void testDefaultProfilesExistForAllCryptoExchanges() {
        assertEquals(EXPECTED_CRYPTO_EXCHANGES, PaperBrokerProfileRegistry.getConfiguredExchanges());
        assertEquals(EXPECTED_COMMISSION_TYPES, PaperBrokerProfileRegistry.getCommissionInstrumentTypes());

        for (Exchange exchange : EXPECTED_CRYPTO_EXCHANGES) {
            if (exchange == Exchange.PARADEX) {
                assertLatency(exchange, 350, 550, 200, 300);
                assertCommission(exchange, InstrumentType.PERPETUAL_FUTURES, -0.2, -2.0);
                assertCommission(exchange, InstrumentType.CRYPTO_SPOT, 0.0, 0.0);
                assertCommission(exchange, InstrumentType.OPTION, 0.0, 0.0);
                continue;
            }

            if (exchange == Exchange.HYPERLIQUID) {
                assertLatency(exchange, 900, 2000, 200, 300);
                assertCommission(exchange, InstrumentType.PERPETUAL_FUTURES, -1.5, -4.5);
                assertCommission(exchange, InstrumentType.CRYPTO_SPOT, 0.0, 0.0);
                assertCommission(exchange, InstrumentType.OPTION, 0.0, 0.0);
                continue;
            }

            if (exchange == Exchange.DRIFT) {
                assertLatency(exchange, 650, 1200, 200, 350);
                assertCommission(exchange, InstrumentType.PERPETUAL_FUTURES, 0.25, -3.5);
                assertCommission(exchange, InstrumentType.CRYPTO_SPOT, 0.0, 0.0);
                assertCommission(exchange, InstrumentType.OPTION, 0.0, 0.0);
                continue;
            }

            assertLatency(exchange, 0, 0, 0, 0);
            for (InstrumentType instrumentType : EXPECTED_COMMISSION_TYPES) {
                assertCommission(exchange, instrumentType, 0.0, 0.0);
            }
        }
    }

    @Test
    public void testLatencyLookupsReturnCopies() {
        PaperBrokerLatency firstLookup = PaperBrokerProfileRegistry.getLatencyProfile(Exchange.PARADEX);
        firstLookup.setRestLatencyMsMin(99);

        PaperBrokerLatency secondLookup = PaperBrokerProfileRegistry.getLatencyProfile(Exchange.PARADEX);

        assertEquals(350, secondLookup.getRestLatencyMsMin());
    }

    private static void assertLatency(Exchange exchange, int restMin, int restMax, int wsMin, int wsMax) {
        PaperBrokerLatency latency = PaperBrokerProfileRegistry.getLatencyProfile(exchange);
        assertEquals(restMin, latency.getRestLatencyMsMin());
        assertEquals(restMax, latency.getRestLatencyMsMax());
        assertEquals(wsMin, latency.getWsLatencyMsMin());
        assertEquals(wsMax, latency.getWsLatencyMsMax());
    }

    private static void assertCommission(Exchange exchange, InstrumentType instrumentType, double makerFeeBps,
            double takerFeeBps) {
        PaperBrokerCommission commission = PaperBrokerProfileRegistry.getCommissionProfile(exchange, instrumentType);
        assertEquals(makerFeeBps, commission.getMakerFeeBps(), 0.0);
        assertEquals(takerFeeBps, commission.getTakerFeeBps(), 0.0);
    }
}
