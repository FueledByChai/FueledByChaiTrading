package com.fueledbychai.aster.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

class AsterRestApiTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void parsesTradablePerpetualDescriptorsFromExchangeInfo() throws Exception {
        TestableAsterRestApi api = new TestableAsterRestApi(json("""
                {
                  "symbols": [
                    {
                      "symbol": "BTCUSDT",
                      "baseAsset": "BTC",
                      "quoteAsset": "USDT",
                      "contractType": "PERPETUAL",
                      "status": "TRADING",
                      "filters": [
                        {"filterType":"PRICE_FILTER","tickSize":"0.10"},
                        {"filterType":"LOT_SIZE","stepSize":"0.001","minQty":"0.001"},
                        {"filterType":"MIN_NOTIONAL","notional":"5"}
                      ]
                    },
                    {
                      "symbol": "ETHUSDT",
                      "baseAsset": "ETH",
                      "quoteAsset": "USDT",
                      "contractType": "CURRENT_QUARTER",
                      "status": "TRADING",
                      "filters": []
                    }
                  ]
                }
                """));

        InstrumentDescriptor[] descriptors = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);

        assertEquals(1, descriptors.length);
        InstrumentDescriptor descriptor = descriptors[0];
        assertEquals("BTC/USDT", descriptor.getCommonSymbol());
        assertEquals("BTCUSDT", descriptor.getExchangeSymbol());
        assertEquals(new BigDecimal("0.001"), descriptor.getOrderSizeIncrement());
        assertEquals(new BigDecimal("0.1"), descriptor.getPriceTickSize());
        assertEquals(new BigDecimal("0.001"), descriptor.getMinOrderSize());
        assertEquals(5, descriptor.getMinNotionalOrderSize());
    }

    @Test
    void resolvesInstrumentDescriptorByExchangeAndCommonSymbol() throws Exception {
        TestableAsterRestApi api = new TestableAsterRestApi(json("""
                {
                  "symbols": [
                    {
                      "symbol": "BTCUSDT",
                      "baseAsset": "BTC",
                      "quoteAsset": "USDT",
                      "contractType": "PERPETUAL",
                      "status": "TRADING",
                      "filters": [
                        {"filterType":"PRICE_FILTER","tickSize":"0.10"},
                        {"filterType":"LOT_SIZE","stepSize":"0.001","minQty":"0.001"}
                      ]
                    }
                  ]
                }
                """));

        assertNotNull(api.getInstrumentDescriptor("BTCUSDT"));
        assertNotNull(api.getInstrumentDescriptor("BTC/USDT"));
        assertNull(api.getInstrumentDescriptor("ETHUSDT"));
    }

    private static JsonNode json(String value) throws Exception {
        return OBJECT_MAPPER.readTree(value);
    }

    private static final class TestableAsterRestApi extends AsterRestApi {
        private final JsonNode exchangeInfo;

        private TestableAsterRestApi(JsonNode exchangeInfo) {
            super("https://fapi.unit.test");
            this.exchangeInfo = exchangeInfo;
        }

        @Override
        protected JsonNode publicRequest(String method, String path, Map<String, String> params) {
            if ("/fapi/v1/exchangeInfo".equals(path)) {
                return exchangeInfo;
            }
            return OBJECT_MAPPER.createObjectNode();
        }
    }
}
