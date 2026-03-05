package com.fueledbychai.deribit.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

class DeribitRestApiTest {

    @Test
    void mapsSpotPerpAndOptionDescriptors() {
        StubDeribitRestApi api = new StubDeribitRestApi();
        api.addResponse("public/get_currencies", Collections.emptyMap(),
                """
                        {"jsonrpc":"2.0","result":[{"currency":"BTC"}]}
                        """);
        api.addResponse("public/get_instruments", mapOf("currency", "BTC", "expired", "false", "kind", "spot"),
                """
                        {"jsonrpc":"2.0","result":[
                          {"kind":"spot","instrument_name":"BTC_USDC","instrument_id":1,
                           "base_currency":"BTC","quote_currency":"USDC",
                           "tick_size":"0.01","min_trade_amount":"0.0001","contract_size":"1"}
                        ]}
                        """);
        api.addResponse("public/get_instruments", mapOf("currency", "BTC", "expired", "false", "kind", "future"),
                """
                        {"jsonrpc":"2.0","result":[
                          {"kind":"future","settlement_period":"perpetual","instrument_name":"BTC-PERPETUAL",
                           "instrument_id":2,"base_currency":"BTC","tick_size":"0.5",
                           "min_trade_amount":"10","contract_size":"10"}
                        ]}
                        """);
        api.addResponse("public/get_instruments", mapOf("currency", "BTC", "expired", "false", "kind", "option"),
                """
                        {"jsonrpc":"2.0","result":[
                          {"kind":"option","instrument_name":"BTC-BADDATE-90000-C","instrument_id":3,
                           "base_currency":"BTC","tick_size":"0.0005","min_trade_amount":"0.1","contract_size":"1",
                           "expiration_timestamp":1772582400000}
                        ]}
                        """);

        InstrumentDescriptor spot = api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT)[0];
        InstrumentDescriptor perp = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)[0];
        InstrumentDescriptor option = api.getAllInstrumentsForType(InstrumentType.OPTION)[0];

        assertEquals("BTC/USDC", spot.getCommonSymbol());
        assertEquals("BTC/USD", perp.getCommonSymbol());
        assertEquals("BTC/USD-20260304-90000-C", option.getCommonSymbol());
        assertEquals("BTC-BADDATE-90000-C", option.getExchangeSymbol());
        assertEquals(8, perp.getFundingPeriodHours());
    }

    @Test
    void getInstrumentDescriptorReturnsNullWhenSymbolIsUnknown() {
        StubDeribitRestApi api = new StubDeribitRestApi();
        api.addResponse("public/get_currencies", Collections.emptyMap(),
                """
                        {"jsonrpc":"2.0","result":[{"currency":"BTC"}]}
                        """);
        api.addResponse("public/get_instruments", mapOf("currency", "BTC", "expired", "false", "kind", "future"),
                """
                        {"jsonrpc":"2.0","result":[]}
                        """);

        assertNull(api.getInstrumentDescriptor("BTC-PERPETUAL"));
    }

    @Test
    void getInstrumentDescriptorUsesCachedTypeInference() {
        StubDeribitRestApi api = new StubDeribitRestApi();
        api.addResponse("public/get_currencies", Collections.emptyMap(),
                """
                        {"jsonrpc":"2.0","result":[{"currency":"BTC"}]}
                        """);
        api.addResponse("public/get_instruments", mapOf("currency", "BTC", "expired", "false", "kind", "future"),
                """
                        {"jsonrpc":"2.0","result":[
                          {"kind":"future","settlement_period":"perpetual","instrument_name":"BTC-PERPETUAL",
                           "instrument_id":2,"base_currency":"BTC","tick_size":"0.5",
                           "min_trade_amount":"10","contract_size":"10"}
                        ]}
                        """);

        InstrumentDescriptor descriptor = api.getInstrumentDescriptor("BTC-PERPETUAL");
        assertNotNull(descriptor);
        assertEquals("BTC-PERPETUAL", descriptor.getExchangeSymbol());
    }

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
        return map;
    }

    private static class StubDeribitRestApi extends DeribitRestApi {

        private final Map<String, JsonObject> responses = new HashMap<>();

        StubDeribitRestApi() {
            super("https://example.test/api/v2");
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
