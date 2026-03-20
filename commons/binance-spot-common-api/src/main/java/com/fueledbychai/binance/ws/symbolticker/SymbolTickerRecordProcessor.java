package com.fueledbychai.binance.ws.symbolticker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class SymbolTickerRecordProcessor extends AbstractWebSocketProcessor<SymbolTickerRecord> {

    private static final Logger logger = LoggerFactory.getLogger(SymbolTickerRecordProcessor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SymbolTickerRecordProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected SymbolTickerRecord parseMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode dataNode = root.has("data") ? root.get("data") : root;
            return objectMapper.treeToValue(dataNode, SymbolTickerRecord.class);
        } catch (Exception e) {
            logger.error("Error parsing message: " + message, e);
            return null;
        }
    }
}
