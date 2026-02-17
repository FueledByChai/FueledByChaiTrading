package com.fueledbychai.lighter.common.api.ws.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LighterTradesUpdate {

    private final String channel;
    private final String messageType;
    private final List<LighterTrade> trades;

    public LighterTradesUpdate(String channel, String messageType, List<LighterTrade> trades) {
        this.channel = channel;
        this.messageType = messageType;
        this.trades = toUnmodifiableList(trades);
    }

    public String getChannel() {
        return channel;
    }

    public String getMessageType() {
        return messageType;
    }

    public List<LighterTrade> getTrades() {
        return trades;
    }

    public LighterTrade getFirstTrade() {
        if (trades.isEmpty()) {
            return null;
        }
        return trades.get(0);
    }

    private List<LighterTrade> toUnmodifiableList(List<LighterTrade> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
