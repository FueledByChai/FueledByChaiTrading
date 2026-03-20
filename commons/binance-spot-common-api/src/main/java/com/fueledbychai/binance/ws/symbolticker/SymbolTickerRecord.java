package com.fueledbychai.binance.ws.symbolticker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SymbolTickerRecord {

    @JsonProperty("E")
    private long eventTime;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("c")
    private String lastPrice;

    @JsonProperty("Q")
    private String lastQuantity;

    @JsonProperty("v")
    private String volume;

    @JsonProperty("q")
    private String volumeNotional;

    public SymbolTickerRecord() {
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

    public String getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(String lastPrice) {
        this.lastPrice = lastPrice;
    }

    public String getLastQuantity() {
        return lastQuantity;
    }

    public void setLastQuantity(String lastQuantity) {
        this.lastQuantity = lastQuantity;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getVolumeNotional() {
        return volumeNotional;
    }

    public void setVolumeNotional(String volumeNotional) {
        this.volumeNotional = volumeNotional;
    }
}
