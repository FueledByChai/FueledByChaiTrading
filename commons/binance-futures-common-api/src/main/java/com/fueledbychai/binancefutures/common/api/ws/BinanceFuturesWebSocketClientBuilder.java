package com.fueledbychai.binancefutures.common.api.ws;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketProcessor;

public final class BinanceFuturesWebSocketClientBuilder {

    private BinanceFuturesWebSocketClientBuilder() {
    }

    public static String bookTickerChannel(Ticker ticker) {
        return normalizeSymbol(ticker) + "@bookTicker";
    }

    public static String symbolTickerChannel(Ticker ticker) {
        return normalizeSymbol(ticker) + "@ticker";
    }

    public static String partialDepthChannel(Ticker ticker, int depth) {
        if (depth != 5 && depth != 10 && depth != 20) {
            throw new IllegalArgumentException("depth must be one of 5, 10, or 20");
        }
        return normalizeSymbol(ticker) + "@depth" + depth + "@100ms";
    }

    public static String aggTradeChannel(Ticker ticker) {
        return normalizeSymbol(ticker) + "@aggTrade";
    }

    public static String markPriceChannel(Ticker ticker) {
        return normalizeSymbol(ticker) + "@markPrice@1s";
    }

    public static BinanceFuturesWebSocketClient buildBookTickerClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new BinanceFuturesWebSocketClient(url, bookTickerChannel(ticker), processor);
    }

    public static BinanceFuturesWebSocketClient buildSymbolTickerClient(String url, Ticker ticker,
            IWebSocketProcessor processor) throws Exception {
        return new BinanceFuturesWebSocketClient(url, symbolTickerChannel(ticker), processor);
    }

    public static BinanceFuturesWebSocketClient buildPartialDepthClient(String url, Ticker ticker, int depth,
            IWebSocketProcessor processor) throws Exception {
        return new BinanceFuturesWebSocketClient(url, partialDepthChannel(ticker, depth), processor);
    }

    public static BinanceFuturesWebSocketClient buildTradesClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new BinanceFuturesWebSocketClient(url, aggTradeChannel(ticker), processor);
    }

    public static BinanceFuturesWebSocketClient buildMarkPriceClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new BinanceFuturesWebSocketClient(url, markPriceChannel(ticker), processor);
    }

    private static String normalizeSymbol(Ticker ticker) {
        if (ticker == null || ticker.getSymbol() == null || ticker.getSymbol().isBlank()) {
            throw new IllegalArgumentException("ticker with symbol is required");
        }
        return ticker.getSymbol().toLowerCase();
    }
}
