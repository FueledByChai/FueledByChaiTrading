package com.fueledbychai.aster.common.api.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.websocket.IWebSocketClosedListener;
import com.fueledbychai.websocket.IWebSocketProcessor;

/**
 * Multiplexing processor for shared Aster WebSocket connections. Routes
 * incoming messages to the correct child processor by parsing the {@code stream}
 * field from Binance-style combined stream messages.
 *
 * Messages without a {@code stream} field (e.g., subscription confirmations)
 * are silently dropped.
 */
public class AsterMultiplexingProcessor implements IWebSocketProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AsterMultiplexingProcessor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, IWebSocketProcessor> processorsByChannel = new ConcurrentHashMap<>();
    private final IWebSocketClosedListener closedListener;
    private volatile Runnable onConnectedCallback;

    public AsterMultiplexingProcessor(IWebSocketClosedListener closedListener) {
        this.closedListener = closedListener;
    }

    public void setOnConnectedCallback(Runnable callback) {
        this.onConnectedCallback = callback;
    }

    public void addProcessor(String channel, IWebSocketProcessor processor) {
        if (channel != null && processor != null) {
            processorsByChannel.put(channel, processor);
        }
    }

    public void removeProcessor(String channel) {
        processorsByChannel.remove(channel);
    }

    public int getProcessorCount() {
        return processorsByChannel.size();
    }

    @Override
    public void messageReceived(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (root == null || root.isNull()) {
                return;
            }

            // Subscription confirmations have "result" and "id" but no "stream"/"data"
            if (root.has("result") && !root.has("data") && !root.has("stream")) {
                return;
            }

            // Route by stream field if present (Binance combined stream format)
            if (root.has("stream")) {
                routeToProcessor(root.get("stream").asText(), message);
                return;
            }

            // No stream field — derive channel from event type + symbol
            JsonNode payload = root.has("data") ? root.get("data") : root;
            String channel = deriveChannelFromPayload(payload);
            if (channel != null) {
                routeToProcessor(channel, message);
            }
        } catch (Exception e) {
            logger.debug("Error routing Aster multiplexed message", e);
        }
    }

    private void routeToProcessor(String channel, String message) {
        IWebSocketProcessor processor = processorsByChannel.get(channel);
        if (processor != null) {
            processor.messageReceived(message);
        }
    }

    /**
     * Derives the channel key from the message payload when no {@code stream}
     * field is present. Uses the {@code e} (event type) and {@code s} (symbol)
     * fields to reconstruct the channel name.
     */
    protected String deriveChannelFromPayload(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        String symbol = payload.has("s") ? payload.get("s").asText() : null;
        String eventType = payload.has("e") ? payload.get("e").asText() : null;
        if (symbol == null || symbol.isBlank() || eventType == null || eventType.isBlank()) {
            return null;
        }
        String symbolLower = symbol.toLowerCase();
        String channelSuffix = mapEventTypeToChannelSuffix(eventType);
        if (channelSuffix == null) {
            return null;
        }
        return symbolLower + channelSuffix;
    }

    private String mapEventTypeToChannelSuffix(String eventType) {
        return switch (eventType) {
            case "bookTicker" -> "@bookTicker";
            case "24hrTicker" -> "@ticker";
            case "markPriceUpdate" -> "@markPrice@1s";
            case "aggTrade" -> "@aggTrade";
            case "depthUpdate" -> "@depth5@100ms";
            default -> null;
        };
    }

    @Override
    public void connectionClosed(int code, String reason, boolean remote) {
        logger.info("Shared Aster WebSocket connection closed: {}", reason);
        if (closedListener != null) {
            closedListener.connectionClosed();
        }
    }

    @Override
    public void connectionError(Exception error) {
        logger.error("Shared Aster WebSocket connection error", error);
        if (closedListener != null) {
            closedListener.connectionClosed();
        }
    }

    @Override
    public void connectionEstablished() {
        // no-op
    }

    @Override
    public void connectionOpened() {
        Runnable callback = onConnectedCallback;
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                logger.warn("Error in shared WebSocket onConnected callback", e);
            }
        }
    }
}
