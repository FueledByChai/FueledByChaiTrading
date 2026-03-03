package com.fueledbychai.binancefutures.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.math.BigDecimal;
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

    @Test
    void parsesOptionExchangeInfoIntoDescriptors() throws Exception {
        String json = """
                {
                  "optionSymbols": [
                    {
                      "symbol": "BTC-260627-50000-C",
                      "status": "TRADING",
                      "underlying": "BTC",
                      "quoteAsset": "USDT",
                      "unit": "0.1",
                      "minQty": "0.01",
                      "filters": [
                        { "filterType": "PRICE_FILTER", "tickSize": "0.0001" },
                        { "filterType": "LOT_SIZE", "stepSize": "0.01", "minQty": "0.01" }
                      ]
                    },
                    {
                      "symbol": "BTC-250101-50000-C",
                      "status": "END_OF_LIFE",
                      "underlying": "BTC",
                      "quoteAsset": "USDT",
                      "filters": []
                    }
                  ]
                }
                """;

        BinanceFuturesRestApi api = new BinanceFuturesRestApi("https://unit.test") {
            @Override
            protected JsonNode getJson(InstrumentType instrumentType, String path) throws IOException {
                assertEquals(InstrumentType.OPTION, instrumentType);
                assertEquals("/eapi/v1/exchangeInfo", path);
                return objectMapper.readTree(json);
            }
        };

        InstrumentDescriptor[] descriptors = api.getAllInstrumentsForType(InstrumentType.OPTION);
        assertNotNull(descriptors);
        assertEquals(1, descriptors.length);

        InstrumentDescriptor descriptor = descriptors[0];
        assertEquals("BTC-260627-50000-C", descriptor.getCommonSymbol());
        assertEquals("BTC-260627-50000-C", descriptor.getExchangeSymbol());
        assertEquals(InstrumentType.OPTION, descriptor.getInstrumentType());
        assertEquals("BTC", descriptor.getBaseCurrency());
        assertEquals("USDT", descriptor.getQuoteCurrency());
        assertEquals(0, descriptor.getPriceTickSize().compareTo(new BigDecimal("0.0001")));
        assertEquals(0, descriptor.getOrderSizeIncrement().compareTo(new BigDecimal("0.01")));
        assertEquals(0, descriptor.getMinOrderSize().compareTo(new BigDecimal("0.01")));
        assertEquals(0, descriptor.getContractMultiplier().compareTo(new BigDecimal("0.1")));
        assertEquals(BinanceFuturesRestApi.NO_FUNDING_PERIOD_HOURS, descriptor.getFundingPeriodHours());
    }

    @Test
    void parsesOptionExchangeInfoNormalizesPairStyleUnderlying() throws Exception {
        String json = """
                {
                  "optionSymbols": [
                    {
                      "symbol": "BTC-260627-50000-C",
                      "status": "TRADING",
                      "underlying": "BTCUSDT",
                      "quoteAsset": "USDT",
                      "filters": []
                    }
                  ]
                }
                """;

        BinanceFuturesRestApi api = new BinanceFuturesRestApi("https://unit.test") {
            @Override
            protected JsonNode getJson(InstrumentType instrumentType, String path) throws IOException {
                return objectMapper.readTree(json);
            }
        };

        InstrumentDescriptor[] descriptors = api.getAllInstrumentsForType(InstrumentType.OPTION);
        assertNotNull(descriptors);
        assertEquals(1, descriptors.length);
        assertEquals("BTC", descriptors[0].getBaseCurrency());
    }
}
