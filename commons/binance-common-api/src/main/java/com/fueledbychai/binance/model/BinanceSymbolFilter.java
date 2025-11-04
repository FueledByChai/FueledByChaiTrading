package com.fueledbychai.binance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for Binance symbol filters. Uses Jackson polymorphic
 * deserialization based on filterType with fallback for unknown types.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "filterType", visible = true, defaultImpl = UnknownFilter.class)
@JsonSubTypes({ @JsonSubTypes.Type(value = PriceFilter.class, name = "PRICE_FILTER"),
        @JsonSubTypes.Type(value = LotSizeFilter.class, name = "LOT_SIZE"),
        @JsonSubTypes.Type(value = IcebergPartsFilter.class, name = "ICEBERG_PARTS"),
        @JsonSubTypes.Type(value = MarketLotSizeFilter.class, name = "MARKET_LOT_SIZE"),
        @JsonSubTypes.Type(value = TrailingDeltaFilter.class, name = "TRAILING_DELTA"),
        @JsonSubTypes.Type(value = PercentPriceBySideFilter.class, name = "PERCENT_PRICE_BY_SIDE"),
        @JsonSubTypes.Type(value = NotionalFilter.class, name = "NOTIONAL"),
        @JsonSubTypes.Type(value = MaxNumOrdersFilter.class, name = "MAX_NUM_ORDERS"),
        @JsonSubTypes.Type(value = MaxNumOrderListsFilter.class, name = "MAX_NUM_ORDER_LISTS"),
        @JsonSubTypes.Type(value = MaxNumAlgoOrdersFilter.class, name = "MAX_NUM_ALGO_ORDERS"),
        @JsonSubTypes.Type(value = MaxNumOrderAmendsFilter.class, name = "MAX_NUM_ORDER_AMENDS"),
        @JsonSubTypes.Type(value = MaxPositionFilter.class, name = "MAX_POSITION") })
public abstract class BinanceSymbolFilter {

    @JsonProperty("filterType")
    private String filterType;

    public String getFilterType() {
        return filterType;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }

    /**
     * Public constants for filter class types to use with getFilterByClass(). Since
     * the actual filter classes are package-private, use these constants.
     */
    public static class FilterTypes {
        public static final Class<? extends BinanceSymbolFilter> PRICE_FILTER = PriceFilter.class;
        public static final Class<? extends BinanceSymbolFilter> LOT_SIZE_FILTER = LotSizeFilter.class;
        public static final Class<? extends BinanceSymbolFilter> ICEBERG_PARTS_FILTER = IcebergPartsFilter.class;
        public static final Class<? extends BinanceSymbolFilter> MARKET_LOT_SIZE_FILTER = MarketLotSizeFilter.class;
        public static final Class<? extends BinanceSymbolFilter> TRAILING_DELTA_FILTER = TrailingDeltaFilter.class;
        public static final Class<? extends BinanceSymbolFilter> PERCENT_PRICE_BY_SIDE_FILTER = PercentPriceBySideFilter.class;
        public static final Class<? extends BinanceSymbolFilter> NOTIONAL_FILTER = NotionalFilter.class;
        public static final Class<? extends BinanceSymbolFilter> MAX_NUM_ORDERS_FILTER = MaxNumOrdersFilter.class;
        public static final Class<? extends BinanceSymbolFilter> MAX_NUM_ORDER_LISTS_FILTER = MaxNumOrderListsFilter.class;
        public static final Class<? extends BinanceSymbolFilter> MAX_NUM_ALGO_ORDERS_FILTER = MaxNumAlgoOrdersFilter.class;
        public static final Class<? extends BinanceSymbolFilter> MAX_NUM_ORDER_AMENDS_FILTER = MaxNumOrderAmendsFilter.class;
        public static final Class<? extends BinanceSymbolFilter> MAX_POSITION_FILTER = MaxPositionFilter.class;
        public static final Class<? extends BinanceSymbolFilter> UNKNOWN_FILTER = UnknownFilter.class;
    }

    /**
     * String constants for filter type names to use with getFilterByType().
     */
    public static class FilterTypeNames {
        public static final String PRICE_FILTER = "PRICE_FILTER";
        public static final String LOT_SIZE = "LOT_SIZE";
        public static final String ICEBERG_PARTS = "ICEBERG_PARTS";
        public static final String MARKET_LOT_SIZE = "MARKET_LOT_SIZE";
        public static final String TRAILING_DELTA = "TRAILING_DELTA";
        public static final String PERCENT_PRICE_BY_SIDE = "PERCENT_PRICE_BY_SIDE";
        public static final String NOTIONAL = "NOTIONAL";
        public static final String MAX_NUM_ORDERS = "MAX_NUM_ORDERS";
        public static final String MAX_NUM_ORDER_LISTS = "MAX_NUM_ORDER_LISTS";
        public static final String MAX_NUM_ALGO_ORDERS = "MAX_NUM_ALGO_ORDERS";
        public static final String MAX_NUM_ORDER_AMENDS = "MAX_NUM_ORDER_AMENDS";
        public static final String MAX_POSITION = "MAX_POSITION";
    }
}

/**
 * Price filter implementation.
 */
class PriceFilter extends BinanceSymbolFilter {
    @JsonProperty("minPrice")
    private String minPrice;

    @JsonProperty("maxPrice")
    private String maxPrice;

    @JsonProperty("tickSize")
    private String tickSize;

    public String getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(String minPrice) {
        this.minPrice = minPrice;
    }

    public String getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(String maxPrice) {
        this.maxPrice = maxPrice;
    }

    public String getTickSize() {
        return tickSize;
    }

    public void setTickSize(String tickSize) {
        this.tickSize = tickSize;
    }

    @Override
    public String toString() {
        return "PriceFilter{" + "filterType='" + getFilterType() + '\'' + ", minPrice='" + minPrice + '\''
                + ", maxPrice='" + maxPrice + '\'' + ", tickSize='" + tickSize + '\'' + '}';
    }
}

/**
 * Lot size filter implementation.
 */
class LotSizeFilter extends BinanceSymbolFilter {
    @JsonProperty("minQty")
    private String minQty;

    @JsonProperty("maxQty")
    private String maxQty;

    @JsonProperty("stepSize")
    private String stepSize;

    public String getMinQty() {
        return minQty;
    }

    public void setMinQty(String minQty) {
        this.minQty = minQty;
    }

    public String getMaxQty() {
        return maxQty;
    }

    public void setMaxQty(String maxQty) {
        this.maxQty = maxQty;
    }

    public String getStepSize() {
        return stepSize;
    }

    public void setStepSize(String stepSize) {
        this.stepSize = stepSize;
    }

    @Override
    public String toString() {
        return "LotSizeFilter{" + "filterType='" + getFilterType() + '\'' + ", minQty='" + minQty + '\'' + ", maxQty='"
                + maxQty + '\'' + ", stepSize='" + stepSize + '\'' + '}';
    }
}

/**
 * Iceberg parts filter implementation.
 */
class IcebergPartsFilter extends BinanceSymbolFilter {
    @JsonProperty("limit")
    private int limit;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    @Override
    public String toString() {
        return "IcebergPartsFilter{" + "filterType='" + getFilterType() + '\'' + ", limit=" + limit + '}';
    }
}

/**
 * Market lot size filter implementation.
 */
class MarketLotSizeFilter extends BinanceSymbolFilter {
    @JsonProperty("minQty")
    private String minQty;

    @JsonProperty("maxQty")
    private String maxQty;

    @JsonProperty("stepSize")
    private String stepSize;

    public String getMinQty() {
        return minQty;
    }

    public void setMinQty(String minQty) {
        this.minQty = minQty;
    }

    public String getMaxQty() {
        return maxQty;
    }

    public void setMaxQty(String maxQty) {
        this.maxQty = maxQty;
    }

    public String getStepSize() {
        return stepSize;
    }

    public void setStepSize(String stepSize) {
        this.stepSize = stepSize;
    }

    @Override
    public String toString() {
        return "MarketLotSizeFilter{" + "filterType='" + getFilterType() + '\'' + ", minQty='" + minQty + '\''
                + ", maxQty='" + maxQty + '\'' + ", stepSize='" + stepSize + '\'' + '}';
    }
}

/**
 * Trailing delta filter implementation.
 */
class TrailingDeltaFilter extends BinanceSymbolFilter {
    @JsonProperty("minTrailingAboveDelta")
    private int minTrailingAboveDelta;

    @JsonProperty("maxTrailingAboveDelta")
    private int maxTrailingAboveDelta;

    @JsonProperty("minTrailingBelowDelta")
    private int minTrailingBelowDelta;

    @JsonProperty("maxTrailingBelowDelta")
    private int maxTrailingBelowDelta;

    public int getMinTrailingAboveDelta() {
        return minTrailingAboveDelta;
    }

    public void setMinTrailingAboveDelta(int minTrailingAboveDelta) {
        this.minTrailingAboveDelta = minTrailingAboveDelta;
    }

    public int getMaxTrailingAboveDelta() {
        return maxTrailingAboveDelta;
    }

    public void setMaxTrailingAboveDelta(int maxTrailingAboveDelta) {
        this.maxTrailingAboveDelta = maxTrailingAboveDelta;
    }

    public int getMinTrailingBelowDelta() {
        return minTrailingBelowDelta;
    }

    public void setMinTrailingBelowDelta(int minTrailingBelowDelta) {
        this.minTrailingBelowDelta = minTrailingBelowDelta;
    }

    public int getMaxTrailingBelowDelta() {
        return maxTrailingBelowDelta;
    }

    public void setMaxTrailingBelowDelta(int maxTrailingBelowDelta) {
        this.maxTrailingBelowDelta = maxTrailingBelowDelta;
    }

    @Override
    public String toString() {
        return "TrailingDeltaFilter{" + "filterType='" + getFilterType() + '\'' + ", minTrailingAboveDelta="
                + minTrailingAboveDelta + ", maxTrailingAboveDelta=" + maxTrailingAboveDelta
                + ", minTrailingBelowDelta=" + minTrailingBelowDelta + ", maxTrailingBelowDelta="
                + maxTrailingBelowDelta + '}';
    }
}

/**
 * Percent price by side filter implementation.
 */
class PercentPriceBySideFilter extends BinanceSymbolFilter {
    @JsonProperty("bidMultiplierUp")
    private String bidMultiplierUp;

    @JsonProperty("bidMultiplierDown")
    private String bidMultiplierDown;

    @JsonProperty("askMultiplierUp")
    private String askMultiplierUp;

    @JsonProperty("askMultiplierDown")
    private String askMultiplierDown;

    @JsonProperty("avgPriceMins")
    private int avgPriceMins;

    public String getBidMultiplierUp() {
        return bidMultiplierUp;
    }

    public void setBidMultiplierUp(String bidMultiplierUp) {
        this.bidMultiplierUp = bidMultiplierUp;
    }

    public String getBidMultiplierDown() {
        return bidMultiplierDown;
    }

    public void setBidMultiplierDown(String bidMultiplierDown) {
        this.bidMultiplierDown = bidMultiplierDown;
    }

    public String getAskMultiplierUp() {
        return askMultiplierUp;
    }

    public void setAskMultiplierUp(String askMultiplierUp) {
        this.askMultiplierUp = askMultiplierUp;
    }

    public String getAskMultiplierDown() {
        return askMultiplierDown;
    }

    public void setAskMultiplierDown(String askMultiplierDown) {
        this.askMultiplierDown = askMultiplierDown;
    }

    public int getAvgPriceMins() {
        return avgPriceMins;
    }

    public void setAvgPriceMins(int avgPriceMins) {
        this.avgPriceMins = avgPriceMins;
    }

    @Override
    public String toString() {
        return "PercentPriceBySideFilter{" + "filterType='" + getFilterType() + '\'' + ", bidMultiplierUp='"
                + bidMultiplierUp + '\'' + ", bidMultiplierDown='" + bidMultiplierDown + '\'' + ", askMultiplierUp='"
                + askMultiplierUp + '\'' + ", askMultiplierDown='" + askMultiplierDown + '\'' + ", avgPriceMins="
                + avgPriceMins + '}';
    }
}

/**
 * Notional filter implementation.
 */
class NotionalFilter extends BinanceSymbolFilter {
    @JsonProperty("minNotional")
    private String minNotional;

    @JsonProperty("applyMinToMarket")
    private boolean applyMinToMarket;

    @JsonProperty("maxNotional")
    private String maxNotional;

    @JsonProperty("applyMaxToMarket")
    private boolean applyMaxToMarket;

    @JsonProperty("avgPriceMins")
    private int avgPriceMins;

    public String getMinNotional() {
        return minNotional;
    }

    public void setMinNotional(String minNotional) {
        this.minNotional = minNotional;
    }

    public boolean isApplyMinToMarket() {
        return applyMinToMarket;
    }

    public void setApplyMinToMarket(boolean applyMinToMarket) {
        this.applyMinToMarket = applyMinToMarket;
    }

    public String getMaxNotional() {
        return maxNotional;
    }

    public void setMaxNotional(String maxNotional) {
        this.maxNotional = maxNotional;
    }

    public boolean isApplyMaxToMarket() {
        return applyMaxToMarket;
    }

    public void setApplyMaxToMarket(boolean applyMaxToMarket) {
        this.applyMaxToMarket = applyMaxToMarket;
    }

    public int getAvgPriceMins() {
        return avgPriceMins;
    }

    public void setAvgPriceMins(int avgPriceMins) {
        this.avgPriceMins = avgPriceMins;
    }

    @Override
    public String toString() {
        return "NotionalFilter{" + "filterType='" + getFilterType() + '\'' + ", minNotional='" + minNotional + '\''
                + ", applyMinToMarket=" + applyMinToMarket + ", maxNotional='" + maxNotional + '\''
                + ", applyMaxToMarket=" + applyMaxToMarket + ", avgPriceMins=" + avgPriceMins + '}';
    }
}

/**
 * Max number of orders filter implementation.
 */
class MaxNumOrdersFilter extends BinanceSymbolFilter {
    @JsonProperty("maxNumOrders")
    private int maxNumOrders;

    public int getMaxNumOrders() {
        return maxNumOrders;
    }

    public void setMaxNumOrders(int maxNumOrders) {
        this.maxNumOrders = maxNumOrders;
    }

    @Override
    public String toString() {
        return "MaxNumOrdersFilter{" + "filterType='" + getFilterType() + '\'' + ", maxNumOrders=" + maxNumOrders + '}';
    }
}

/**
 * Max number of order lists filter implementation.
 */
class MaxNumOrderListsFilter extends BinanceSymbolFilter {
    @JsonProperty("maxNumOrderLists")
    private int maxNumOrderLists;

    public int getMaxNumOrderLists() {
        return maxNumOrderLists;
    }

    public void setMaxNumOrderLists(int maxNumOrderLists) {
        this.maxNumOrderLists = maxNumOrderLists;
    }

    @Override
    public String toString() {
        return "MaxNumOrderListsFilter{" + "filterType='" + getFilterType() + '\'' + ", maxNumOrderLists="
                + maxNumOrderLists + '}';
    }
}

/**
 * Max number of algo orders filter implementation.
 */
class MaxNumAlgoOrdersFilter extends BinanceSymbolFilter {
    @JsonProperty("maxNumAlgoOrders")
    private int maxNumAlgoOrders;

    public int getMaxNumAlgoOrders() {
        return maxNumAlgoOrders;
    }

    public void setMaxNumAlgoOrders(int maxNumAlgoOrders) {
        this.maxNumAlgoOrders = maxNumAlgoOrders;
    }

    @Override
    public String toString() {
        return "MaxNumAlgoOrdersFilter{" + "filterType='" + getFilterType() + '\'' + ", maxNumAlgoOrders="
                + maxNumAlgoOrders + '}';
    }
}

/**
 * Max number of order amends filter implementation.
 */
class MaxNumOrderAmendsFilter extends BinanceSymbolFilter {
    @JsonProperty("maxNumOrderAmends")
    private int maxNumOrderAmends;

    public int getMaxNumOrderAmends() {
        return maxNumOrderAmends;
    }

    public void setMaxNumOrderAmends(int maxNumOrderAmends) {
        this.maxNumOrderAmends = maxNumOrderAmends;
    }

    @Override
    public String toString() {
        return "MaxNumOrderAmendsFilter{" + "filterType='" + getFilterType() + '\'' + ", maxNumOrderAmends="
                + maxNumOrderAmends + '}';
    }
}

class MaxPositionFilter extends BinanceSymbolFilter {
    @JsonProperty("maxPosition")
    private String maxPosition;

    public String getMaxPosition() {
        return maxPosition;
    }

    public void setMaxPosition(String maxPosition) {
        this.maxPosition = maxPosition;
    }

    @Override
    public String toString() {
        return "MaxPositionFilter{" + "filterType='" + getFilterType() + '\'' + ", maxPosition='" + maxPosition + '\''
                + '}';
    }
}

class UnknownFilter extends BinanceSymbolFilter {
    // This class handles any unknown filter types that aren't explicitly defined
    // It stores any unknown properties in a flexible way without failing
    // deserialization

    // Default constructor for Jackson
    public UnknownFilter() {
        super();
    }

    @Override
    public String toString() {
        return "UnknownFilter{" + "filterType='" + getFilterType() + '\'' + '}';
    }
}