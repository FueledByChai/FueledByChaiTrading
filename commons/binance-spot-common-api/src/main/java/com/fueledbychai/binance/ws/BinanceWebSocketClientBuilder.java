package com.fueledbychai.binance.ws;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class BinanceWebSocketClientBuilder {

    public static String partialBookDepthChannel(Ticker ticker) {
        return String.format("%s@depth5@100ms", ticker.getSymbol().toLowerCase());
    }

    public static String tradesChannel(Ticker ticker) {
        return String.format("%s@aggTrade", ticker.getSymbol().toLowerCase());
    }

    public static String bookTickerChannel(Ticker ticker) {
        return String.format("%s@bookTicker", ticker.getSymbol().toLowerCase());
    }

    public static String symbolTickerChannel(Ticker ticker) {
        return String.format("%s@ticker", ticker.getSymbol().toLowerCase());
    }

    public static BinanceWebSocketClient buildPartialBookDepth(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new BinanceWebSocketClient(url, partialBookDepthChannel(ticker), processor);
    }

    public static BinanceWebSocketClient buildTradesClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new BinanceWebSocketClient(url, tradesChannel(ticker), processor);
    }

    public static BinanceWebSocketClient buildBookTickerClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new BinanceWebSocketClient(url, bookTickerChannel(ticker), processor);
    }

    public static BinanceWebSocketClient buildSymbolTickerClient(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        return new BinanceWebSocketClient(url, symbolTickerChannel(ticker), processor);
    }

}
