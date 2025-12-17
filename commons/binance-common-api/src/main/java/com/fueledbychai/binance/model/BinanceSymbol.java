package com.fueledbychai.binance.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Binance trading symbol with all its configuration and
 * restrictions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
    public BinanceSymbol() {
    }

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

    // Utility methods for finding specific filters

    /**
     * Find a filter by its filterType string name (e.g., "PRICE_FILTER",
     * "LOT_SIZE").
     * 
     * @param filterTypeName The filterType string to search for
     * @return The matching filter, or null if not found
     */
    public BinanceSymbolFilter getFilterByType(String filterTypeName) {
        if (filters == null) {
            return null;
        }
        return filters.stream().filter(filter -> filterTypeName.equals(filter.getFilterType())).findFirst()
                .orElse(null);
    }

    /**
     * Find a filter by its class type (e.g., PriceFilter.class,
     * LotSizeFilter.class).
     * 
     * @param filterClass The class type to search for
     * @return The matching filter cast to the requested type, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends BinanceSymbolFilter> T getFilterByClass(Class<T> filterClass) {
        if (filters == null) {
            return null;
        }
        return filters.stream().filter(filterClass::isInstance).map(filter -> (T) filter).findFirst().orElse(null);
    }

    /**
     * Check if a specific filter type exists.
     * 
     * @param filterTypeName The filterType string to check for
     * @return true if the filter exists, false otherwise
     */
    public boolean hasFilterType(String filterTypeName) {
        return getFilterByType(filterTypeName) != null;
    }

    /**
     * Check if a specific filter class exists.
     * 
     * @param filterClass The class type to check for
     * @return true if the filter exists, false otherwise
     */
    public boolean hasFilter(Class<? extends BinanceSymbolFilter> filterClass) {
        return getFilterByClass(filterClass) != null;
    }

    // Typed filter access methods with property exposure

    /**
     * Get price filter information.
     * 
     * @return PriceFilterInfo with min/max price and tick size, or null if not
     *         found
     */
    public PriceFilterInfo getPriceFilter() {
        BinanceSymbolFilter filter = getFilterByType("PRICE_FILTER");
        if (filter != null) {
            return new PriceFilterInfo(filter);
        }
        return null;
    }

    /**
     * Get lot size filter information.
     * 
     * @return LotSizeFilterInfo with min/max quantity and step size, or null if not
     *         found
     */
    public LotSizeFilterInfo getLotSizeFilter() {
        BinanceSymbolFilter filter = getFilterByType("LOT_SIZE");
        if (filter != null) {
            return new LotSizeFilterInfo(filter);
        }
        return null;
    }

    /**
     * Get notional filter information.
     * 
     * @return NotionalFilterInfo with notional limits, or null if not found
     */
    public NotionalFilterInfo getNotionalFilter() {
        BinanceSymbolFilter filter = getFilterByType("NOTIONAL");
        if (filter != null) {
            return new NotionalFilterInfo(filter);
        }
        return null;
    }

    /**
     * Get max position filter information.
     * 
     * @return MaxPositionFilterInfo with position limit, or null if not found
     */
    public MaxPositionFilterInfo getMaxPositionFilter() {
        BinanceSymbolFilter filter = getFilterByType("MAX_POSITION");
        if (filter != null) {
            return new MaxPositionFilterInfo(filter);
        }
        return null;
    }

    // Inner classes for filter information exposure

    /**
     * Exposes price filter properties publicly.
     */
    public static class PriceFilterInfo {
        private final String minPrice;
        private final String maxPrice;
        private final String tickSize;

        private PriceFilterInfo(BinanceSymbolFilter filter) {
            // Use reflection to access the properties since we can't cast to PriceFilter
            try {
                java.lang.reflect.Field minPriceField = filter.getClass().getDeclaredField("minPrice");
                java.lang.reflect.Field maxPriceField = filter.getClass().getDeclaredField("maxPrice");
                java.lang.reflect.Field tickSizeField = filter.getClass().getDeclaredField("tickSize");

                minPriceField.setAccessible(true);
                maxPriceField.setAccessible(true);
                tickSizeField.setAccessible(true);

                this.minPrice = (String) minPriceField.get(filter);
                this.maxPrice = (String) maxPriceField.get(filter);
                this.tickSize = (String) tickSizeField.get(filter);
            } catch (Exception e) {
                throw new RuntimeException("Failed to access PriceFilter properties", e);
            }
        }

        public String getMinPrice() {
            return minPrice;
        }

        public String getMaxPrice() {
            return maxPrice;
        }

        public String getTickSize() {
            return tickSize;
        }

        @Override
        public String toString() {
            return "PriceFilterInfo{minPrice='" + minPrice + "', maxPrice='" + maxPrice + "', tickSize='" + tickSize
                    + "'}";
        }
    }

    /**
     * Exposes lot size filter properties publicly.
     */
    public static class LotSizeFilterInfo {
        private final String minQty;
        private final String maxQty;
        private final String stepSize;

        private LotSizeFilterInfo(BinanceSymbolFilter filter) {
            try {
                java.lang.reflect.Field minQtyField = filter.getClass().getDeclaredField("minQty");
                java.lang.reflect.Field maxQtyField = filter.getClass().getDeclaredField("maxQty");
                java.lang.reflect.Field stepSizeField = filter.getClass().getDeclaredField("stepSize");

                minQtyField.setAccessible(true);
                maxQtyField.setAccessible(true);
                stepSizeField.setAccessible(true);

                this.minQty = (String) minQtyField.get(filter);
                this.maxQty = (String) maxQtyField.get(filter);
                this.stepSize = (String) stepSizeField.get(filter);
            } catch (Exception e) {
                throw new RuntimeException("Failed to access LotSizeFilter properties", e);
            }
        }

        public String getMinQty() {
            return minQty;
        }

        public String getMaxQty() {
            return maxQty;
        }

        public String getStepSize() {
            return stepSize;
        }

        @Override
        public String toString() {
            return "LotSizeFilterInfo{minQty='" + minQty + "', maxQty='" + maxQty + "', stepSize='" + stepSize + "'}";
        }
    }

    /**
     * Exposes notional filter properties publicly.
     */
    public static class NotionalFilterInfo {
        private final String minNotional;
        private final String maxNotional;
        private final boolean applyMinToMarket;
        private final boolean applyMaxToMarket;
        private final int avgPriceMins;

        private NotionalFilterInfo(BinanceSymbolFilter filter) {
            try {
                this.minNotional = (String) getField(filter, "minNotional");
                this.maxNotional = (String) getField(filter, "maxNotional");
                this.applyMinToMarket = (Boolean) getField(filter, "applyMinToMarket");
                this.applyMaxToMarket = (Boolean) getField(filter, "applyMaxToMarket");
                this.avgPriceMins = (Integer) getField(filter, "avgPriceMins");
            } catch (Exception e) {
                throw new RuntimeException("Failed to access NotionalFilter properties", e);
            }
        }

        public String getMinNotional() {
            return minNotional;
        }

        public String getMaxNotional() {
            return maxNotional;
        }

        public boolean isApplyMinToMarket() {
            return applyMinToMarket;
        }

        public boolean isApplyMaxToMarket() {
            return applyMaxToMarket;
        }

        public int getAvgPriceMins() {
            return avgPriceMins;
        }

        @Override
        public String toString() {
            return "NotionalFilterInfo{minNotional='" + minNotional + "', maxNotional='" + maxNotional
                    + "', applyMinToMarket=" + applyMinToMarket + ", applyMaxToMarket=" + applyMaxToMarket
                    + ", avgPriceMins=" + avgPriceMins + '}';
        }
    }

    /**
     * Exposes max position filter properties publicly.
     */
    public static class MaxPositionFilterInfo {
        private final String maxPosition;

        private MaxPositionFilterInfo(BinanceSymbolFilter filter) {
            try {
                this.maxPosition = (String) getField(filter, "maxPosition");
            } catch (Exception e) {
                throw new RuntimeException("Failed to access MaxPositionFilter properties", e);
            }
        }

        public String getMaxPosition() {
            return maxPosition;
        }

        @Override
        public String toString() {
            return "MaxPositionFilterInfo{maxPosition='" + maxPosition + "'}";
        }
    }

    // Helper method for reflection access
    private static Object getField(BinanceSymbolFilter filter, String fieldName) throws Exception {
        java.lang.reflect.Field field = filter.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(filter);
    }

    @Override
    public String toString() {
        return "BinanceSymbol [symbol=" + symbol + ", status=" + status + ", baseAsset=" + baseAsset
                + ", baseAssetPrecision=" + baseAssetPrecision + ", quoteAsset=" + quoteAsset + ", quotePrecision="
                + quotePrecision + ", quoteAssetPrecision=" + quoteAssetPrecision + ", baseCommissionPrecision="
                + baseCommissionPrecision + ", quoteCommissionPrecision=" + quoteCommissionPrecision + ", orderTypes="
                + orderTypes + ", icebergAllowed=" + icebergAllowed + ", ocoAllowed=" + ocoAllowed + ", otoAllowed="
                + otoAllowed + ", quoteOrderQtyMarketAllowed=" + quoteOrderQtyMarketAllowed + ", allowTrailingStop="
                + allowTrailingStop + ", cancelReplaceAllowed=" + cancelReplaceAllowed + ", amendAllowed="
                + amendAllowed + ", pegInstructionsAllowed=" + pegInstructionsAllowed + ", isSpotTradingAllowed="
                + isSpotTradingAllowed + ", isMarginTradingAllowed=" + isMarginTradingAllowed + ", filters=" + filters
                + ", permissions=" + permissions + ", permissionSets=" + permissionSets
                + ", defaultSelfTradePreventionMode=" + defaultSelfTradePreventionMode
                + ", allowedSelfTradePreventionModes=" + allowedSelfTradePreventionModes + "]";
    }

}