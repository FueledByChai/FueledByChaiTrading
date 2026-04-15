package com.fueledbychai.hibachi.common.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

/**
 * Generic JSON-parsing processor for Hibachi WebSocket frames.
 *
 * <p>Subclasses or downstream consumers receive the parsed {@link JsonNode}; topic
 * routing is the listener's responsibility (or can be done via {@link HibachiTopicRouter}).
 */
public class HibachiJsonProcessor extends AbstractWebSocketProcessor<JsonNode> {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    public HibachiJsonProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected JsonNode parseMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            return root == null || root.isNull() ? null : root;
        } catch (Exception e) {
            logger.debug("Ignoring malformed Hibachi WebSocket payload: {}", message, e);
            return null;
        }
    }
}
