package com.fueledbychai.aster.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fueledbychai.aster.common.api.ws.AsterJsonProcessor;
import com.fueledbychai.aster.common.api.ws.AsterWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

class AsterWebSocketApiTest {

    @Test
    void spotMarketDataSubscriptionsUseSpotWebSocketBaseUrl() {
        RecordingAsterWebSocketApi api = new RecordingAsterWebSocketApi("wss://fstream.unit.test/ws",
                "wss://sstream.unit.test/ws");

        api.subscribeBookTicker(spotTicker(), message -> {
        });

        assertEquals("wss://sstream.unit.test/ws", api.lastStreamUrl);
        assertEquals("bnbusdt@bookTicker", api.lastChannel);
    }

    @Test
    void perpetualMarketDataSubscriptionsUseFuturesWebSocketBaseUrl() {
        RecordingAsterWebSocketApi api = new RecordingAsterWebSocketApi("wss://fstream.unit.test/ws",
                "wss://sstream.unit.test/ws");

        api.subscribeMarkPrice(perpetualTicker(), message -> {
        });

        assertEquals("wss://fstream.unit.test/ws", api.lastStreamUrl);
        assertEquals("btcusdt@markPrice@1s", api.lastChannel);
    }

    @Test
    void spotTickersCannotSubscribeToMarkPrice() {
        RecordingAsterWebSocketApi api = new RecordingAsterWebSocketApi("wss://fstream.unit.test/ws",
                "wss://sstream.unit.test/ws");

        assertThrows(IllegalArgumentException.class, () -> api.subscribeMarkPrice(spotTicker(), message -> {
        }));
    }

    @Test
    void userDataStreamUsesFuturesWebSocketBaseUrl() {
        RecordingAsterWebSocketApi api = new RecordingAsterWebSocketApi("wss://fstream.unit.test/ws",
                "wss://sstream.unit.test/ws");

        api.subscribeUserData("listen-key", message -> {
        });

        assertEquals("wss://fstream.unit.test/ws/listen-key", api.lastStreamUrl);
        assertNull(api.lastChannel);
    }

    private static Ticker perpetualTicker() {
        return new Ticker("BTCUSDT")
                .setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
    }

    private static Ticker spotTicker() {
        return new Ticker("BNBUSDT")
                .setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.CRYPTO_SPOT);
    }

    private static final class RecordingAsterWebSocketApi extends AsterWebSocketApi {
        private String lastStreamUrl;
        private String lastChannel;

        private RecordingAsterWebSocketApi(String futuresWebSocketUrl, String spotWebSocketUrl) {
            super(futuresWebSocketUrl, spotWebSocketUrl, true);
        }

        @Override
        protected AsterWebSocketClient newClient(String streamUrl, String channel, IWebSocketProcessor processor) {
            this.lastStreamUrl = streamUrl;
            this.lastChannel = channel;
            try {
                return new NoOpAsterWebSocketClient(streamUrl, channel, processor);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create test websocket client", e);
            }
        }
    }

    private static final class NoOpAsterWebSocketClient extends AsterWebSocketClient {
        private NoOpAsterWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor)
                throws Exception {
            super(serverUri, channel, processor);
        }

        @Override
        public void connect() {
            // No-op for tests.
        }
    }
}
