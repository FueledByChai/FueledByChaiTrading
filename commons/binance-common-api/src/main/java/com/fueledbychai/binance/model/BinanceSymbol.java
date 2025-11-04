package com.fueledbychai.binance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a Binance trading symbol with all its configuration and restrictions.
 */
public class BinanceSymbol {
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("baseAsset")
    private String baseAsset;
    
    @JsonProperty("baseAssetPrecision")
    private int baseAssetPrecision;
    
    @JsonProperty("quoteAsset")
    private String quoteAsset;
    
    @JsonProperty("quotePrecision")
    private int quotePrecision;
    
    @JsonProperty("quoteAssetPrecision")
    private int quoteAssetPrecision;
    
    @JsonProperty("baseCommissionPrecision")
    private int baseCommissionPrecision;
    
    @JsonProperty("quoteCommissionPrecision")
    private int quoteCommissionPrecision;
    
    @JsonProperty("orderTypes")
    private List<String> orderTypes;
    
    @JsonProperty("icebergAllowed")
    private boolean icebergAllowed;
    
    @JsonProperty("ocoAllowed")
    private boolean ocoAllowed;
    
    @JsonProperty("otoAllowed")
    private boolean otoAllowed;
    
    @JsonProperty("quoteOrderQtyMarketAllowed")
    private boolean quoteOrderQtyMarketAllowed;
    
    @JsonProperty("allowTrailingStop")
    private boolean allowTrailingStop;
    
    @JsonProperty("cancelReplaceAllowed")
    private boolean cancelReplaceAllowed;
    
    @JsonProperty("amendAllowed")
    private boolean amendAllowed;
    
    @JsonProperty("pegInstructionsAllowed")
    private boolean pegInstructionsAllowed;
    
    @JsonProperty("isSpotTradingAllowed")
    private boolean isSpotTradingAllowed;
    
    @JsonProperty("isMarginTradingAllowed")
    private boolean isMarginTradingAllowed;
    
    @JsonProperty("filters")
    private List<BinanceSymbolFilter> filters;
    
    @JsonProperty("permissions")
    private List<String> permissions;
    
    @JsonProperty("permissionSets")
    private List<List<String>> permissionSets;
    
    @JsonProperty("defaultSelfTradePreventionMode")
    private String defaultSelfTradePreventionMode;
    
    @JsonProperty("allowedSelfTradePreventionModes")
    private List<String> allowedSelfTradePreventionModes;
    
    // Default constructor for JSON deserialization
    public BinanceSymbol() {}
    
    // Getters and setters
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getBaseAsset() {
        return baseAsset;
    }
    
    public void setBaseAsset(String baseAsset) {
        this.baseAsset = baseAsset;
    }
    
    public int getBaseAssetPrecision() {
        return baseAssetPrecision;
    }
    
    public void setBaseAssetPrecision(int baseAssetPrecision) {
        this.baseAssetPrecision = baseAssetPrecision;
    }
    
    public String getQuoteAsset() {
        return quoteAsset;
    }
    
    public void setQuoteAsset(String quoteAsset) {
        this.quoteAsset = quoteAsset;
    }
    
    public int getQuotePrecision() {
        return quotePrecision;
    }
    
    public void setQuotePrecision(int quotePrecision) {
        this.quotePrecision = quotePrecision;
    }
    
    public int getQuoteAssetPrecision() {
        return quoteAssetPrecision;
    }
    
    public void setQuoteAssetPrecision(int quoteAssetPrecision) {
        this.quoteAssetPrecision = quoteAssetPrecision;
    }
    
    public int getBaseCommissionPrecision() {
        return baseCommissionPrecision;
    }
    
    public void setBaseCommissionPrecision(int baseCommissionPrecision) {
        this.baseCommissionPrecision = baseCommissionPrecision;
    }
    
    public int getQuoteCommissionPrecision() {
        return quoteCommissionPrecision;
    }
    
    public void setQuoteCommissionPrecision(int quoteCommissionPrecision) {
        this.quoteCommissionPrecision = quoteCommissionPrecision;
    }
    
    public List<String> getOrderTypes() {
        return orderTypes;
    }
    
    public void setOrderTypes(List<String> orderTypes) {
        this.orderTypes = orderTypes;
    }
    
    public boolean isIcebergAllowed() {
        return icebergAllowed;
    }
    
    public void setIcebergAllowed(boolean icebergAllowed) {
        this.icebergAllowed = icebergAllowed;
    }
    
    public boolean isOcoAllowed() {
        return ocoAllowed;
    }
    
    public void setOcoAllowed(boolean ocoAllowed) {
        this.ocoAllowed = ocoAllowed;
    }
    
    public boolean isOtoAllowed() {
        return otoAllowed;
    }
    
    public void setOtoAllowed(boolean otoAllowed) {
        this.otoAllowed = otoAllowed;
    }
    
    public boolean isQuoteOrderQtyMarketAllowed() {
        return quoteOrderQtyMarketAllowed;
    }
    
    public void setQuoteOrderQtyMarketAllowed(boolean quoteOrderQtyMarketAllowed) {
        this.quoteOrderQtyMarketAllowed = quoteOrderQtyMarketAllowed;
    }
    
    public boolean isAllowTrailingStop() {
        return allowTrailingStop;
    }
    
    public void setAllowTrailingStop(boolean allowTrailingStop) {
        this.allowTrailingStop = allowTrailingStop;
    }
    
    public boolean isCancelReplaceAllowed() {
        return cancelReplaceAllowed;
    }
    
    public void setCancelReplaceAllowed(boolean cancelReplaceAllowed) {
        this.cancelReplaceAllowed = cancelReplaceAllowed;
    }
    
    public boolean isAmendAllowed() {
        return amendAllowed;
    }
    
    public void setAmendAllowed(boolean amendAllowed) {
        this.amendAllowed = amendAllowed;
    }
    
    public boolean isPegInstructionsAllowed() {
        return pegInstructionsAllowed;
    }
    
    public void setPegInstructionsAllowed(boolean pegInstructionsAllowed) {
        this.pegInstructionsAllowed = pegInstructionsAllowed;
    }
    
    public boolean isSpotTradingAllowed() {
        return isSpotTradingAllowed;
    }
    
    public void setSpotTradingAllowed(boolean spotTradingAllowed) {
        isSpotTradingAllowed = spotTradingAllowed;
    }
    
    public boolean isMarginTradingAllowed() {
        return isMarginTradingAllowed;
    }
    
    public void setMarginTradingAllowed(boolean marginTradingAllowed) {
        isMarginTradingAllowed = marginTradingAllowed;
    }
    
    public List<BinanceSymbolFilter> getFilters() {
        return filters;
    }
    
    public void setFilters(List<BinanceSymbolFilter> filters) {
        this.filters = filters;
    }
    
    public List<String> getPermissions() {
        return permissions;
    }
    
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
    
    public List<List<String>> getPermissionSets() {
        return permissionSets;
    }
    
    public void setPermissionSets(List<List<String>> permissionSets) {
        this.permissionSets = permissionSets;
    }
    
    public String getDefaultSelfTradePreventionMode() {
        return defaultSelfTradePreventionMode;
    }
    
    public void setDefaultSelfTradePreventionMode(String defaultSelfTradePreventionMode) {
        this.defaultSelfTradePreventionMode = defaultSelfTradePreventionMode;
    }
    
    public List<String> getAllowedSelfTradePreventionModes() {
        return allowedSelfTradePreventionModes;
    }
    
    public void setAllowedSelfTradePreventionModes(List<String> allowedSelfTradePreventionModes) {
        this.allowedSelfTradePreventionModes = allowedSelfTradePreventionModes;
    }
    
    @Override
    public String toString() {
        return "BinanceSymbol{" +
                "symbol='" + symbol + '\'' +
                ", status='" + status + '\'' +
                ", baseAsset='" + baseAsset + '\'' +
                ", quoteAsset='" + quoteAsset + '\'' +
                ", isSpotTradingAllowed=" + isSpotTradingAllowed +
                ", isMarginTradingAllowed=" + isMarginTradingAllowed +
                '}';
    }
}