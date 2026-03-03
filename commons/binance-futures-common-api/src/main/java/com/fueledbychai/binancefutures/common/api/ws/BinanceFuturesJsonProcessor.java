package com.fueledbychai.binancefutures.common.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class BinanceFuturesJsonProcessor extends AbstractWebSocketProcessor<JsonNode> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BinanceFuturesJsonProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected JsonNode parseMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (root == null || root.isNull()) {
                return null;
            }
            if (root.has("result") && !root.has("data")) {
                return null;
            }
            return root.has("data") ? root.get("data") : root;
        } catch (Exception e) {
            logger.debug("Ignoring malformed Binance futures websocket payload: {}", message, e);
            return null;
        }
    }
}
