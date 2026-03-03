package com.fueledbychai.deribit.common.api.ws.model;

import java.util.List;

public class DeribitBookUpdate {

    protected final String instrumentName;
    protected final String updateType;
    protected final Long changeId;
    protected final Long timestamp;
    protected final List<DeribitBookLevel> bids;
    protected final List<DeribitBookLevel> asks;

    public DeribitBookUpdate(String instrumentName, String updateType, Long changeId, Long timestamp,
            List<DeribitBookLevel> bids, List<DeribitBookLevel> asks) {
        this.instrumentName = instrumentName;
        this.updateType = updateType;
        this.changeId = changeId;
        this.timestamp = timestamp;
        this.bids = bids;
        this.asks = asks;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public String getUpdateType() {
        return updateType;
    }

    public Long getChangeId() {
        return changeId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public List<DeribitBookLevel> getBids() {
        return bids;
    }

    public List<DeribitBookLevel> getAsks() {
        return asks;
    }
}
