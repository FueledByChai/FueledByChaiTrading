package com.fueledbychai.hibachi.common.api;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Represents one Hibachi perpetual future contract as returned by
 * {@code GET /market/exchange-info} on the data-api host.
 *
 * <p>The {@code id}, {@code underlyingDecimals}, and {@code settlementDecimals} fields are
 * required inputs for signing order payloads. See {@link HibachiPayloadPacker} and the
 * Python SDK at {@code hibachi_xyz/api.py} ({@code _create_order_request_data}).
 */
public class HibachiContract {

    private final int id;
    private final String symbol;
    private final String displayName;
    private final String underlyingSymbol;
    private final String settlementSymbol;
    private final int underlyingDecimals;
    private final int settlementDecimals;
    private final BigDecimal tickSize;
    private final BigDecimal stepSize;
    private final BigDecimal minOrderSize;
    private final BigDecimal minNotional;
    private final BigDecimal initialMarginRate;
    private final BigDecimal maintenanceMarginRate;
    private final String status;
    private final List<String> orderbookGranularities;
    private final Long marketOpenTimestamp;
    private final Long marketCloseTimestamp;
    private final Long marketCreationTimestamp;

    private HibachiContract(Builder b) {
        this.id = b.id;
        this.symbol = b.symbol;
        this.displayName = b.displayName;
        this.underlyingSymbol = b.underlyingSymbol;
        this.settlementSymbol = b.settlementSymbol;
        this.underlyingDecimals = b.underlyingDecimals;
        this.settlementDecimals = b.settlementDecimals;
        this.tickSize = b.tickSize;
        this.stepSize = b.stepSize;
        this.minOrderSize = b.minOrderSize;
        this.minNotional = b.minNotional;
        this.initialMarginRate = b.initialMarginRate;
        this.maintenanceMarginRate = b.maintenanceMarginRate;
        this.status = b.status;
        this.orderbookGranularities = b.orderbookGranularities == null
                ? Collections.emptyList()
                : List.copyOf(b.orderbookGranularities);
        this.marketOpenTimestamp = b.marketOpenTimestamp;
        this.marketCloseTimestamp = b.marketCloseTimestamp;
        this.marketCreationTimestamp = b.marketCreationTimestamp;
    }

    public int getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getDisplayName() { return displayName; }
    public String getUnderlyingSymbol() { return underlyingSymbol; }
    public String getSettlementSymbol() { return settlementSymbol; }
    public int getUnderlyingDecimals() { return underlyingDecimals; }
    public int getSettlementDecimals() { return settlementDecimals; }
    public BigDecimal getTickSize() { return tickSize; }
    public BigDecimal getStepSize() { return stepSize; }
    public BigDecimal getMinOrderSize() { return minOrderSize; }
    public BigDecimal getMinNotional() { return minNotional; }
    public BigDecimal getInitialMarginRate() { return initialMarginRate; }
    public BigDecimal getMaintenanceMarginRate() { return maintenanceMarginRate; }
    public String getStatus() { return status; }
    public List<String> getOrderbookGranularities() { return orderbookGranularities; }
    public Long getMarketOpenTimestamp() { return marketOpenTimestamp; }
    public Long getMarketCloseTimestamp() { return marketCloseTimestamp; }
    public Long getMarketCreationTimestamp() { return marketCreationTimestamp; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int id;
        private String symbol;
        private String displayName;
        private String underlyingSymbol;
        private String settlementSymbol;
        private int underlyingDecimals;
        private int settlementDecimals;
        private BigDecimal tickSize;
        private BigDecimal stepSize;
        private BigDecimal minOrderSize;
        private BigDecimal minNotional;
        private BigDecimal initialMarginRate;
        private BigDecimal maintenanceMarginRate;
        private String status;
        private List<String> orderbookGranularities;
        private Long marketOpenTimestamp;
        private Long marketCloseTimestamp;
        private Long marketCreationTimestamp;

        public Builder id(int v) { this.id = v; return this; }
        public Builder symbol(String v) { this.symbol = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder underlyingSymbol(String v) { this.underlyingSymbol = v; return this; }
        public Builder settlementSymbol(String v) { this.settlementSymbol = v; return this; }
        public Builder underlyingDecimals(int v) { this.underlyingDecimals = v; return this; }
        public Builder settlementDecimals(int v) { this.settlementDecimals = v; return this; }
        public Builder tickSize(BigDecimal v) { this.tickSize = v; return this; }
        public Builder stepSize(BigDecimal v) { this.stepSize = v; return this; }
        public Builder minOrderSize(BigDecimal v) { this.minOrderSize = v; return this; }
        public Builder minNotional(BigDecimal v) { this.minNotional = v; return this; }
        public Builder initialMarginRate(BigDecimal v) { this.initialMarginRate = v; return this; }
        public Builder maintenanceMarginRate(BigDecimal v) { this.maintenanceMarginRate = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder orderbookGranularities(List<String> v) { this.orderbookGranularities = v; return this; }
        public Builder marketOpenTimestamp(Long v) { this.marketOpenTimestamp = v; return this; }
        public Builder marketCloseTimestamp(Long v) { this.marketCloseTimestamp = v; return this; }
        public Builder marketCreationTimestamp(Long v) { this.marketCreationTimestamp = v; return this; }

        public HibachiContract build() { return new HibachiContract(this); }
    }
}
