package com.fueledbychai.drift.common.api.ws.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fueledbychai.drift.common.api.model.DriftGatewayWebSocketEvent;
import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.drift.common.api.model.DriftOrderBookLevel;
import com.fueledbychai.drift.common.api.model.DriftOrderBookSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class DriftWebSocketParsers {

    private static final BigDecimal PRICE_PRECISION = new BigDecimal("1000000");
    private static final BigDecimal BASE_PRECISION = new BigDecimal("1000000000");

    private DriftWebSocketParsers() {
    }

    public static DriftOrderBookSnapshot parseOrderBookSnapshot(JsonObject root) {
        return new DriftOrderBookSnapshot(getString(root, "marketName"),
                root.has("marketType") ? DriftMarketType.fromString(getString(root, "marketType")) : DriftMarketType.PERP,
                getInt(root, "marketIndex", 0), getLong(root, "ts", 0L), getLong(root, "slot", 0L),
                scalePrice(getBigDecimal(root, "markPrice")), scalePrice(getBigDecimal(root, "bestBidPrice")),
                scalePrice(getBigDecimal(root, "bestAskPrice")), scalePrice(getBigDecimal(root, "oracle")),
                parseLevels(getArray(root, "bids")), parseLevels(getArray(root, "asks")));
    }

    public static DriftGatewayWebSocketEvent parseGatewayEvent(JsonObject root) {
        JsonObject data = getObject(root, "data");
        String eventType = getString(root, "eventType");
        JsonObject payload = data;
        if ((eventType == null || eventType.isBlank()) && !data.entrySet().isEmpty()) {
            eventType = data.entrySet().iterator().next().getKey();
        }
        if (eventType != null && data.has(eventType) && data.get(eventType).isJsonObject()) {
            payload = data.getAsJsonObject(eventType);
        }
        return new DriftGatewayWebSocketEvent(getString(root, "channel"), eventType, getInt(root, "subAccountId", 0),
                payload);
    }

    private static List<DriftOrderBookLevel> parseLevels(JsonArray levels) {
        List<DriftOrderBookLevel> parsed = new ArrayList<>();
        for (JsonElement levelElement : levels) {
            if (!levelElement.isJsonObject()) {
                continue;
            }
            JsonObject level = levelElement.getAsJsonObject();
            Map<String, BigDecimal> sources = new HashMap<>();
            JsonObject sourceObject = getObject(level, "sources");
            for (Map.Entry<String, JsonElement> entry : sourceObject.entrySet()) {
                sources.put(entry.getKey(), scaleSize(asBigDecimal(entry.getValue())));
            }
            parsed.add(new DriftOrderBookLevel(scalePrice(getBigDecimal(level, "price")),
                    scaleSize(getBigDecimal(level, "size")), sources));
        }
        return parsed;
    }

    private static JsonObject getObject(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || !object.get(member).isJsonObject()) {
            return new JsonObject();
        }
        return object.getAsJsonObject(member);
    }

    private static JsonArray getArray(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || !object.get(member).isJsonArray()) {
            return new JsonArray();
        }
        return object.getAsJsonArray(member);
    }

    private static BigDecimal getBigDecimal(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member)) {
            return null;
        }
        return asBigDecimal(object.get(member));
    }

    private static BigDecimal asBigDecimal(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        String value = element.getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static String getString(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || object.get(member).isJsonNull()) {
            return null;
        }
        return object.get(member).getAsString();
    }

    private static int getInt(JsonObject object, String member, int defaultValue) {
        if (object == null || member == null || !object.has(member) || object.get(member).isJsonNull()) {
            return defaultValue;
        }
        return object.get(member).getAsInt();
    }

    private static long getLong(JsonObject object, String member, long defaultValue) {
        if (object == null || member == null || !object.has(member) || object.get(member).isJsonNull()) {
            return defaultValue;
        }
        return object.get(member).getAsLong();
    }

    private static BigDecimal scalePrice(BigDecimal raw) {
        return raw == null ? null : raw.divide(PRICE_PRECISION, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleSize(BigDecimal raw) {
        return raw == null ? null : raw.divide(BASE_PRECISION, 9, RoundingMode.HALF_UP);
    }
}
