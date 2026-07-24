package com.fueledbychai.grvt.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.grvt.common.api.IGrvtRestApi;
import com.fueledbychai.grvt.common.api.IGrvtWebSocketApi;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;

/**
 * Verifies that the GRVT common-api SPI providers are discovered via {@code META-INF/services} and
 * resolve for {@link Exchange#GRVT}. These checks construct the public REST and WebSocket APIs but
 * make no network calls (instruments are loaded lazily).
 */
class GrvtSpiDiscoveryTest {

    @Test
    void restApiProviderIsDiscovered() {
        assertTrue(ExchangeRestApiFactory.isRegistered(Exchange.GRVT));
        IGrvtRestApi restApi = ExchangeRestApiFactory.getPublicApi(Exchange.GRVT, IGrvtRestApi.class);
        assertNotNull(restApi);
        assertTrue(restApi.isPublicApiOnly());
    }

    @Test
    void webSocketApiProviderIsDiscovered() {
        assertTrue(ExchangeWebSocketApiFactory.isRegistered(Exchange.GRVT));
        IGrvtWebSocketApi webSocketApi = ExchangeWebSocketApiFactory.getApi(Exchange.GRVT, IGrvtWebSocketApi.class);
        assertNotNull(webSocketApi);
    }

    @Test
    void capabilitiesProviderIsDiscovered() {
        ExchangeCapabilities capabilities = new GrvtExchangeCapabilitiesProvider().getCapabilities();
        assertEquals(Exchange.GRVT, capabilities.getExchange());
        assertTrue(capabilities.supportsStreaming());
        assertTrue(capabilities.supportsBrokerage());
        assertTrue(capabilities.supportsHistoricalData());
        assertTrue(capabilities.getInstrumentTypes().contains(InstrumentType.PERPETUAL_FUTURES));
        assertTrue(capabilities.getInstrumentTypes().contains(InstrumentType.OPTION));
    }
}
