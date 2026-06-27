package com.fueledbychai.marketdata.paradex;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.common.api.ws.ParadexWebSocketClient;

public class MarketsSummaryWebSocketClient {

    protected static final Logger logger = LoggerFactory.getLogger(MarketsSummaryWebSocketClient.class);
    protected Map<Ticker, ParadexOrderBook> orderBooks = new HashMap<>();
    protected String wsUrl = "wss://ws.api.prod.paradex.trade/v1";
    protected MarketsSummaryWebSocketProcessor marketsSummaryProcessor;
    // Currently-live WS client. Reconnects are driven by the processor's
    // closed-listener calling back into start(); without tracking + closing the
    // prior client, each reconnect leaked a live connection (duplicate streams →
    // N× ticks → dispatch-queue/heap blowup, 2026-06-25).
    protected ParadexWebSocketClient marketsSummaryWSClient;

    public synchronized void startMarketsSummaryWSClient(Ticker ticker,
            MarketsSummaryUpdateListener marketsSummaryListener) {
        try {
            closeQuietly(marketsSummaryWSClient);
            logger.info("Starting markets summary WebSocket client");
            marketsSummaryWSClient = new ParadexWebSocketClient(wsUrl,
                    "markets_summary." + ticker.getSymbol(),
                    getMarketsSummaryProcessor(ticker, marketsSummaryListener));
            marketsSummaryWSClient.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

    static void closeQuietly(ParadexWebSocketClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignore) {
                // already closed / closing — nothing to do
            }
        }
    }

    protected MarketsSummaryWebSocketProcessor getMarketsSummaryProcessor(Ticker ticker,
            MarketsSummaryUpdateListener listener) {
        if (marketsSummaryProcessor == null) {
            marketsSummaryProcessor = new MarketsSummaryWebSocketProcessor(() -> {
                logger.info("Markets Summary WebSocket closed, trying to restart...");
                startMarketsSummaryWSClient(ticker, listener);

            });
            marketsSummaryProcessor.addMarketsSummaryUpdateListener(listener);
        }
        return marketsSummaryProcessor;
    }
}
