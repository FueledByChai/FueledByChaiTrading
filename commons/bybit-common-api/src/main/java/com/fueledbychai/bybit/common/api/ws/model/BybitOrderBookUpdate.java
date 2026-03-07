package com.fueledbychai.bybit.common.api.ws.model;

import java.util.Collections;
import java.util.List;

public class BybitOrderBookUpdate {

    protected final String instrumentId;
    protected final Long timestamp;
    protected final List<BybitOrderBookLevel> bids;
    protected final List<BybitOrderBookLevel> asks;

    public BybitOrderBookUpdate(String instrumentId, Long timestamp, List<BybitOrderBookLevel> bids,
            List<BybitOrderBookLevel> asks) {
        this.instrumentId = instrumentId;
        this.timestamp = timestamp;
        this.bids = bids == null ? List.of() : List.copyOf(bids);
        this.asks = asks == null ? List.of() : List.copyOf(asks);
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public List<BybitOrderBookLevel> getBids() {
        return Collections.unmodifiableList(bids);
    }

    public List<BybitOrderBookLevel> getAsks() {
        return Collections.unmodifiableList(asks);
    }
}
