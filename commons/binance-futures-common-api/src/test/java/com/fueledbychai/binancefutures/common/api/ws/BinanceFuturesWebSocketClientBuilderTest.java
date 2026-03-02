package com.fueledbychai.binancefutures.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

class BinanceFuturesWebSocketClientBuilderTest {

    @Test
    void optionTickersUseOptionStreamNames() {
        Ticker ticker = new Ticker("BTC-260627-50000-C").setInstrumentType(InstrumentType.OPTION);

        assertEquals("btc-260627-50000-c@bookTicker", BinanceFuturesWebSocketClientBuilder.bookTickerChannel(ticker));
        assertEquals("btc-260627-50000-c@optionTicker", BinanceFuturesWebSocketClientBuilder.symbolTickerChannel(ticker));
        assertEquals("btc-260627-50000-c@depth10@100ms",
                BinanceFuturesWebSocketClientBuilder.partialDepthChannel(ticker, 10));
        assertEquals("btc-260627-50000-c@optionTrade", BinanceFuturesWebSocketClientBuilder.aggTradeChannel(ticker));
        assertEquals("btcusdt@markPrice", BinanceFuturesWebSocketClientBuilder.markPriceChannel(ticker));
    }

    @Test
    void perpetualFuturesTickersRemainLowercaseAndUseHighFrequencyDepth() {
        Ticker ticker = new Ticker("BTCUSDT").setInstrumentType(InstrumentType.PERPETUAL_FUTURES);

        assertEquals("btcusdt@ticker", BinanceFuturesWebSocketClientBuilder.symbolTickerChannel(ticker));
        assertEquals("btcusdt@depth20@100ms", BinanceFuturesWebSocketClientBuilder.partialDepthChannel(ticker, 20));
    }
}
