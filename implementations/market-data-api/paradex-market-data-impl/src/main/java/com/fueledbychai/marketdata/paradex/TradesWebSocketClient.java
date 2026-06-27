package com.fueledbychai.marketdata.paradex;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.common.api.ws.ParadexWebSocketClient;

public class TradesWebSocketClient {

    protected static final Logger logger = LoggerFactory.getLogger(TradesWebSocketClient.class);
    protected Map<Ticker, ParadexOrderBook> orderBooks = new HashMap<>();
    protected String wsUrl = "wss://ws.api.prod.paradex.trade/v1";
    protected TradesWebSocketProcessor tradesProcessor;
    // Currently-live WS client — closed before each reconnect so connections
    // don't accumulate across restarts (see 2026-06-25 OOM).
    protected ParadexWebSocketClient tradesWSClient;

    public synchronized void startTradesWSClient(Ticker ticker, TradesUpdateListener tradesListener) {
        try {
            MarketsSummaryWebSocketClient.closeQuietly(tradesWSClient);
            logger.info("Starting trades WebSocket client");
            tradesWSClient = new ParadexWebSocketClient(wsUrl, "trades." + ticker.getSymbol(),
                    getTradesProcessor(ticker, tradesListener));
            tradesWSClient.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

    protected TradesWebSocketProcessor getTradesProcessor(Ticker ticker, TradesUpdateListener listener) {
        if (tradesProcessor == null) {
            tradesProcessor = new TradesWebSocketProcessor(() -> {
                logger.info("Trades WebSocket closed, trying to restart...");
                startTradesWSClient(ticker, listener);

            });
            tradesProcessor.addTradesUpdateListener(listener);
        }
        return tradesProcessor;
    }
}
