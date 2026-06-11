package com.fueledbychai.binance.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

/**
 * Minimal processor for the Binance spot full-depth diff stream ({@code <symbol>@depth@100ms}).
 * Hands each {@code depthUpdate} frame to listeners as the raw payload {@link JsonNode} (the
 * {@code U}/{@code u}/{@code b}/{@code a} fields), leaving sequencing and absolute-book
 * reconstruction to the consumer (the data recorder). Combined-stream envelopes that wrap the
 * payload in {@code data} are unwrapped; bare frames are passed through as-is.
 */
public class RawDiffDepthProcessor extends AbstractWebSocketProcessor<JsonNode> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RawDiffDepthProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected JsonNode parseMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("data");
            return payload.isObject() ? payload : root;
        } catch (Exception e) {
            logger.error("Error parsing diff-depth message: " + message, e);
            return null;
        }
    }
}
