package com.fueledbychai.hibachi.common.api.ws.market;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class HibachiOrderBookUpdate {

    private final String symbol;
    private final List<Level> bids;
    private final List<Level> asks;
    private final long timestamp;
    private final boolean snapshot;

    public HibachiOrderBookUpdate(String symbol, List<Level> bids, List<Level> asks,
                                  long timestamp, boolean snapshot) {
        this.symbol = symbol;
        this.bids = bids == null ? Collections.emptyList() : List.copyOf(bids);
        this.asks = asks == null ? Collections.emptyList() : List.copyOf(asks);
        this.timestamp = timestamp;
        this.snapshot = snapshot;
    }

    public String getSymbol() { return symbol; }
    public List<Level> getBids() { return bids; }
    public List<Level> getAsks() { return asks; }
    public long getTimestamp() { return timestamp; }
    public boolean isSnapshot() { return snapshot; }

    public static class Level {
        public final BigDecimal price;
        public final BigDecimal size;

        public Level(BigDecimal price, BigDecimal size) {
            this.price = price;
            this.size = size;
        }
    }
}
