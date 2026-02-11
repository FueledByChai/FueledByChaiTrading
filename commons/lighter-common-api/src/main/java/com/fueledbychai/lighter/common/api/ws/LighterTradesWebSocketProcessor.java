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

public class LighterTradesWebSocketProcessor extends AbstractWebSocketProcessor<LighterTradesUpdate> {

    private static final Logger logger = LoggerFactory.getLogger(LighterTradesWebSocketProcessor.class);

    public LighterTradesWebSocketProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected LighterTradesUpdate parseMessage(String message) {
        JSONObject root = new JSONObject(message);
        String channel = root.optString("channel", null);
        if (!isTradeChannel(channel)) {
            return null;
        }

        JSONArray tradesJson = root.optJSONArray("trades");
        if (tradesJson == null || tradesJson.isEmpty()) {
            return null;
        }

        Integer fallbackMarketId = parseMarketIdFromChannel(channel);
        List<LighterTrade> trades = new ArrayList<>();
        for (int i = 0; i < tradesJson.length(); i++) {
            Object value = tradesJson.opt(i);
            if (!(value instanceof JSONObject tradeJson)) {
                continue;
            }
            LighterTrade trade = parseTrade(tradeJson, fallbackMarketId);
            if (trade != null) {
                trades.add(trade);
            }
        }

        if (trades.isEmpty()) {
            return null;
        }

        String messageType = root.optString("type", null);
        if (messageType != null && messageType.isBlank()) {
            messageType = null;
        }
        return new LighterTradesUpdate(channel, messageType, trades);
    }

    protected boolean isTradeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_TRADE;
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

    protected LighterTrade parseTrade(JSONObject tradeJson, Integer fallbackMarketId) {
        LighterTrade trade = new LighterTrade();
        trade.setId(parseLong(tradeJson.opt("id")));
        trade.setTxHash(tradeJson.optString("tx_hash", null));
        trade.setType(tradeJson.optString("type", null));
        Integer marketId = parseInteger(tradeJson.opt("market_id"));
        trade.setMarketId(marketId != null ? marketId : fallbackMarketId);
        trade.setSize(parseBigDecimal(tradeJson.opt("size")));
        trade.setPrice(parseBigDecimal(tradeJson.opt("price")));
        trade.setUsdAmount(parseBigDecimal(tradeJson.opt("usd_amount")));
        trade.setAskId(parseLong(tradeJson.opt("ask_id")));
        trade.setBidId(parseLong(tradeJson.opt("bid_id")));
        trade.setAskAccountId(parseLong(tradeJson.opt("ask_account_id")));
        trade.setBidAccountId(parseLong(tradeJson.opt("bid_account_id")));
        trade.setMakerAsk(parseBoolean(tradeJson.opt("is_maker_ask")));
        trade.setBlockHeight(parseLong(tradeJson.opt("block_height")));
        trade.setTimestamp(parseLong(tradeJson.opt("timestamp")));
        trade.setTakerFee(parseBigDecimal(tradeJson.opt("taker_fee")));
        return trade;
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

    protected Boolean parseBoolean(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = value.toString();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
