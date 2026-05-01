package com.fueledbychai.binancefutures.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fueledbychai.binancefutures.common.api.ws.BinanceFuturesWebSocketClientBuilder;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;

/**
 * Routes Binance Futures WS subscriptions to the correct base URL after the
 * 2026-04-23 split. Public-category channels (bookTicker, depth) must land on
 * /public/ws; everything else on /market/ws. Connecting to the legacy /ws URL
 * silently drops anything outside the public set — SUBSCRIBE is acked but no
 * frames flow.
 */
class BinanceFuturesWebSocketApiUrlRoutingTest {

    @Test
    void publicChannelHelperRecognizesBookTickerAndDepth() {
        assertTrue(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@bookTicker"));
        assertTrue(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@depth20@100ms"));
        assertTrue(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@depth"));
        assertTrue(BinanceFuturesWebSocketApi.isPublicChannel("!bookTicker"));
    }

    @Test
    void publicChannelHelperRejectsMarketStreams() {
        assertFalse(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@ticker"));
        assertFalse(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@markPrice"));
        assertFalse(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@markPrice@1s"));
        assertFalse(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@aggTrade"));
        assertFalse(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@kline_1m"));
        assertFalse(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@miniTicker"));
        assertFalse(BinanceFuturesWebSocketApi.isPublicChannel("solusdt@forceOrder"));
        assertFalse(BinanceFuturesWebSocketApi.isPublicChannel(null));
    }

    @Test
    void resolvesPublicAndMarketUrlsForFuturesTicker() {
        BinanceFuturesWebSocketApi api = new BinanceFuturesWebSocketApi(
                "wss://fstream.binance.com/ws", "wss://fstream.binance.com/public/ws");
        Ticker perp = perp("SOLUSDT");

        // Public channels -> /public/ws
        assertEquals("wss://fstream.binance.com/public/ws",
                api.resolveWebSocketUrl(perp, BinanceFuturesWebSocketClientBuilder.bookTickerChannel(perp)));
        assertEquals("wss://fstream.binance.com/public/ws",
                api.resolveWebSocketUrl(perp, BinanceFuturesWebSocketClientBuilder.partialDepthChannel(perp, 20)));

        // Market channels -> /market/ws
        assertEquals("wss://fstream.binance.com/market/ws",
                api.resolveWebSocketUrl(perp, BinanceFuturesWebSocketClientBuilder.symbolTickerChannel(perp)));
        assertEquals("wss://fstream.binance.com/market/ws",
                api.resolveWebSocketUrl(perp, BinanceFuturesWebSocketClientBuilder.markPriceChannel(perp)));
        assertEquals("wss://fstream.binance.com/market/ws",
                api.resolveWebSocketUrl(perp, BinanceFuturesWebSocketClientBuilder.aggTradeChannel(perp)));
    }

    @Test
    void preservesOptionsRoutingAcrossChannelTypes() {
        // Options stayed on /public/ws. The category split applies to futures only;
        // any options ticker keeps using the dedicated optionsWebSocketUrl regardless
        // of channel name.
        BinanceFuturesWebSocketApi api = new BinanceFuturesWebSocketApi(
                "wss://fstream.binance.com/ws", "wss://fstream.binance.com/public/ws");
        Ticker option = option("BTC-260626-50000-C");

        assertEquals("wss://fstream.binance.com/public/ws",
                api.resolveWebSocketUrl(option, BinanceFuturesWebSocketClientBuilder.bookTickerChannel(option)));
        assertEquals("wss://fstream.binance.com/public/ws",
                api.resolveWebSocketUrl(option, BinanceFuturesWebSocketClientBuilder.symbolTickerChannel(option)));
        assertEquals("wss://fstream.binance.com/public/ws",
                api.resolveWebSocketUrl(option, BinanceFuturesWebSocketClientBuilder.markPriceChannel(option)));
    }

    @Test
    void toleratesCallerWhoConfiguredBareBaseOrAlternatePathSuffix() {
        // Users who set BINANCE_FUTURES_MAINNET_WS_URL to a non-default value
        // shouldn't break. We strip /ws, /public/ws, /market/ws, or /stream
        // and re-append the right category suffix.
        Ticker perp = perp("SOLUSDT");
        String tickerCh = BinanceFuturesWebSocketClientBuilder.symbolTickerChannel(perp);
        String bookCh = BinanceFuturesWebSocketClientBuilder.bookTickerChannel(perp);

        BinanceFuturesWebSocketApi bareBase = new BinanceFuturesWebSocketApi(
                "wss://fstream.binance.com", "wss://fstream.binance.com/public/ws");
        assertEquals("wss://fstream.binance.com/market/ws", bareBase.resolveWebSocketUrl(perp, tickerCh));
        assertEquals("wss://fstream.binance.com/public/ws", bareBase.resolveWebSocketUrl(perp, bookCh));

        BinanceFuturesWebSocketApi alreadyPublic = new BinanceFuturesWebSocketApi(
                "wss://fstream.binance.com/public/ws", "wss://fstream.binance.com/public/ws");
        assertEquals("wss://fstream.binance.com/market/ws", alreadyPublic.resolveWebSocketUrl(perp, tickerCh));
        assertEquals("wss://fstream.binance.com/public/ws", alreadyPublic.resolveWebSocketUrl(perp, bookCh));
    }

    private static Ticker perp(String symbol) {
        Ticker t = new Ticker();
        t.setSymbol(symbol);
        t.setExchange(Exchange.BINANCE_FUTURES);
        t.setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        return t;
    }

    private static Ticker option(String symbol) {
        Ticker t = new Ticker();
        t.setSymbol(symbol);
        t.setExchange(Exchange.BINANCE_FUTURES);
        t.setInstrumentType(InstrumentType.OPTION);
        return t;
    }
}
