package com.fueledbychai.aster.common.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class AsterJsonProcessor extends AbstractWebSocketProcessor<JsonNode> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AsterJsonProcessor(IWebSocketClosedListener listener) {
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
            logger.debug("Ignoring malformed Aster websocket payload: {}", message, e);
            return null;
        }
    }
}
