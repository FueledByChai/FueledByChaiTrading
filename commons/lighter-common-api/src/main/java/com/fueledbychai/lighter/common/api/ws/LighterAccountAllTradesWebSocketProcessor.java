package com.fueledbychai.lighter.common.api.ws;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fueledbychai.websocket.IWebSocketClosedListener;

public class LighterAccountAllTradesWebSocketProcessor extends LighterTradesWebSocketProcessor {

    public LighterAccountAllTradesWebSocketProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected boolean isTradeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_ACCOUNT_ALL_TRADES;
        return channel.startsWith(prefix + "/") || channel.startsWith(prefix + ":");
    }

    @Override
    protected Integer parseMarketIdFromChannel(String channel) {
        return null;
    }

    @Override
    protected LighterTradesUpdate parseMessage(String message) {
        JSONObject root = new JSONObject(message);
        String channel = root.optString("channel", null);
        if (!isTradeChannel(channel)) {
            return null;
        }

        List<LighterTrade> trades = new ArrayList<>();
        Object tradesValue = root.opt("trades");
        if (tradesValue instanceof JSONArray tradesJson) {
            appendTrades(tradesJson, null, trades);
        } else if (tradesValue instanceof JSONObject tradesByMarketJson) {
            Iterator<String> keys = tradesByMarketJson.keys();
            while (keys.hasNext()) {
                String marketKey = keys.next();
                Integer fallbackMarketId = parseInteger(marketKey);
                Object marketTradesValue = tradesByMarketJson.opt(marketKey);
                if (marketTradesValue instanceof JSONArray marketTradesJson) {
                    appendTrades(marketTradesJson, fallbackMarketId, trades);
                }
            }
        } else {
            return null;
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

    protected void appendTrades(JSONArray tradesJson, Integer fallbackMarketId, List<LighterTrade> trades) {
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
    }
}
