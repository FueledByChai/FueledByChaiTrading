package com.fueledbychai.aster.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
                """), OBJECT_MAPPER.createObjectNode());

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
    void parsesTradableSpotDescriptorsFromExchangeInfo() throws Exception {
        TestableAsterRestApi api = new TestableAsterRestApi(OBJECT_MAPPER.createObjectNode(), json("""
                {
                  "symbols": [
                    {
                      "symbol": "BNBUSDT",
                      "baseAsset": "BNB",
                      "quoteAsset": "USDT",
                      "status": "TRADING",
                      "filters": [
                        {"filterType":"PRICE_FILTER","tickSize":"0.01000000"},
                        {"filterType":"LOT_SIZE","stepSize":"0.00100000","minQty":"0.01000000"},
                        {"filterType":"NOTIONAL","minNotional":"5"}
                      ]
                    },
                    {
                      "symbol": "XRPUSDT",
                      "baseAsset": "XRP",
                      "quoteAsset": "USDT",
                      "status": "BREAK",
                      "filters": []
                    }
                  ]
                }
                """));

        InstrumentDescriptor[] descriptors = api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT);

        assertEquals(1, descriptors.length);
        InstrumentDescriptor descriptor = descriptors[0];
        assertEquals(InstrumentType.CRYPTO_SPOT, descriptor.getInstrumentType());
        assertEquals("BNB/USDT", descriptor.getCommonSymbol());
        assertEquals("BNBUSDT", descriptor.getExchangeSymbol());
        assertEquals(new BigDecimal("0.001"), descriptor.getOrderSizeIncrement());
        assertEquals(new BigDecimal("0.01"), descriptor.getPriceTickSize());
        assertEquals(new BigDecimal("0.01"), descriptor.getMinOrderSize());
        assertEquals(5, descriptor.getMinNotionalOrderSize());
        assertEquals(0, descriptor.getFundingPeriodHours());
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
                """), json("""
                {
                  "symbols": [
                    {
                      "symbol": "BNBUSDT",
                      "baseAsset": "BNB",
                      "quoteAsset": "USDT",
                      "status": "TRADING",
                      "filters": [
                        {"filterType":"PRICE_FILTER","tickSize":"0.01"},
                        {"filterType":"LOT_SIZE","stepSize":"0.001","minQty":"0.001"}
                      ]
                    }
                  ]
                }
                """));

        assertNotNull(api.getInstrumentDescriptor("BTCUSDT"));
        assertNotNull(api.getInstrumentDescriptor("BTC/USDT"));
        assertNotNull(api.getInstrumentDescriptor("BNBUSDT"));
        assertNotNull(api.getInstrumentDescriptor("BNB/USDT"));
        assertNull(api.getInstrumentDescriptor("ETHUSDT"));
    }

    @Test
    void accountInformationUsesSignedV3AccountEndpoint() throws Exception {
        JsonNode accountInfo = json("""
                {
                  "totalMarginBalance": "150.25",
                  "availableBalance": "90.5"
                }
                """);
        PrivateTestableAsterRestApi api = new PrivateTestableAsterRestApi(accountInfo);

        JsonNode response = api.getAccountInformation();

        assertSame(accountInfo, response);
        assertEquals("GET", api.lastMethod);
        assertEquals("/fapi/v3/account", api.lastPath);
    }

    private static JsonNode json(String value) throws Exception {
        return OBJECT_MAPPER.readTree(value);
    }

    private static final class TestableAsterRestApi extends AsterRestApi {
        private final JsonNode futuresExchangeInfo;
        private final JsonNode spotExchangeInfo;

        private TestableAsterRestApi(JsonNode futuresExchangeInfo, JsonNode spotExchangeInfo) {
            super("https://fapi.unit.test", "https://sapi.unit.test");
            this.futuresExchangeInfo = futuresExchangeInfo;
            this.spotExchangeInfo = spotExchangeInfo;
        }

        @Override
        protected JsonNode publicRequest(String baseUrl, String method, String path, Map<String, String> params) {
            if ("https://fapi.unit.test".equals(baseUrl) && "/fapi/v1/exchangeInfo".equals(path)) {
                return futuresExchangeInfo;
            }
            if ("https://sapi.unit.test".equals(baseUrl) && "/api/v1/exchangeInfo".equals(path)) {
                return spotExchangeInfo;
            }
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private static final class PrivateTestableAsterRestApi extends AsterRestApi {
        private final JsonNode accountInfo;
        private String lastMethod;
        private String lastPath;

        private PrivateTestableAsterRestApi(JsonNode accountInfo) {
            super("https://fapi.unit.test", "https://sapi.unit.test",
                    "0x1111111111111111111111111111111111111111",
                    "0x2222222222222222222222222222222222222222",
                    "0x0000000000000000000000000000000000000000000000000000000000000001");
            this.accountInfo = accountInfo;
        }

        @Override
        protected JsonNode signedRequest(String baseUrl, String method, String path, Map<String, String> params) {
            this.lastMethod = method;
            this.lastPath = path;
            return accountInfo;
        }
    }
}
