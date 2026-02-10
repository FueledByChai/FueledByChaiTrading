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
        stats.setMarkPrice(readDecimal(json, "mark_price"));
        stats.setIndexPrice(readDecimal(json, "index_price"));
        stats.setDailyBaseVolume(readDecimal(json, "daily_base_volume", "daily_base_token_volume"));
        stats.setDailyQuoteVolume(readDecimal(json, "daily_quote_volume", "daily_quote_token_volume"));
        stats.setOpenInterest(readDecimal(json, "open_interest"));
        BigDecimal currentFundingRate = readDecimal(json, "current_funding_rate");
        BigDecimal lastFundingRate = readDecimal(json, "funding_rate");
        stats.setCurrentFundingRate(currentFundingRate);
        stats.setLastFundingRate(lastFundingRate);
        stats.setFundingTimestamp(parseLong(json.opt("funding_timestamp")));
        stats.setDailyLow(readDecimal(json, "daily_low", "daily_price_low"));
        stats.setDailyHigh(readDecimal(json, "daily_high", "daily_price_high"));
        stats.setDailyPriceChange24h(readDecimal(json, "daily_price_change_24h", "daily_price_change"));
        stats.setDailyPriceChangePercent24h(
                readDecimal(json, "daily_price_change_percent_24h", "daily_price_change_percent"));
        stats.setLastPrice(readDecimal(json, "last_price", "last_trade_price"));
        return stats;
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
