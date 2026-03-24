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

        Long nonce = parseLong(root.opt("nonce"));
        Long timestamp = parseLong(root.opt("timestamp"));
        String messageType = root.optString("type", null);

        Object tickerObj = root.opt("ticker");
        if (!(tickerObj instanceof JSONObject)) {
            return null;
        }
        JSONObject tickerJson = (JSONObject) tickerObj;

        String symbol = tickerJson.optString("s", null);
        BigDecimal askPrice = null;
        BigDecimal askSize = null;
        BigDecimal bidPrice = null;
        BigDecimal bidSize = null;

        Object askObj = tickerJson.opt("a");
        if (askObj instanceof JSONObject askJson) {
            askPrice = parseBigDecimal(askJson.opt("price"));
            askSize = parseBigDecimal(askJson.opt("size"));
        }

        Object bidObj = tickerJson.opt("b");
        if (bidObj instanceof JSONObject bidJson) {
            bidPrice = parseBigDecimal(bidJson.opt("price"));
            bidSize = parseBigDecimal(bidJson.opt("size"));
        }

        Integer marketId = parseMarketIdFromChannel(channel);

        return new LighterTickerUpdate(channel, marketId, nonce, timestamp, symbol, askPrice, askSize, bidPrice,
                bidSize, messageType);
    }

    private boolean isTickerChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_TICKER;
        return channel.startsWith(prefix + "/") || channel.startsWith(prefix + ":");
    }

    protected Integer parseMarketIdFromChannel(String channel) {
        if (channel == null) {
            return null;
        }
        int sep = channel.lastIndexOf('/');
        if (sep < 0) {
            sep = channel.lastIndexOf(':');
        }
        if (sep < 0 || sep >= channel.length() - 1) {
            return null;
        }
        return parseInteger(channel.substring(sep + 1));
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
