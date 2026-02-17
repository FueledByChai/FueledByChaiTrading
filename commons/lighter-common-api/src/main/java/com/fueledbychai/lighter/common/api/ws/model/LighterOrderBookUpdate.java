package com.fueledbychai.lighter.common.api.ws.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LighterOrderBookUpdate {

    private final String channel;
    private final Integer marketId;
    private final Integer code;
    private final List<LighterOrderBookLevel> asks;
    private final List<LighterOrderBookLevel> bids;
    private final Long offset;
    private final Long nonce;
    private final Long beginNonce;
    private final Long timestamp;
    private final String messageType;

    public LighterOrderBookUpdate(String channel, Integer marketId, Integer code, List<LighterOrderBookLevel> asks,
            List<LighterOrderBookLevel> bids, Long offset, Long nonce, Long beginNonce, Long timestamp,
            String messageType) {
        this.channel = channel;
        this.marketId = marketId;
        this.code = code;
        this.asks = toUnmodifiableList(asks);
        this.bids = toUnmodifiableList(bids);
        this.offset = offset;
        this.nonce = nonce;
        this.beginNonce = beginNonce;
        this.timestamp = timestamp;
        this.messageType = messageType;
    }

    public String getChannel() {
        return channel;
    }

    public Integer getMarketId() {
        return marketId;
    }

    public Integer getCode() {
        return code;
    }

    public List<LighterOrderBookLevel> getAsks() {
        return asks;
    }

    public List<LighterOrderBookLevel> getBids() {
        return bids;
    }

    public Long getOffset() {
        return offset;
    }

    public Long getNonce() {
        return nonce;
    }

    public Long getBeginNonce() {
        return beginNonce;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public LighterOrderBookLevel getBestAsk() {
        if (asks.isEmpty()) {
            return null;
        }
        return asks.get(0);
    }

    public LighterOrderBookLevel getBestBid() {
        if (bids.isEmpty()) {
            return null;
        }
        return bids.get(0);
    }

    private List<LighterOrderBookLevel> toUnmodifiableList(List<LighterOrderBookLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(levels));
    }
}
