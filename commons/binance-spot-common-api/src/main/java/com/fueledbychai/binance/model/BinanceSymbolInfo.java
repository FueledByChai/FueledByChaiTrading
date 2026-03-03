package com.fueledbychai.binance.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Utility class providing convenient access to Binance symbol information and filters.
 * This class wraps a BinanceSymbol and provides type-safe access to specific filter types.
 */
public class BinanceSymbolInfo {
    
    private final BinanceSymbol symbol;
    
    public BinanceSymbolInfo(BinanceSymbol symbol) {
        this.symbol = symbol;
    }
    
    public BinanceSymbol getSymbol() {
        return symbol;
    }
    
    /**
     * Gets the price filter for this symbol.
     * @return Optional containing PriceFilter if found
     */
    public Optional<PriceFilter> getPriceFilter() {
        return getFilter(PriceFilter.class);
    }
    
    /**
     * Gets the lot size filter for this symbol.
     * @return Optional containing LotSizeFilter if found
     */
    public Optional<LotSizeFilter> getLotSizeFilter() {
        return getFilter(LotSizeFilter.class);
    }
    
    /**
     * Gets the notional filter for this symbol.
     * @return Optional containing NotionalFilter if found
     */
    public Optional<NotionalFilter> getNotionalFilter() {
        return getFilter(NotionalFilter.class);
    }
    
    /**
     * Gets the market lot size filter for this symbol.
     * @return Optional containing MarketLotSizeFilter if found
     */
    public Optional<MarketLotSizeFilter> getMarketLotSizeFilter() {
        return getFilter(MarketLotSizeFilter.class);
    }
    
    /**
     * Generic method to get a specific filter type.
     */
    @SuppressWarnings("unchecked")
    private <T extends BinanceSymbolFilter> Optional<T> getFilter(Class<T> filterClass) {
        if (symbol.getFilters() == null) {
            return Optional.empty();
        }
        
        return symbol.getFilters().stream()
                .filter(filterClass::isInstance)
                .map(filter -> (T) filter)
                .findFirst();
    }
    
    /**
     * Gets the minimum order quantity for this symbol.
     * @return BigDecimal representing minimum quantity, or null if not available
     */
    public BigDecimal getMinOrderQuantity() {
        return getLotSizeFilter()
                .map(filter -> new BigDecimal(filter.getMinQty()))
                .orElse(null);
    }
    
    /**
     * Gets the maximum order quantity for this symbol.
     * @return BigDecimal representing maximum quantity, or null if not available
     */
    public BigDecimal getMaxOrderQuantity() {
        return getLotSizeFilter()
                .map(filter -> new BigDecimal(filter.getMaxQty()))
                .orElse(null);
    }
    
    /**
     * Gets the step size for order quantities.
     * @return BigDecimal representing step size, or null if not available
     */
    public BigDecimal getQuantityStepSize() {
        return getLotSizeFilter()
                .map(filter -> new BigDecimal(filter.getStepSize()))
                .orElse(null);
    }
    
    /**
     * Gets the minimum price for orders.
     * @return BigDecimal representing minimum price, or null if not available
     */
    public BigDecimal getMinPrice() {
        return getPriceFilter()
                .map(filter -> new BigDecimal(filter.getMinPrice()))
                .orElse(null);
    }
    
    /**
     * Gets the maximum price for orders.
     * @return BigDecimal representing maximum price, or null if not available
     */
    public BigDecimal getMaxPrice() {
        return getPriceFilter()
                .map(filter -> new BigDecimal(filter.getMaxPrice()))
                .orElse(null);
    }
    
    /**
     * Gets the tick size for prices.
     * @return BigDecimal representing tick size, or null if not available
     */
    public BigDecimal getPriceTickSize() {
        return getPriceFilter()
                .map(filter -> new BigDecimal(filter.getTickSize()))
                .orElse(null);
    }
    
    /**
     * Gets the minimum notional value for orders.
     * @return BigDecimal representing minimum notional, or null if not available
     */
    public BigDecimal getMinNotional() {
        return getNotionalFilter()
                .map(filter -> new BigDecimal(filter.getMinNotional()))
                .orElse(null);
    }
    
    /**
     * Checks if the symbol supports a specific order type.
     * @param orderType The order type to check (e.g., "LIMIT", "MARKET")
     * @return true if the order type is supported
     */
    public boolean supportsOrderType(String orderType) {
        List<String> orderTypes = symbol.getOrderTypes();
        return orderTypes != null && orderTypes.contains(orderType);
    }
    
    /**
     * Validates if a quantity is valid for this symbol.
     * @param quantity The quantity to validate
     * @return true if the quantity meets the symbol's requirements
     */
    public boolean isValidQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        Optional<LotSizeFilter> lotSizeFilter = getLotSizeFilter();
        if (lotSizeFilter.isEmpty()) {
            return true; // No restrictions
        }
        
        LotSizeFilter filter = lotSizeFilter.get();
        BigDecimal minQty = new BigDecimal(filter.getMinQty());
        BigDecimal maxQty = new BigDecimal(filter.getMaxQty());
        BigDecimal stepSize = new BigDecimal(filter.getStepSize());
        
        // Check bounds
        if (quantity.compareTo(minQty) < 0 || quantity.compareTo(maxQty) > 0) {
            return false;
        }
        
        // Check step size alignment
        if (stepSize.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainder = quantity.subtract(minQty).remainder(stepSize);
            return remainder.compareTo(BigDecimal.ZERO) == 0;
        }
        
        return true;
    }
    
    /**
     * Validates if a price is valid for this symbol.
     * @param price The price to validate
     * @return true if the price meets the symbol's requirements
     */
    public boolean isValidPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        Optional<PriceFilter> priceFilter = getPriceFilter();
        if (priceFilter.isEmpty()) {
            return true; // No restrictions
        }
        
        PriceFilter filter = priceFilter.get();
        BigDecimal minPrice = new BigDecimal(filter.getMinPrice());
        BigDecimal maxPrice = new BigDecimal(filter.getMaxPrice());
        BigDecimal tickSize = new BigDecimal(filter.getTickSize());
        
        // Check bounds
        if (price.compareTo(minPrice) < 0 || price.compareTo(maxPrice) > 0) {
            return false;
        }
        
        // Check tick size alignment
        if (tickSize.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainder = price.subtract(minPrice).remainder(tickSize);
            return remainder.compareTo(BigDecimal.ZERO) == 0;
        }
        
        return true;
    }
}