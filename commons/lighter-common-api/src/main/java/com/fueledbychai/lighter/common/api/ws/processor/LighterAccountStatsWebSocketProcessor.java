package com.fueledbychai.lighter.common.api.ws.processor;

import java.math.BigDecimal;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.lighter.common.api.ws.client.LighterWSClientBuilder;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStats;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStatsBreakdown;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStatsUpdate;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class LighterAccountStatsWebSocketProcessor extends AbstractWebSocketProcessor<LighterAccountStatsUpdate> {

    private static final Logger logger = LoggerFactory.getLogger(LighterAccountStatsWebSocketProcessor.class);
    private static final String LEGACY_ACCOUNT_STATS_CHANNEL_PREFIX = "account_stats";

    public LighterAccountStatsWebSocketProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected LighterAccountStatsUpdate parseMessage(String message) {
        JSONObject root = new JSONObject(message);
        String channel = root.optString("channel", null);
        if (!isAccountStatsChannel(channel)) {
            return null;
        }

        JSONObject statsJson = root.optJSONObject("stats");
        if (statsJson == null || statsJson.isEmpty()) {
            return null;
        }

        String messageType = parseString(root.opt("type"));
        Long accountIndex = parseAccountIndexFromChannel(channel);
        if (accountIndex == null) {
            accountIndex = parseLong(root.opt("account"));
        }

        LighterAccountStats stats = parseStats(statsJson);
        if (stats == null) {
            return null;
        }

        return new LighterAccountStatsUpdate(channel, messageType, accountIndex, stats);
    }

    protected boolean isAccountStatsChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String primaryPrefix = LighterWSClientBuilder.WS_TYPE_ACCOUNT_STATS;
        if (channel.startsWith(primaryPrefix + "/") || channel.startsWith(primaryPrefix + ":")) {
            return true;
        }
        return channel.startsWith(LEGACY_ACCOUNT_STATS_CHANNEL_PREFIX + "/")
                || channel.startsWith(LEGACY_ACCOUNT_STATS_CHANNEL_PREFIX + ":");
    }

    protected Long parseAccountIndexFromChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        int idx = Math.max(channel.lastIndexOf(':'), channel.lastIndexOf('/'));
        if (idx < 0 || idx >= channel.length() - 1) {
            return null;
        }
        return parseLong(channel.substring(idx + 1));
    }

    protected LighterAccountStats parseStats(JSONObject statsJson) {
        if (statsJson == null) {
            return null;
        }

        LighterAccountStats stats = new LighterAccountStats();
        stats.setCollateral(parseBigDecimal(statsJson.opt("collateral")));
        stats.setPortfolioValue(parseBigDecimal(statsJson.opt("portfolio_value")));
        stats.setLeverage(parseBigDecimal(statsJson.opt("leverage")));
        stats.setAvailableBalance(parseBigDecimal(statsJson.opt("available_balance")));
        stats.setMarginUsage(parseBigDecimal(statsJson.opt("margin_usage")));
        stats.setBuyingPower(parseBigDecimal(statsJson.opt("buying_power")));
        stats.setCrossStats(parseBreakdown(statsJson.optJSONObject("cross_stats")));
        stats.setTotalStats(parseBreakdown(statsJson.optJSONObject("total_stats")));
        return stats;
    }

    protected LighterAccountStatsBreakdown parseBreakdown(JSONObject breakdownJson) {
        if (breakdownJson == null) {
            return null;
        }
        LighterAccountStatsBreakdown breakdown = new LighterAccountStatsBreakdown();
        breakdown.setCollateral(parseBigDecimal(breakdownJson.opt("collateral")));
        breakdown.setPortfolioValue(parseBigDecimal(breakdownJson.opt("portfolio_value")));
        breakdown.setLeverage(parseBigDecimal(breakdownJson.opt("leverage")));
        breakdown.setAvailableBalance(parseBigDecimal(breakdownJson.opt("available_balance")));
        breakdown.setMarginUsage(parseBigDecimal(breakdownJson.opt("margin_usage")));
        breakdown.setBuyingPower(parseBigDecimal(breakdownJson.opt("buying_power")));
        return breakdown;
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

    protected String parseString(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        String text = value.toString();
        if (text.isBlank()) {
            return null;
        }
        return text;
    }
}
