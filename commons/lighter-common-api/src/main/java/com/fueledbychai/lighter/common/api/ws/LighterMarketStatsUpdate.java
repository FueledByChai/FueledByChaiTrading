package com.fueledbychai.lighter.common.api.ws;

import java.util.Collections;
import java.util.Map;

public class LighterMarketStatsUpdate {

    private final String channel;
    private final Map<String, LighterMarketStats> marketStatsByMarketId;

    public LighterMarketStatsUpdate(String channel, Map<String, LighterMarketStats> marketStatsByMarketId) {
        this.channel = channel;
        this.marketStatsByMarketId = marketStatsByMarketId == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(marketStatsByMarketId);
    }

    public String getChannel() {
        return channel;
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

