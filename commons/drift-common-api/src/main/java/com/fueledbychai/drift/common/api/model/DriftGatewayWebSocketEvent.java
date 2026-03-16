package com.fueledbychai.drift.common.api.model;

import com.google.gson.JsonObject;

public class DriftGatewayWebSocketEvent {

    private final String channel;
    private final String eventType;
    private final int subAccountId;
    private final JsonObject payload;

    public DriftGatewayWebSocketEvent(String channel, String eventType, int subAccountId, JsonObject payload) {
        this.channel = channel;
        this.eventType = eventType;
        this.subAccountId = subAccountId;
        this.payload = payload;
    }

    public String getChannel() {
        return channel;
    }

    public String getEventType() {
        return eventType;
    }

    public int getSubAccountId() {
        return subAccountId;
    }

    public JsonObject getPayload() {
        return payload;
    }
}
