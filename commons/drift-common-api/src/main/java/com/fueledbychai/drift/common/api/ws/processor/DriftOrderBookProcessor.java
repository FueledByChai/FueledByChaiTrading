package com.fueledbychai.drift.common.api.ws.processor;

import com.fueledbychai.drift.common.api.DriftRestApi;
import com.fueledbychai.drift.common.api.model.DriftOrderBookSnapshot;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DriftOrderBookProcessor extends AbstractWebSocketProcessor<DriftOrderBookSnapshot> {

    public DriftOrderBookProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected DriftOrderBookSnapshot parseMessage(String message) {
        JsonElement element = JsonParser.parseString(message);
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("bids") || !object.has("asks")) {
            return null;
        }
        return DriftWebSocketParsers.parseOrderBookSnapshot(object);
    }
}
