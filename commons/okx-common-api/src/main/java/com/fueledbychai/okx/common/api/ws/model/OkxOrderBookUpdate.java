package com.fueledbychai.okx.common.api.ws.model;

import java.util.List;

public class OkxOrderBookUpdate {

    protected final String instrumentId;
    protected final Long timestamp;
    protected final List<OkxOrderBookLevel> bids;
    protected final List<OkxOrderBookLevel> asks;

    public OkxOrderBookUpdate(String instrumentId, Long timestamp, List<OkxOrderBookLevel> bids,
            List<OkxOrderBookLevel> asks) {
        this.instrumentId = instrumentId;
        this.timestamp = timestamp;
        this.bids = bids;
        this.asks = asks;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public List<OkxOrderBookLevel> getBids() {
        return bids;
    }

    public List<OkxOrderBookLevel> getAsks() {
        return asks;
    }
}
