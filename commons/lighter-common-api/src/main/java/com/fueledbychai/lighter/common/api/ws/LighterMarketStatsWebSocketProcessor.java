package com.fueledbychai.lighter.common.api.ws;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class LighterMarketStatsWebSocketProcessor extends AbstractWebSocketProcessor<LighterMarketStatsUpdate> {

    private static final Logger logger = LoggerFactory.getLogger(LighterMarketStatsWebSocketProcessor.class);

    public LighterMarketStatsWebSocketProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected LighterMarketStatsUpdate parseMessage(String message) {
        JSONObject root = new JSONObject(message);
        String channel = root.optString("channel", null);
        if (!isMarketStatsChannel(channel)) {
            return null;
        }

        Object marketStatsObject = root.opt("market_stats");
        if (!(marketStatsObject instanceof JSONObject)) {
            return null;
        }

        JSONObject marketStatsJson = (JSONObject) marketStatsObject;
        if (marketStatsJson.has("market_id")) {
            String marketId = marketStatsJson.opt("market_id").toString();
            Map<String, LighterMarketStats> payload = new LinkedHashMap<>();
            payload.put(marketId, parseMarketStats(marketStatsJson, marketId));
            return new LighterMarketStatsUpdate(channel, payload);
        }

        Map<String, LighterMarketStats> payload = new LinkedHashMap<>();
        for (String key : marketStatsJson.keySet()) {
            Object statObj = marketStatsJson.opt(key);
            if (statObj instanceof JSONObject) {
                payload.put(key, parseMarketStats((JSONObject) statObj, key));
            }
        }
        return new LighterMarketStatsUpdate(channel, payload);
    }

    private boolean isMarketStatsChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String prefix = LighterWSClientBuilder.WS_TYPE_MARKET_STATS;
        return channel.startsWith(prefix + "/") || channel.startsWith(prefix + ":");
    }

    protected LighterMarketStats parseMarketStats(JSONObject json, String fallbackMarketId) {
        LighterMarketStats stats = new LighterMarketStats();
        Integer marketId = parseInteger(json.opt("market_id"));
        if (marketId == null) {
            marketId = parseInteger(fallbackMarketId);
        }
        stats.setMarketId(marketId);
        stats.setMarkPrice(parseBigDecimal(json.opt("mark_price")));
        stats.setIndexPrice(parseBigDecimal(json.opt("index_price")));
        stats.setDailyBaseVolume(parseBigDecimal(json.opt("daily_base_volume")));
        stats.setDailyQuoteVolume(parseBigDecimal(json.opt("daily_quote_volume")));
        stats.setOpenInterest(parseBigDecimal(json.opt("open_interest")));
        stats.setFundingRate(parseBigDecimal(json.opt("funding_rate")));
        stats.setDailyLow(parseBigDecimal(json.opt("daily_low")));
        stats.setDailyHigh(parseBigDecimal(json.opt("daily_high")));
        stats.setDailyPriceChange24h(parseBigDecimal(json.opt("daily_price_change_24h")));
        stats.setDailyPriceChangePercent24h(parseBigDecimal(json.opt("daily_price_change_percent_24h")));
        stats.setLastPrice(parseBigDecimal(json.opt("last_price")));
        return stats;
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
}
