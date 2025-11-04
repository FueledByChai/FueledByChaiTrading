package com.fueledbychai.binance.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test class for BinanceInstrumentDescriptorResult and related model classes.
 */
public class BinanceInstrumentDescriptorResultTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testJsonDeserialization() throws Exception {
    String json = """
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
              "orderTypes": ["LIMIT", "MARKET"],
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

    BinanceInstrumentDescriptorResult result = objectMapper.readValue(json, BinanceInstrumentDescriptorResult.class);

    // Test basic fields
    assertNotNull(result);
    assertEquals("UTC", result.getTimezone());
    assertEquals(1762218949949L, result.getServerTime());

    // Test rate limits
    assertNotNull(result.getRateLimits());
    assertEquals(1, result.getRateLimits().size());
    BinanceRateLimit rateLimit = result.getRateLimits().get(0);
    assertEquals("REQUEST_WEIGHT", rateLimit.getRateLimitType());
    assertEquals("MINUTE", rateLimit.getInterval());
    assertEquals(1, rateLimit.getIntervalNum());
    assertEquals(6000, rateLimit.getLimit());

    // Test symbols
    assertNotNull(result.getSymbols());
    assertEquals(1, result.getSymbols().size());
    BinanceSymbol symbol = result.getSymbols().get(0);
    assertEquals("BTCUSDC", symbol.getSymbol());
    assertEquals("TRADING", symbol.getStatus());
    assertEquals("BTC", symbol.getBaseAsset());
    assertEquals("USDC", symbol.getQuoteAsset());
    assertTrue(symbol.isSpotTradingAllowed());
    assertTrue(symbol.isMarginTradingAllowed());

    // Test utility methods
    assertEquals(1, result.getSymbolCount());
    assertTrue(result.isSymbolTradingAllowed("BTCUSDC"));
    assertFalse(result.isSymbolTradingAllowed("NONEXISTENT"));
    assertNotNull(result.findSymbol("BTCUSDC"));
    assertNull(result.findSymbol("NONEXISTENT"));

    // Test filters
    assertNotNull(symbol.getFilters());
    assertEquals(2, symbol.getFilters().size());

    // Verify toString methods work
    assertNotNull(result.toString());
    assertNotNull(symbol.toString());
    assertNotNull(rateLimit.toString());
  }

  @Test
  public void testEmptyConstructor() {
    BinanceInstrumentDescriptorResult result = new BinanceInstrumentDescriptorResult();
    assertNull(result.getTimezone());
    assertEquals(0, result.getServerTime());
    assertNull(result.getRateLimits());
    assertNull(result.getExchangeFilters());
    assertNull(result.getSymbols());
    assertEquals(0, result.getSymbolCount());
  }

  @Test
  public void testConstructorWithParameters() {
    BinanceInstrumentDescriptorResult result = new BinanceInstrumentDescriptorResult("UTC", System.currentTimeMillis(),
        List.of(), List.of(), List.of());

    assertEquals("UTC", result.getTimezone());
    assertTrue(result.getServerTime() > 0);
    assertNotNull(result.getRateLimits());
    assertNotNull(result.getExchangeFilters());
    assertNotNull(result.getSymbols());
  }
}