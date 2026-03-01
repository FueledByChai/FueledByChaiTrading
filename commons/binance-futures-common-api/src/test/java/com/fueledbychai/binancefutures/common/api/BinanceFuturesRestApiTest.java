package com.fueledbychai.binancefutures.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

class BinanceFuturesRestApiTest {

    @Test
    void parsesPerpetualExchangeInfoIntoDescriptors() throws Exception {
        String json = """
                {
                  "symbols": [
                    {
                      "symbol": "BTCUSDT",
                      "status": "TRADING",
                      "contractType": "PERPETUAL",
                      "baseAsset": "BTC",
                      "quoteAsset": "USDT",
                      "filters": [
                        { "filterType": "PRICE_FILTER", "tickSize": "0.10" },
                        { "filterType": "LOT_SIZE", "stepSize": "0.001", "minQty": "0.001" },
                        { "filterType": "MIN_NOTIONAL", "notional": "5.01" }
                      ]
                    },
                    {
                      "symbol": "BTCUSD_260327",
                      "status": "TRADING",
                      "contractType": "CURRENT_QUARTER",
                      "baseAsset": "BTC",
                      "quoteAsset": "USD",
                      "filters": []
                    }
                  ]
                }
                """;

        BinanceFuturesRestApi api = new BinanceFuturesRestApi("https://unit.test") {
            @Override
            protected JsonNode getJson(String path) throws IOException {
                return objectMapper.readTree(json);
            }
        };
        InstrumentDescriptor[] descriptors = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
        assertNotNull(descriptors);
        assertEquals(1, descriptors.length);
        InstrumentDescriptor descriptor = descriptors[0];

        assertEquals("BTC/USDT", descriptor.getCommonSymbol());
        assertEquals("BTCUSDT", descriptor.getExchangeSymbol());
        assertEquals(InstrumentType.PERPETUAL_FUTURES, descriptor.getInstrumentType());
        assertEquals(0, descriptor.getPriceTickSize().compareTo(new java.math.BigDecimal("0.1")));
        assertEquals(0, descriptor.getOrderSizeIncrement().compareTo(new java.math.BigDecimal("0.001")));
        assertEquals(0, descriptor.getMinOrderSize().compareTo(new java.math.BigDecimal("0.001")));
        assertEquals(6, descriptor.getMinNotionalOrderSize());
        assertEquals(BinanceFuturesRestApi.DEFAULT_FUNDING_PERIOD_HOURS, descriptor.getFundingPeriodHours());
    }

    @Test
    void getServerTimeUsesServerTimeField() {
        BinanceFuturesRestApi api = new BinanceFuturesRestApi("https://unit.test") {
            @Override
            protected JsonNode getJson(String path) throws IOException {
                return objectMapper.readTree("{\"serverTime\":1710000000123}");
            }
        };

        assertEquals(new Date(1710000000123L), api.getServerTime());
    }
}
