package com.fueledbychai.lighter.common.api.ws.model;

public class LighterAccountStatsUpdate {

    private final String channel;
    private final String messageType;
    private final Long accountIndex;
    private final LighterAccountStats stats;

    public LighterAccountStatsUpdate(String channel, String messageType, Long accountIndex, LighterAccountStats stats) {
        this.channel = channel;
        this.messageType = messageType;
        this.accountIndex = accountIndex;
        this.stats = stats;
    }

    public String getChannel() {
        return channel;
    }

    public String getMessageType() {
        return messageType;
    }

    public Long getAccountIndex() {
        return accountIndex;
    }

    public LighterAccountStats getStats() {
        return stats;
    }
}
