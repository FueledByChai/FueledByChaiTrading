package com.fueledbychai.aster.common.api.ws;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketProcessor;

public final class AsterWebSocketClientBuilder {

    private AsterWebSocketClientBuilder() {
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

    public static String userDataUrl(String webSocketUrl, String listenKey) {
        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            throw new IllegalArgumentException("webSocketUrl is required");
        }
        if (listenKey == null || listenKey.isBlank()) {
            throw new IllegalArgumentException("listenKey is required");
        }
        String normalizedUrl = webSocketUrl.trim();
        if (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        return normalizedUrl + "/" + listenKey.trim();
    }

    public static AsterWebSocketClient buildBookTickerClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new AsterWebSocketClient(url, bookTickerChannel(ticker), processor);
    }

    public static AsterWebSocketClient buildSymbolTickerClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new AsterWebSocketClient(url, symbolTickerChannel(ticker), processor);
    }

    public static AsterWebSocketClient buildPartialDepthClient(String url, Ticker ticker, int depth,
            IWebSocketProcessor processor) throws Exception {
        return new AsterWebSocketClient(url, partialDepthChannel(ticker, depth), processor);
    }

    public static AsterWebSocketClient buildTradesClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new AsterWebSocketClient(url, aggTradeChannel(ticker), processor);
    }

    public static AsterWebSocketClient buildMarkPriceClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new AsterWebSocketClient(url, markPriceChannel(ticker), processor);
    }

    public static AsterWebSocketClient buildUserDataClient(String url, String listenKey, IWebSocketProcessor processor)
            throws Exception {
        return new AsterWebSocketClient(userDataUrl(url, listenKey), null, processor);
    }

    private static String normalizeSymbol(Ticker ticker) {
        if (ticker == null || ticker.getSymbol() == null || ticker.getSymbol().isBlank()) {
            throw new IllegalArgumentException("ticker with symbol is required");
        }
        return ticker.getSymbol().trim().toLowerCase();
    }
}
