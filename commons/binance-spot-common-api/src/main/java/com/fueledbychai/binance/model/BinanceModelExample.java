package com.fueledbychai.binance.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;

/**
 * Example class demonstrating how to use the BinanceInstrumentDescriptorResult
 * and related model classes.
 */
public class BinanceModelExample {
    
    public static void main(String[] args) throws Exception {
        
        // Example JSON response (shortened for brevity)
        String jsonResponse = """
            {
              "timezone": "UTC",
              "serverTime": 1762218949949,
              "rateLimits": [
                {
                  "rateLimitType": "REQUEST_WEIGHT",
                  "interval": "MINUTE",
                  "intervalNum": 1,
                  "limit": 6000
                }
              ],
              "exchangeFilters": [],
              "symbols": [
                {
                  "symbol": "BTCUSDC",
                  "status": "TRADING",
                  "baseAsset": "BTC",
                  "baseAssetPrecision": 8,
                  "quoteAsset": "USDC",
                  "quotePrecision": 8,
                  "quoteAssetPrecision": 8,
                  "baseCommissionPrecision": 8,
                  "quoteCommissionPrecision": 8,
                  "orderTypes": ["LIMIT", "MARKET", "STOP_LOSS"],
                  "icebergAllowed": true,
                  "ocoAllowed": true,
                  "otoAllowed": true,
                  "quoteOrderQtyMarketAllowed": true,
                  "allowTrailingStop": true,
                  "cancelReplaceAllowed": true,
                  "amendAllowed": true,
                  "pegInstructionsAllowed": true,
                  "isSpotTradingAllowed": true,
                  "isMarginTradingAllowed": true,
                  "filters": [
                    {
                      "filterType": "PRICE_FILTER",
                      "minPrice": "0.01000000",
                      "maxPrice": "1000000.00000000",
                      "tickSize": "0.01000000"
                    },
                    {
                      "filterType": "LOT_SIZE",
                      "minQty": "0.00001000",
                      "maxQty": "9000.00000000",
                      "stepSize": "0.00001000"
                    },
                    {
                      "filterType": "NOTIONAL",
                      "minNotional": "5.00000000",
                      "applyMinToMarket": true,
                      "maxNotional": "9000000.00000000",
                      "applyMaxToMarket": false,
                      "avgPriceMins": 5
                    }
                  ],
                  "permissions": [],
                  "permissionSets": [["SPOT", "MARGIN"]],
                  "defaultSelfTradePreventionMode": "EXPIRE_MAKER",
                  "allowedSelfTradePreventionModes": ["EXPIRE_TAKER", "EXPIRE_MAKER"]
                }
              ]
            }
            """;
        
        // Parse JSON into model
        ObjectMapper mapper = new ObjectMapper();
        BinanceInstrumentDescriptorResult result = mapper.readValue(jsonResponse, 
                                                                  BinanceInstrumentDescriptorResult.class);
        
        // Basic information
        System.out.println("Exchange Info:");
        System.out.println("  Timezone: " + result.getTimezone());
        System.out.println("  Server Time: " + result.getServerTime());
        System.out.println("  Symbols Count: " + result.getSymbolCount());
        System.out.println("  Rate Limits Count: " + result.getRateLimits().size());
        
        // Find a specific symbol
        BinanceSymbol btcSymbol = result.findSymbol("BTCUSDC");
        if (btcSymbol != null) {
            System.out.println("\\nBTCUSDC Symbol Info:");
            System.out.println("  Status: " + btcSymbol.getStatus());
            System.out.println("  Base Asset: " + btcSymbol.getBaseAsset());
            System.out.println("  Quote Asset: " + btcSymbol.getQuoteAsset());
            System.out.println("  Spot Trading Allowed: " + btcSymbol.isSpotTradingAllowed());
            System.out.println("  Order Types: " + String.join(", ", btcSymbol.getOrderTypes()));
            
            // Use the utility class for easier access
            BinanceSymbolInfo symbolInfo = new BinanceSymbolInfo(btcSymbol);
            
            System.out.println("\\nTrading Constraints:");
            System.out.println("  Min Quantity: " + symbolInfo.getMinOrderQuantity());
            System.out.println("  Max Quantity: " + symbolInfo.getMaxOrderQuantity());
            System.out.println("  Quantity Step: " + symbolInfo.getQuantityStepSize());
            System.out.println("  Min Price: " + symbolInfo.getMinPrice());
            System.out.println("  Max Price: " + symbolInfo.getMaxPrice());
            System.out.println("  Price Tick Size: " + symbolInfo.getPriceTickSize());
            System.out.println("  Min Notional: " + symbolInfo.getMinNotional());
            
            // Validate sample orders
            System.out.println("\\nOrder Validation Examples:");
            
            BigDecimal sampleQuantity = new BigDecimal("0.001");
            BigDecimal samplePrice = new BigDecimal("50000.00");
            
            System.out.println("  Supports LIMIT orders: " + 
                             symbolInfo.supportsOrderType(BinanceOrderType.LIMIT.getValue()));
            System.out.println("  Supports MARKET orders: " + 
                             symbolInfo.supportsOrderType(BinanceOrderType.MARKET.getValue()));
            
            System.out.println("  Quantity " + sampleQuantity + " is valid: " + 
                             symbolInfo.isValidQuantity(sampleQuantity));
            System.out.println("  Price " + samplePrice + " is valid: " + 
                             symbolInfo.isValidPrice(samplePrice));
        }
        
        // Rate limit information
        System.out.println("\\nRate Limits:");
        for (BinanceRateLimit rateLimit : result.getRateLimits()) {
            System.out.println("  " + rateLimit.getRateLimitType() + ": " +
                             rateLimit.getLimit() + " per " + 
                             rateLimit.getIntervalNum() + " " + 
                             rateLimit.getInterval().toLowerCase());
        }
    }
}