package com.fueledbychai.lighter.common.api.ws.processor;

import java.math.BigDecimal;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.lighter.common.api.ws.client.LighterWSClientBuilder;
import com.fueledbychai.lighter.common.api.ws.model.LighterTickerUpdate;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class LighterTickerWebSocketProcessor extends AbstractWebSocketProcessor<LighterTickerUpdate> {

    private static final Logger logger = LoggerFactory.getLogger(LighterTickerWebSocketProcessor.class);

    public LighterTickerWebSocketProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected LighterTickerUpdate parseMessage(String message) {
        JSONObject root = new JSONObject(message);
        String channel = root.optString("channel", null);
        if (!isTickerChannel(channel)) {
            return null;
        }
        Long timestamp = parseLong(root.opt("timestamp"));
        Integer marketId = parseMarketIdFromChannel(channel);

        JSONObject tickerJson = root.optJSONObject("ticker");
        if (tickerJson == null) {
            return null;
        }

        if (marketId == null) {
            marketId = parseInteger(tickerJson.opt("market_id"));
        }

        LighterTickerUpdate update = new LighterTickerUpdate(channel, timestamp, marketId);

        // Nested format: "b": {"price": "...", "size": "..."}, "a": {"price": "...", "size": "..."}
        JSONObject bid = tickerJson.optJSONObject("b");
        JSONObject ask = tickerJson.optJSONObject("a");
        if (bid != null) {
            update.setBestBidPrice(readDecimal(bid, "price"));
            update.setBestBidQuantity(readDecimal(bid, "size", "quantity"));
        }
        if (ask != null) {
            update.setBestAskPrice(readDecimal(ask, "price"));
            update.setBestAskQuantity(readDecimal(ask, "size", "quantity"));
        }
        return update;
    }

    private boolean isTickerChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_TICKER;
        return channel.startsWith(prefix + "/") || channel.startsWith(prefix + ":");
    }

    private Integer parseMarketIdFromChannel(String channel) {
        if (channel == null) {
            return null;
        }
        int lastSep = Math.max(channel.lastIndexOf('/'), channel.lastIndexOf(':'));
        if (lastSep < 0 || lastSep >= channel.length() - 1) {
            return null;
        }
        return parseInteger(channel.substring(lastSep + 1));
    }

    protected BigDecimal readDecimal(JSONObject json, String... keys) {
        if (json == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Object value = json.opt(key);
            BigDecimal parsed = parseBigDecimal(value);
            if (parsed != null) {
                return parsed;
            }
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
            return null;
        }
    }
}
