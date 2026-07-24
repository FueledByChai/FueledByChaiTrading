package com.fueledbychai.marketdata.extended;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.fueledbychai.websocket.IWebSocketClosedListener;
import com.fueledbychai.websocket.IWebSocketProcessor;

/**
 * Processes Extended order-book stream frames and applies them to an
 * {@link IExtendedOrderBook}. Extended subscribes via the URL path, so there is
 * no subscribe handshake; frames look like:
 *
 * <pre>
 * { "type": "SNAPSHOT"|"DELTA", "data": { "market": "..", "bid": [{"price":"..","qty":".."}], "ask":[..] }, "ts":.., "seq":.. }
 * </pre>
 *
 * Parsing is defensive against missing/null fields.
 */
public class ExtendedOrderBookProcessor implements IWebSocketProcessor {

    protected static final Logger logger = LoggerFactory.getLogger(ExtendedOrderBookProcessor.class);
    protected final IExtendedOrderBook orderBook;
    protected IWebSocketClosedListener listener;

    public ExtendedOrderBookProcessor(IExtendedOrderBook orderBook, IWebSocketClosedListener listener) {
        this.orderBook = orderBook;
        this.listener = listener;
    }

    @Override
    public void connectionClosed(int code, String reason, boolean remote) {
        logger.info("Disconnected from Extended WebSocket: {}", reason);
        if (listener != null) {
            listener.connectionClosed();
        }
    }

    @Override
    public void connectionError(Exception error) {
        logger.error(error.getMessage(), error);
        if (listener != null) {
            listener.connectionClosed();
        }
    }

    @Override
    public void connectionEstablished() {
        logger.info("Connected to Extended order book WebSocket");
    }

    @Override
    public void connectionOpened() {
        logger.info("Opened connection to Extended order book WebSocket");
    }

    @Override
    public void messageReceived(String message) {
        try {
            JsonObject root = JsonParser.parseString(message).getAsJsonObject();

            String type = getString(root, "type");
            if (type == null) {
                // Some frames may not carry a type; ignore non-data frames.
                if (!root.has("data")) {
                    return;
                }
            }

            JsonObject data = null;
            if (root.has("data") && !root.get("data").isJsonNull() && root.get("data").isJsonObject()) {
                data = root.getAsJsonObject("data");
            }
            if (data == null) {
                return;
            }

            long timestampMillis = getLong(root, "ts");
            ZonedDateTime timestamp = timestampMillis > 0
                    ? ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), ZoneId.of("UTC"))
                    : ZonedDateTime.now(ZoneId.of("UTC"));

            if (type != null && "SNAPSHOT".equalsIgnoreCase(type)) {
                orderBook.handleSnapshot(data, timestamp);
            } else {
                orderBook.applyDelta(data, timestamp);
            }
        } catch (Exception e) {
            logger.error("Error processing order book message: {}", message, e);
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
            return -1L;
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Exception e) {
            return -1L;
        }
    }
}
