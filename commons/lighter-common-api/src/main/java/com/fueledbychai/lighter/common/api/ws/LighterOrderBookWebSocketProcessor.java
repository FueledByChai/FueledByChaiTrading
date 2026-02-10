package com.fueledbychai.lighter.common.api.ws;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class LighterOrderBookWebSocketProcessor extends AbstractWebSocketProcessor<LighterOrderBookUpdate> {

    private static final Logger logger = LoggerFactory.getLogger(LighterOrderBookWebSocketProcessor.class);

    public LighterOrderBookWebSocketProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected LighterOrderBookUpdate parseMessage(String message) {
        JSONObject root = new JSONObject(message);
        String channel = root.optString("channel", null);
        if (!isOrderBookChannel(channel)) {
            return null;
        }

        Object orderBookObject = root.opt("order_book");
        if (!(orderBookObject instanceof JSONObject)) {
            return null;
        }

        JSONObject orderBookJson = (JSONObject) orderBookObject;
        Integer marketId = parseInteger(orderBookJson.opt("market_id"));
        if (marketId == null) {
            marketId = parseMarketIdFromChannel(channel);
        }

        Integer code = parseInteger(orderBookJson.opt("code"));
        List<LighterOrderBookLevel> asks = parseLevels(orderBookJson.optJSONArray("asks"));
        List<LighterOrderBookLevel> bids = parseLevels(orderBookJson.optJSONArray("bids"));

        Long offset = parseLong(root.opt("offset"));
        if (offset == null) {
            offset = parseLong(orderBookJson.opt("offset"));
        }
        Long nonce = parseLong(root.opt("nonce"));
        if (nonce == null) {
            nonce = parseLong(orderBookJson.opt("nonce"));
        }
        Long beginNonce = parseLong(root.opt("begin_nonce"));
        if (beginNonce == null) {
            beginNonce = parseLong(orderBookJson.opt("begin_nonce"));
        }
        Long timestamp = parseLong(root.opt("timestamp"));
        if (timestamp == null) {
            timestamp = parseLong(orderBookJson.opt("timestamp"));
        }

        String messageType = root.optString("type", null);
        if (messageType != null && messageType.isBlank()) {
            messageType = null;
        }

        return new LighterOrderBookUpdate(channel, marketId, code, asks, bids, offset, nonce, beginNonce, timestamp,
                messageType);
    }

    protected boolean isOrderBookChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_ORDER_BOOK;
        return channel.startsWith(prefix + "/") || channel.startsWith(prefix + ":");
    }

    protected Integer parseMarketIdFromChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        int idx = Math.max(channel.lastIndexOf(':'), channel.lastIndexOf('/'));
        if (idx < 0 || idx >= channel.length() - 1) {
            return null;
        }
        return parseInteger(channel.substring(idx + 1));
    }

    protected List<LighterOrderBookLevel> parseLevels(JSONArray levelsJson) {
        if (levelsJson == null || levelsJson.isEmpty()) {
            return List.of();
        }

        List<LighterOrderBookLevel> levels = new ArrayList<>();
        for (int i = 0; i < levelsJson.length(); i++) {
            Object levelObj = levelsJson.opt(i);
            LighterOrderBookLevel level = parseLevel(levelObj);
            if (level != null) {
                levels.add(level);
            }
        }
        return levels;
    }

    protected LighterOrderBookLevel parseLevel(Object levelObj) {
        if (levelObj instanceof JSONObject json) {
            BigDecimal price = parseBigDecimal(json.opt("price"));
            BigDecimal size = parseBigDecimal(json.opt("size"));
            if (price == null || size == null) {
                return null;
            }
            return new LighterOrderBookLevel(price, size);
        }

        if (levelObj instanceof JSONArray arr && arr.length() >= 2) {
            BigDecimal price = parseBigDecimal(arr.opt(0));
            BigDecimal size = parseBigDecimal(arr.opt(1));
            if (price == null || size == null) {
                return null;
            }
            return new LighterOrderBookLevel(price, size);
        }

        return null;
    }

    protected BigDecimal parseBigDecimal(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            logger.warn("Unable to parse decimal value '{}'", value);
            return null;
        }
    }

    protected Integer parseInteger(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (Exception e) {
            logger.warn("Unable to parse integer value '{}'", value);
            return null;
        }
    }

    protected Long parseLong(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (Exception e) {
            logger.warn("Unable to parse long value '{}'", value);
            return null;
        }
    }
}
