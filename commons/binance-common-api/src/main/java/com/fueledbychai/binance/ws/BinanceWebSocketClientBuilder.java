package com.fueledbychai.binance.ws;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class BinanceWebSocketClientBuilder {

    public static BinanceWebSocketClient buildPartialBookDepth(String url, Ticker ticker, IWebSocketProcessor processor)
            throws Exception {
        String channel = String.format("%s@depth5@100ms", ticker.getSymbol().toLowerCase());

        return new BinanceWebSocketClient(url, channel, processor);
    }

}
