package com.fueledbychai.marketdata.extended;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.extended.common.api.ExtendedConfiguration;
import com.fueledbychai.extended.common.api.ws.ExtendedStreamUrls;
import com.fueledbychai.extended.common.api.ws.ExtendedWebSocketClient;

/**
 * Manages the Extended public-trades WebSocket connection for a single market.
 * Extended subscribes via the URL path, so the market is encoded in the stream
 * URL and no subscribe message is sent.
 */
public class TradesWebSocketClient {

    protected static final Logger logger = LoggerFactory.getLogger(TradesWebSocketClient.class);
    protected ExtendedTradesProcessor tradesProcessor;

    public void startTradesWSClient(Ticker ticker, TradesUpdateListener tradesListener) {
        try {
            logger.info("Starting Extended trades WebSocket client for {}", ticker.getSymbol());
            String baseWsUrl = ExtendedConfiguration.getInstance().getWebSocketUrl();
            String streamUrl = new ExtendedStreamUrls(baseWsUrl).publicTrades(ticker.getSymbol());
            ExtendedWebSocketClient tradesWSClient = new ExtendedWebSocketClient(streamUrl,
                    "publicTrades." + ticker.getSymbol(), getTradesProcessor(ticker, tradesListener), null);
            tradesWSClient.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected ExtendedTradesProcessor getTradesProcessor(Ticker ticker, TradesUpdateListener listener) {
        if (tradesProcessor == null) {
            tradesProcessor = new ExtendedTradesProcessor(() -> {
                logger.info("Extended trades WebSocket closed, trying to restart...");
                startTradesWSClient(ticker, listener);
            });
            tradesProcessor.addTradesUpdateListener(listener);
        }
        return tradesProcessor;
    }
}
