package com.fueledbychai.binance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents the complete Binance exchange information response containing 
 * timezone, server time, rate limits, exchange filters, and symbol information.
 * 
 * This class maps to the JSON response from Binance's /api/v3/exchangeInfo endpoint.
 */
public class BinanceInstrumentDescriptorResult {
    
    @JsonProperty("timezone")
    private String timezone;
    
    @JsonProperty("serverTime")
    private long serverTime;
    
    @JsonProperty("rateLimits")
    private List<BinanceRateLimit> rateLimits;
    
    @JsonProperty("exchangeFilters")
    private List<Object> exchangeFilters;  // Usually empty, using Object for flexibility
    
    @JsonProperty("symbols")
    private List<BinanceSymbol> symbols;
    
    // Default constructor for JSON deserialization
    public BinanceInstrumentDescriptorResult() {}
    
    /**
     * Constructor with all required fields.
     * 
     * @param timezone The timezone used by the exchange (typically "UTC")
     * @param serverTime The current server time in milliseconds
     * @param rateLimits List of rate limit configurations
     * @param exchangeFilters List of exchange-wide filters (usually empty)
     * @param symbols List of all trading symbols and their configurations
     */
    public BinanceInstrumentDescriptorResult(String timezone, long serverTime, 
                                           List<BinanceRateLimit> rateLimits,
                                           List<Object> exchangeFilters,
                                           List<BinanceSymbol> symbols) {
        this.timezone = timezone;
        this.serverTime = serverTime;
        this.rateLimits = rateLimits;
        this.exchangeFilters = exchangeFilters;
        this.symbols = symbols;
    }
    
    // Getters and setters
    
    /**
     * Gets the timezone used by the exchange.
     * 
     * @return The timezone string (typically "UTC")
     */
    public String getTimezone() {
        return timezone;
    }
    
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
    
    /**
     * Gets the current server time in milliseconds since epoch.
     * 
     * @return The server time as a long value
     */
    public long getServerTime() {
        return serverTime;
    }
    
    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
    }
    
    /**
     * Gets the list of rate limit configurations for the exchange.
     * 
     * @return List of BinanceRateLimit objects
     */
    public List<BinanceRateLimit> getRateLimits() {
        return rateLimits;
    }
    
    public void setRateLimits(List<BinanceRateLimit> rateLimits) {
        this.rateLimits = rateLimits;
    }
    
    /**
     * Gets the list of exchange-wide filters (typically empty).
     * 
     * @return List of exchange filters
     */
    public List<Object> getExchangeFilters() {
        return exchangeFilters;
    }
    
    public void setExchangeFilters(List<Object> exchangeFilters) {
        this.exchangeFilters = exchangeFilters;
    }
    
    /**
     * Gets the list of all trading symbols and their configurations.
     * 
     * @return List of BinanceSymbol objects
     */
    public List<BinanceSymbol> getSymbols() {
        return symbols;
    }
    
    public void setSymbols(List<BinanceSymbol> symbols) {
        this.symbols = symbols;
    }
    
    // Utility methods
    
    /**
     * Finds a symbol by its name (e.g., "BTCUSDC").
     * 
     * @param symbolName The symbol name to search for
     * @return The BinanceSymbol if found, null otherwise
     */
    public BinanceSymbol findSymbol(String symbolName) {
        if (symbols == null || symbolName == null) {
            return null;
        }
        
        return symbols.stream()
                .filter(symbol -> symbolName.equals(symbol.getSymbol()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Gets the number of symbols available for trading.
     * 
     * @return The count of symbols
     */
    public int getSymbolCount() {
        return symbols != null ? symbols.size() : 0;
    }
    
    /**
     * Checks if a specific symbol is available for trading.
     * 
     * @param symbolName The symbol name to check
     * @return true if the symbol exists and is in TRADING status, false otherwise
     */
    public boolean isSymbolTradingAllowed(String symbolName) {
        BinanceSymbol symbol = findSymbol(symbolName);
        return symbol != null && "TRADING".equals(symbol.getStatus());
    }
    
    /**
     * Gets all symbols that are currently in TRADING status.
     * 
     * @return List of symbols available for trading
     */
    public List<BinanceSymbol> getTradingSymbols() {
        if (symbols == null) {
            return List.of();
        }
        
        return symbols.stream()
                .filter(symbol -> "TRADING".equals(symbol.getStatus()))
                .toList();
    }
    
    @Override
    public String toString() {
        return "BinanceInstrumentDescriptorResult{" +
                "timezone='" + timezone + '\'' +
                ", serverTime=" + serverTime +
                ", rateLimitsCount=" + (rateLimits != null ? rateLimits.size() : 0) +
                ", symbolsCount=" + getSymbolCount() +
                ", exchangeFiltersCount=" + (exchangeFilters != null ? exchangeFilters.size() : 0) +
                '}';
    }
}