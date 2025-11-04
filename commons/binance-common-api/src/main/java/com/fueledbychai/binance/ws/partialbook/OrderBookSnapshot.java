package com.fueledbychai.binance.ws.partialbook;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a Binance WebSocket depth update message. This corresponds to
 * the @depth streams which provide order book updates.
 */
public class OrderBookSnapshot {

    @JsonProperty("stream")
    private String stream;

    @JsonProperty("data")
    private DepthUpdateData data;

    // Default constructor for Jackson
    public OrderBookSnapshot() {
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public DepthUpdateData getData() {
        return data;
    }

    public void setData(DepthUpdateData data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "OrderBookSnapshot{" + "stream='" + stream + '\'' + ", data=" + data + '}';
    }

    /**
     * Represents the depth update data within a WebSocket message.
     */
    public static class DepthUpdateData {

        @JsonProperty("e")
        private String eventType;

        @JsonProperty("E")
        private long eventTime;

        @JsonProperty("T")
        private long transactionTime;

        @JsonProperty("s")
        private String symbol;

        @JsonProperty("U")
        private long firstUpdateId;

        @JsonProperty("u")
        private long finalUpdateId;

        @JsonProperty("pu")
        private long previousUpdateId;

        @JsonProperty("b")
        private List<PriceLevel> bids;

        @JsonProperty("a")
        private List<PriceLevel> asks;

        // Default constructor for Jackson
        public DepthUpdateData() {
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public long getEventTime() {
            return eventTime;
        }

        public void setEventTime(long eventTime) {
            this.eventTime = eventTime;
        }

        public long getTransactionTime() {
            return transactionTime;
        }

        public void setTransactionTime(long transactionTime) {
            this.transactionTime = transactionTime;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public long getFirstUpdateId() {
            return firstUpdateId;
        }

        public void setFirstUpdateId(long firstUpdateId) {
            this.firstUpdateId = firstUpdateId;
        }

        public long getFinalUpdateId() {
            return finalUpdateId;
        }

        public void setFinalUpdateId(long finalUpdateId) {
            this.finalUpdateId = finalUpdateId;
        }

        public long getPreviousUpdateId() {
            return previousUpdateId;
        }

        public void setPreviousUpdateId(long previousUpdateId) {
            this.previousUpdateId = previousUpdateId;
        }

        public List<PriceLevel> getBids() {
            return bids;
        }

        public void setBids(List<PriceLevel> bids) {
            this.bids = bids;
        }

        public List<PriceLevel> getAsks() {
            return asks;
        }

        public void setAsks(List<PriceLevel> asks) {
            this.asks = asks;
        }

        @Override
        public String toString() {
            return "DepthUpdateData{" + "eventType='" + eventType + '\'' + ", eventTime=" + eventTime
                    + ", transactionTime=" + transactionTime + ", symbol='" + symbol + '\'' + ", firstUpdateId="
                    + firstUpdateId + ", finalUpdateId=" + finalUpdateId + ", previousUpdateId=" + previousUpdateId
                    + ", bids=" + bids + ", asks=" + asks + '}';
        }
    }
}
