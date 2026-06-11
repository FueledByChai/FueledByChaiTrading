package com.fueledbychai.binancefutures.common.api.ws;

import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketProcessor;

public final class BinanceFuturesWebSocketClientBuilder {

    private BinanceFuturesWebSocketClientBuilder() {
    }

    public static String bookTickerChannel(Ticker ticker) {
        if (isOptionTicker(ticker)) {
            return normalizeSymbol(ticker) + "@bookTicker";
        }
        return normalizeSymbol(ticker) + "@bookTicker";
    }

    public static String symbolTickerChannel(Ticker ticker) {
        if (isOptionTicker(ticker)) {
            return normalizeSymbol(ticker) + "@optionTicker";
        }
        return normalizeSymbol(ticker) + "@ticker";
    }

    public static String partialDepthChannel(Ticker ticker, int depth) {
        if (isOptionTicker(ticker)) {
            if (depth != 5 && depth != 10 && depth != 20) {
                throw new IllegalArgumentException("option depth must be one of 5, 10, or 20");
            }
            return normalizeSymbol(ticker) + "@depth" + depth + "@100ms";
        }
        if (depth != 5 && depth != 10 && depth != 20) {
            throw new IllegalArgumentException("depth must be one of 5, 10, or 20");
        }
        return normalizeSymbol(ticker) + "@depth" + depth + "@100ms";
    }

    /**
     * Full-depth incremental diff stream ({@code <symbol>@depth@100ms}). Unlike
     * {@link #partialDepthChannel} (a throttled top-N snapshot), this delivers per-event
     * inserts/updates/deletes with {@code U}/{@code u}/{@code pu} update ids — the order-flow
     * primitive the recorder needs. Requires a REST depth snapshot to seed an absolute book.
     */
    public static String diffDepthChannel(Ticker ticker) {
        return normalizeSymbol(ticker) + "@depth@100ms";
    }

    public static String aggTradeChannel(Ticker ticker) {
        if (isOptionTicker(ticker)) {
            return normalizeSymbol(ticker) + "@optionTrade";
        }
        return normalizeSymbol(ticker) + "@aggTrade";
    }

    public static String markPriceChannel(Ticker ticker) {
        if (isOptionTicker(ticker)) {
            return normalizeOptionUnderlying(ticker) + "@markPrice";
        }
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
        return ticker.getSymbol().trim().toLowerCase();
    }

    private static String normalizeOptionUnderlying(Ticker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker is required");
        }
        String symbol = ticker.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("ticker with symbol is required");
        }
        int dashIndex = symbol.indexOf('-');
        if (dashIndex > 0) {
            return symbol.substring(0, dashIndex).toLowerCase() + "usdt";
        }
        return symbol.toLowerCase();
    }

    private static boolean isOptionTicker(Ticker ticker) {
        if (ticker == null) {
            return false;
        }
        InstrumentType instrumentType = ticker.getInstrumentType();
        return instrumentType == InstrumentType.OPTION || instrumentType == InstrumentType.PERPETUAL_OPTION;
    }
}
