package com.fueledbychai.broker.extended;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Processor for the Extended private {@code /account} WebSocket stream.
 * <p>
 * The account stream wraps each message as
 * {@code { "type": "TRADE"|"ORDER"|"BALANCE"|..., "data": {...}, "ts":..., "seq":... }}.
 * This processor parses the raw text into a {@link JsonObject} and hands the
 * root object to listeners; the broker inspects the {@code type} field and
 * dispatches accordingly.
 */
public class ExtendedAccountStreamProcessor extends AbstractWebSocketProcessor<JsonObject> {

    private static final Logger log = LoggerFactory.getLogger(ExtendedAccountStreamProcessor.class);

    public ExtendedAccountStreamProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected JsonObject parseMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(message);
            if (element == null || !element.isJsonObject()) {
                log.debug("Ignoring non-object account stream message: {}", message);
                return null;
            }
            return element.getAsJsonObject();
        } catch (Exception e) {
            log.warn("Failed to parse account stream message: {}", message, e);
            return null;
        }
    }
}
