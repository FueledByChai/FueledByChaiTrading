package com.fueledbychai.drift.common.api.ws.processor;

import com.fueledbychai.drift.common.api.model.DriftMarketType;
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
        JsonObject snapshotPayload = extractSnapshotPayload(object);
        if (snapshotPayload == null) {
            return null;
        }
        return DriftWebSocketParsers.parseOrderBookSnapshot(snapshotPayload);
    }

    protected JsonObject extractSnapshotPayload(JsonObject object) {
        if (containsOrderBook(object)) {
            return object;
        }
        if (!object.has("data")) {
            return null;
        }

        JsonObject data = extractDataObject(object.get("data"));
        if (!containsOrderBook(data)) {
            return null;
        }

        JsonObject normalized = data.deepCopy();
        if (!normalized.has("marketName") && object.has("market")) {
            normalized.add("marketName", object.get("market"));
        }
        if (!normalized.has("marketType") && object.has("marketType")) {
            normalized.add("marketType", object.get("marketType"));
        }
        if (!normalized.has("channel") && object.has("channel")) {
            normalized.add("channel", object.get("channel"));
        }
        enrichFromChannel(normalized);
        return normalized;
    }

    protected boolean containsOrderBook(JsonObject object) {
        return object != null && (object.has("bids") || object.has("asks"));
    }

    protected JsonObject extractDataObject(JsonElement data) {
        if (data == null || data.isJsonNull()) {
            return null;
        }
        if (data.isJsonObject()) {
            return data.getAsJsonObject();
        }
        if (!data.isJsonPrimitive() || !data.getAsJsonPrimitive().isString()) {
            return null;
        }

        String raw = data.getAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    protected void enrichFromChannel(JsonObject normalized) {
        if (normalized == null || !normalized.has("channel")) {
            return;
        }
        String channel = normalized.get("channel").getAsString();
        String[] parts = channel == null ? new String[0] : channel.split("_");
        if (parts.length < 3 || !"orderbook".equals(parts[0])) {
            return;
        }

        if (!normalized.has("marketType")) {
            try {
                DriftMarketType marketType = DriftMarketType.fromString(parts[1]);
                if (marketType != null) {
                    normalized.addProperty("marketType", marketType.getApiValue());
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore unexpected channel suffixes and keep parsing the payload.
            }
        }
        if (!normalized.has("marketIndex")) {
            try {
                normalized.addProperty("marketIndex", Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) {
                // Ignore unexpected channel suffixes and keep parsing the payload.
            }
        }
    }
}
