package com.fueledbychai.marketdata.extended;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.fueledbychai.websocket.IWebSocketClosedListener;
import com.fueledbychai.websocket.IWebSocketProcessor;

/**
 * Processes Extended public-trades stream frames. Frames look like:
 *
 * <pre>
 * { "type":"TRADE", "data":[{"market":"..","side":"BUY|SELL","price":"..","qty":"..","timestamp":..}], .. }
 * </pre>
 *
 * Each trade is dispatched to the registered {@link TradesUpdateListener}s on a
 * small thread pool so the WebSocket thread is never blocked.
 */
public class ExtendedTradesProcessor implements IWebSocketProcessor {

    protected static final Logger logger = LoggerFactory.getLogger(ExtendedTradesProcessor.class);
    protected IWebSocketClosedListener closedListener;
    protected List<TradesUpdateListener> listeners = new ArrayList<>();

    private final ExecutorService tradeExecutor;

    private static class TradeThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "extended-trade-processor-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    public ExtendedTradesProcessor(IWebSocketClosedListener closedListener) {
        this.closedListener = closedListener;
        this.tradeExecutor = Executors.newFixedThreadPool(4, new TradeThreadFactory());
        logger.info("Initialized ExtendedTradesProcessor with 4 threads for trade processing");
    }

    public void addTradesUpdateListener(TradesUpdateListener listener) {
        listeners.add(listener);
    }

    public void removeTradesUpdateListener(TradesUpdateListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void connectionClosed(int code, String reason, boolean remote) {
        logger.info("Disconnected from Extended Trades WebSocket: {}", reason);
        if (closedListener != null) {
            closedListener.connectionClosed();
        }
    }

    @Override
    public void connectionError(Exception error) {
        logger.error(error.getMessage(), error);
        if (closedListener != null) {
            closedListener.connectionClosed();
        }
    }

    @Override
    public void connectionEstablished() {
        logger.info("Connected to Extended Trades WebSocket");
    }

    @Override
    public void connectionOpened() {
        logger.info("Opened connection to Extended Trades WebSocket");
    }

    @Override
    public void messageReceived(String message) {
        logger.debug("Trades message received: {}", message);
        try {
            JsonObject root = JsonParser.parseString(message).getAsJsonObject();
            if (!root.has("data") || root.get("data").isJsonNull()) {
                return;
            }

            JsonElement dataEl = root.get("data");
            if (dataEl.isJsonArray()) {
                for (JsonElement el : dataEl.getAsJsonArray()) {
                    if (el != null && el.isJsonObject()) {
                        dispatchTrade(el.getAsJsonObject());
                    }
                }
            } else if (dataEl.isJsonObject()) {
                dispatchTrade(dataEl.getAsJsonObject());
            }
        } catch (Exception e) {
            logger.error("Error processing trades message: {}", message, e);
        }
    }

    private void dispatchTrade(JsonObject trade) {
        String market = getString(trade, "market");
        String price = getString(trade, "price");
        String side = getString(trade, "side");
        String size = getString(trade, "qty");
        long timestamp = getLong(trade, "timestamp");

        if (market == null || price == null || side == null || size == null) {
            return;
        }

        for (TradesUpdateListener listener : listeners) {
            tradeExecutor.submit(() -> {
                try {
                    listener.newTrade(timestamp, market, price, side, size);
                } catch (Exception ex) {
                    logger.warn("Error processing trade notification for listener", ex);
                }
            });
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static long getLong(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return System.currentTimeMillis();
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public void shutdown() {
        if (tradeExecutor != null && !tradeExecutor.isShutdown()) {
            tradeExecutor.shutdown();
        }
    }

    public void shutdownNow() {
        if (tradeExecutor != null && !tradeExecutor.isShutdown()) {
            tradeExecutor.shutdownNow();
        }
    }

    public boolean isShutdown() {
        return tradeExecutor == null || tradeExecutor.isShutdown();
    }
}
