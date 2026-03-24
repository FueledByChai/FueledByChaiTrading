package com.fueledbychai.lighter.common.api.ws.model;

import java.math.BigDecimal;

public class LighterTickerUpdate {

    private final String channel;
    private final Integer marketId;
    private final Long nonce;
    private final Long timestamp;
    private final String symbol;
    private final BigDecimal askPrice;
    private final BigDecimal askSize;
    private final BigDecimal bidPrice;
    private final BigDecimal bidSize;
    private final String messageType;

    public LighterTickerUpdate(String channel, Integer marketId, Long nonce, Long timestamp, String symbol,
            BigDecimal askPrice, BigDecimal askSize, BigDecimal bidPrice, BigDecimal bidSize, String messageType) {
        this.channel = channel;
        this.marketId = marketId;
        this.nonce = nonce;
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.askPrice = askPrice;
        this.askSize = askSize;
        this.bidPrice = bidPrice;
        this.bidSize = bidSize;
        this.messageType = messageType;
    }

    public String getChannel() {
        return channel;
    }

    public Integer getMarketId() {
        return marketId;
    }

    public Long getNonce() {
        return nonce;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getAskPrice() {
        return askPrice;
    }

    public BigDecimal getAskSize() {
        return askSize;
    }

    public BigDecimal getBidPrice() {
        return bidPrice;
    }

    public BigDecimal getBidSize() {
        return bidSize;
    }

    public String getMessageType() {
        return messageType;
    }
}
