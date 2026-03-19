package com.fueledbychai.aster.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

class AsterWebSocketClientBuilderTest {

    @Test
    void buildsExpectedMarketDataChannels() {
        Ticker ticker = new Ticker("BTCUSDT")
                .setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES);

        assertEquals("btcusdt@bookTicker", AsterWebSocketClientBuilder.bookTickerChannel(ticker));
        assertEquals("btcusdt@ticker", AsterWebSocketClientBuilder.symbolTickerChannel(ticker));
        assertEquals("btcusdt@depth20@100ms", AsterWebSocketClientBuilder.partialDepthChannel(ticker, 20));
        assertEquals("btcusdt@aggTrade", AsterWebSocketClientBuilder.aggTradeChannel(ticker));
        assertEquals("btcusdt@markPrice@1s", AsterWebSocketClientBuilder.markPriceChannel(ticker));
    }

    @Test
    void buildsExpectedUserDataUrl() {
        assertEquals("wss://fstream.asterdex.com/ws/listen-key",
                AsterWebSocketClientBuilder.userDataUrl("wss://fstream.asterdex.com/ws", "listen-key"));
    }

    @Test
    void rejectsUnsupportedDepthLevels() {
        Ticker ticker = new Ticker("BTCUSDT")
                .setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES);

        assertThrows(IllegalArgumentException.class, () -> AsterWebSocketClientBuilder.partialDepthChannel(ticker, 15));
    }
}
