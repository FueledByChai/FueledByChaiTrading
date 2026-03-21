package com.fueledbychai.lighter.common.api.ws.model;

import java.util.Collections;
import java.util.Map;

public class LighterMarketStatsUpdate {

    private final String channel;
    private final Long timestamp;
    private final Map<String, LighterMarketStats> marketStatsByMarketId;

    public LighterMarketStatsUpdate(String channel, Map<String, LighterMarketStats> marketStatsByMarketId) {
        this(channel, null, marketStatsByMarketId);
    }

    public LighterMarketStatsUpdate(String channel, Long timestamp,
            Map<String, LighterMarketStats> marketStatsByMarketId) {
        this.channel = channel;
        this.timestamp = timestamp;
        this.marketStatsByMarketId = marketStatsByMarketId == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(marketStatsByMarketId);
    }

    public String getChannel() {
        return channel;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public Map<String, LighterMarketStats> getMarketStatsByMarketId() {
        return marketStatsByMarketId;
    }

    public LighterMarketStats getMarketStats(String marketId) {
        if (marketId == null) {
            return null;
        }
        return marketStatsByMarketId.get(marketId);
    }

    public boolean isAllMarketsChannel() {
        return channel != null && channel.endsWith("/all");
    }
}

