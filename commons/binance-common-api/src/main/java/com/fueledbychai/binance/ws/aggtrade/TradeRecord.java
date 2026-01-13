package com.fueledbychai.binance.ws.aggtrade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeRecord {

    @JsonProperty("E")
    private long eventTime;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("p")
    private String price;

    @JsonProperty("q")
    private String quantity;

    @JsonProperty("T")
    private long tradeTime;

    @JsonProperty("m")
    private boolean isBuyerMarketMaker;

    // Default constructor for Jackson
    public TradeRecord() {
    }

    public long getEventTime() {
        return eventTime;
    }

    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public long getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(long tradeTime) {
        this.tradeTime = tradeTime;
    }

    public boolean isBuyerMarketMaker() {
        return isBuyerMarketMaker;
    }

    public void setBuyerMarketMaker(boolean isBuyerMarketMaker) {
        this.isBuyerMarketMaker = isBuyerMarketMaker;
    }

    @Override
    public String toString() {
        return "TradeRecord [eventTime=" + eventTime + ", symbol=" + symbol + ", price=" + price + ", quantity="
                + quantity + ", tradeTime=" + tradeTime + ", isBuyerMarketMaker=" + isBuyerMarketMaker + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (eventTime ^ (eventTime >>> 32));
        result = prime * result + ((symbol == null) ? 0 : symbol.hashCode());
        result = prime * result + ((price == null) ? 0 : price.hashCode());
        result = prime * result + ((quantity == null) ? 0 : quantity.hashCode());
        result = prime * result + (int) (tradeTime ^ (tradeTime >>> 32));
        result = prime * result + (isBuyerMarketMaker ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TradeRecord other = (TradeRecord) obj;
        if (eventTime != other.eventTime)
            return false;
        if (symbol == null) {
            if (other.symbol != null)
                return false;
        } else if (!symbol.equals(other.symbol))
            return false;
        if (price == null) {
            if (other.price != null)
                return false;
        } else if (!price.equals(other.price))
            return false;
        if (quantity == null) {
            if (other.quantity != null)
                return false;
        } else if (!quantity.equals(other.quantity))
            return false;
        if (tradeTime != other.tradeTime)
            return false;
        if (isBuyerMarketMaker != other.isBuyerMarketMaker)
            return false;
        return true;
    }

}
