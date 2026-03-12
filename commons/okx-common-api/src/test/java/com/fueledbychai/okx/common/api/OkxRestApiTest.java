package com.fueledbychai.okx.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.okx.common.api.ws.model.OkxFundingRateUpdate;

class OkxRestApiTest {

    @Test
    void mapsSpotPerpFutureAndOptionDescriptors() {
        StubOkxRestApi api = new StubOkxRestApi();

        api.addResponse("public/instruments", mapOf("instType", "SPOT"),
                """
                        {"code":"0","data":[
                          {"instType":"SPOT","instId":"BTC-USDT","baseCcy":"BTC","quoteCcy":"USDT",
                           "tickSz":"0.1","lotSz":"0.0001","minSz":"0.0001"}
                        ]}
                        """);

        api.addResponse("public/instruments", mapOf("instType", "SWAP"),
                """
                        {"code":"0","data":[
                          {"instType":"SWAP","instId":"BTC-USDT-SWAP","uly":"BTC-USDT","settleCcy":"USDT",
                           "tickSz":"0.1","lotSz":"1","minSz":"1","ctVal":"0.01","ctMult":"1"}
                        ]}
                        """);

        api.addResponse("public/instruments", mapOf("instType", "FUTURES"),
                """
                        {"code":"0","data":[
                          {"instType":"FUTURES","instId":"BTC-USDT-260327","uly":"BTC-USDT","settleCcy":"USDT",
                           "expTime":"1774569600000","tickSz":"0.1","lotSz":"1","minSz":"1",
                           "ctVal":"0.01","ctMult":"1"}
                        ]}
                        """);

        api.addResponse("public/underlying", mapOf("instType", "OPTION"),
                """
                        {"code":"0","data":[["BTC-USD"]]}
                        """);

        api.addResponse("public/instruments", mapOf("instType", "OPTION", "uly", "BTC-USD"),
                """
                        {"code":"0","data":[
                          {"instType":"OPTION","instId":"BTC-USD-260327-120000-C","uly":"BTC-USD","settleCcy":"USD",
                           "expTime":"1774569600000","stk":"120000","optType":"C",
                           "tickSz":"0.1","lotSz":"0.1","minSz":"0.1","ctVal":"0.01","ctMult":"1"}
                        ]}
                        """);

        InstrumentDescriptor spot = api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT)[0];
        InstrumentDescriptor perp = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)[0];
        InstrumentDescriptor future = api.getAllInstrumentsForType(InstrumentType.FUTURES)[0];
        InstrumentDescriptor option = api.getAllInstrumentsForType(InstrumentType.OPTION)[0];

        assertEquals("BTC/USDT", spot.getCommonSymbol());
        assertEquals("BTC/USDT-PERP", perp.getCommonSymbol());
        assertEquals("BTC/USDT-20260327", future.getCommonSymbol());
        assertEquals("BTC/USD-20260327-120000-C", option.getCommonSymbol());
        assertEquals(8, perp.getFundingPeriodHours());

        InstrumentDescriptor byExchange = api.getInstrumentDescriptor("BTC-USDT-SWAP");
        InstrumentDescriptor byCommon = api.getInstrumentDescriptor("BTC/USD-20260327-120000-C");
        assertNotNull(byExchange);
        assertNotNull(byCommon);
        assertEquals("BTC-USDT-SWAP", byExchange.getExchangeSymbol());
        assertEquals("BTC-USD-260327-120000-C", byCommon.getExchangeSymbol());
    }

    @Test
    void returnsNullForUnknownSymbol() {
        StubOkxRestApi api = new StubOkxRestApi();
        api.addResponse("public/instruments", mapOf("instType", "SPOT"),
                """
                        {"code":"0","data":[]}
                        """);

        assertNull(api.getInstrumentDescriptor("UNKNOWN-SYMBOL"));
    }

    @Test
    void returnsFundingRateSnapshot() {
        StubOkxRestApi api = new StubOkxRestApi();
        api.addResponse("public/funding-rate", mapOf("instId", "BTC-USDT-SWAP"),
                """
                        {"code":"0","data":[
                          {"instId":"BTC-USDT-SWAP","instType":"SWAP","fundingRate":"0.0008",
                           "nextFundingRate":"0.0010","fundingTime":"1710003600000","nextFundingTime":"1710032400000"}
                        ]}
                        """);

        OkxFundingRateUpdate fundingRate = api.getFundingRate("btc-usdt-swap");

        assertNotNull(fundingRate);
        assertEquals("BTC-USDT-SWAP", fundingRate.getInstrumentId());
        assertEquals("0.0008", fundingRate.getFundingRate().toPlainString());
        assertEquals("0.0010", fundingRate.getNextFundingRate().toPlainString());
        assertEquals(1710003600000L, fundingRate.getTimestamp());
        assertEquals(1710032400000L, fundingRate.getNextFundingTime());
    }

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
        return map;
    }

    private static class StubOkxRestApi extends OkxRestApi {

        private final Map<String, JsonObject> responses = new HashMap<>();

        StubOkxRestApi() {
            super("https://example.test/api/v5");
        }

        void addResponse(String methodPath, Map<String, String> params, String json) {
            responses.put(key(methodPath, params), JsonParser.parseString(json).getAsJsonObject());
        }

        @Override
        protected JsonObject executeGet(String methodPath, Map<String, String> params) {
            JsonObject response = responses.get(key(methodPath, params));
            if (response == null) {
                throw new IllegalStateException("No stubbed response for " + key(methodPath, params));
            }
            return response;
        }

        private String key(String methodPath, Map<String, String> params) {
            StringBuilder builder = new StringBuilder(methodPath);
            if (params == null || params.isEmpty()) {
                return builder.toString();
            }
            builder.append('|');
            params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append(entry.getKey()).append('=').append(entry.getValue()).append('&'));
            return builder.toString();
        }
    }
}
