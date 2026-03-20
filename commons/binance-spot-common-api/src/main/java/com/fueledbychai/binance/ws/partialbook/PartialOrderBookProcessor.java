package com.fueledbychai.binance.ws.partialbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class PartialOrderBookProcessor extends AbstractWebSocketProcessor<OrderBookSnapshot> {

    private static final Logger logger = LoggerFactory.getLogger(PartialOrderBookProcessor.class);
    ObjectMapper objectMapper = new ObjectMapper();

    public PartialOrderBookProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected OrderBookSnapshot parseMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("data");
            if (!payload.isObject()) {
                payload = root;
            }

            OrderBookSnapshot obs = objectMapper.treeToValue(payload, OrderBookSnapshot.class);
            if (obs.getEventTime() == null) {
                JsonNode rootEventTime = root.get("E");
                if (rootEventTime != null && rootEventTime.canConvertToLong()) {
                    obs.setEventTime(rootEventTime.longValue());
                }
            }
            return obs;
        } catch (Exception e) {
            logger.error("Error parsing message: " + message, e);
            return null;
        }
    }

}
