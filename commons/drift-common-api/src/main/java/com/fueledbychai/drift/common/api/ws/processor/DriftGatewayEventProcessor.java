package com.fueledbychai.drift.common.api.ws.processor;

import com.fueledbychai.drift.common.api.model.DriftGatewayWebSocketEvent;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DriftGatewayEventProcessor extends AbstractWebSocketProcessor<DriftGatewayWebSocketEvent> {

    public DriftGatewayEventProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected DriftGatewayWebSocketEvent parseMessage(String message) {
        JsonElement element = JsonParser.parseString(message);
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("channel")) {
            return null;
        }
        return DriftWebSocketParsers.parseGatewayEvent(object);
    }
}
