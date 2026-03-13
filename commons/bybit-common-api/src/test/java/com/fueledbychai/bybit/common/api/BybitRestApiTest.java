package com.fueledbychai.bybit.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.websocket.ProxyConfig;

class BybitRestApiTest {

    @Test
    void mapsSpotPerpFutureAndOptionDescriptors() {
        StubBybitRestApi api = new StubBybitRestApi();

        api.addResponse("market/instruments-info", mapOf("category", "spot"),
                """
                        {"retCode":0,"retMsg":"OK","result":{"list":[
                          {"symbol":"BTCUSDT","baseCoin":"BTC","quoteCoin":"USDT",
                           "priceFilter":{"tickSize":"0.01"},
                           "lotSizeFilter":{"basePrecision":"0.0001","minOrderQty":"0.0001","minNotionalValue":"5"}}
                        ]}}
                        """);

        api.addResponse("market/instruments-info", mapOf("category", "linear", "limit", "1000"),
                """
                        {"retCode":0,"retMsg":"OK","result":{"nextPageCursor":"","list":[
                          {"symbol":"BTCUSDT","contractType":"LinearPerpetual","baseCoin":"BTC",
                           "quoteCoin":"USDT","settleCoin":"USDT",
                           "priceFilter":{"tickSize":"0.1"},
                           "lotSizeFilter":{"qtyStep":"0.001","minOrderQty":"0.001"}},
                          {"symbol":"BTC-27DEC24","contractType":"LinearFutures","baseCoin":"BTC",
                           "quoteCoin":"USDT","settleCoin":"USDT","deliveryTime":"1735257600000",
                           "priceFilter":{"tickSize":"0.5"},
                           "lotSizeFilter":{"qtyStep":"0.001","minOrderQty":"0.001"}}
                        ]}}
                        """);

        api.addResponse("market/instruments-info", mapOf("category", "inverse", "limit", "1000"),
                """
                        {"retCode":0,"retMsg":"OK","result":{"nextPageCursor":"","list":[
                          {"symbol":"BTCUSD","contractType":"InversePerpetual","baseCoin":"BTC",
                           "quoteCoin":"USD","settleCoin":"BTC",
                           "priceFilter":{"tickSize":"0.5"},
                           "lotSizeFilter":{"qtyStep":"1","minOrderQty":"1"}},
                          {"symbol":"BTCUSDZ24","contractType":"InverseFutures","baseCoin":"BTC",
                           "quoteCoin":"USD","settleCoin":"BTC","deliveryTime":"1735257600000",
                           "priceFilter":{"tickSize":"0.5"},
                           "lotSizeFilter":{"qtyStep":"1","minOrderQty":"1"}}
                        ]}}
                        """);

        api.addResponse("market/instruments-info", mapOf("category", "option", "limit", "1000", "baseCoin", "BTC"),
                """
                        {"retCode":0,"retMsg":"OK","result":{"nextPageCursor":"","list":[
                          {"symbol":"BTC-29MAR24-70000-C","baseCoin":"BTC","quoteCoin":"USD",
                           "strike":"70000","optionsType":"Call","deliveryTime":"1711670400000",
                           "priceFilter":{"tickSize":"0.1"},
                           "lotSizeFilter":{"qtyStep":"0.01","minOrderQty":"0.01"}}
                        ]}}
                        """);
        api.addResponse("market/instruments-info", mapOf("category", "option", "limit", "1000", "baseCoin", "ETH"),
                """
                        {"retCode":0,"retMsg":"OK","result":{"nextPageCursor":"","list":[]}}
                        """);

        InstrumentDescriptor spot = api.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT)[0];
        InstrumentDescriptor perp = api.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES)[0];
        InstrumentDescriptor future = api.getAllInstrumentsForType(InstrumentType.FUTURES)[0];
        InstrumentDescriptor option = api.getAllInstrumentsForType(InstrumentType.OPTION)[0];

        assertEquals("BTC/USDT", spot.getCommonSymbol());
        assertEquals("BTC/USDT-PERP", perp.getCommonSymbol());
        assertEquals("BTC/USDT-20241227", future.getCommonSymbol());
        assertEquals("BTC/USD-20240329-70000-C", option.getCommonSymbol());
        assertEquals(8, perp.getFundingPeriodHours());

        InstrumentDescriptor byExchange = api.getInstrumentDescriptor("BTCUSDT");
        InstrumentDescriptor byCommon = api.getInstrumentDescriptor("BTC/USD-20240329-70000-C");
        assertNotNull(byExchange);
        assertNotNull(byCommon);
        assertEquals("BTCUSDT", byExchange.getExchangeSymbol());
        assertEquals("BTC-29MAR24-70000-C", byCommon.getExchangeSymbol());
    }

    @Test
    void loadsEthOptionDescriptorsWhenEthBaseCoinQueryHasData() {
        StubBybitRestApi api = new StubBybitRestApi();

        api.addResponse("market/instruments-info", mapOf("category", "option", "limit", "1000", "baseCoin", "BTC"),
                """
                        {"retCode":0,"retMsg":"OK","result":{"nextPageCursor":"","list":[]}}
                        """);
        api.addResponse("market/instruments-info", mapOf("category", "option", "limit", "1000", "baseCoin", "ETH"),
                """
                        {"retCode":0,"retMsg":"OK","result":{"nextPageCursor":"","list":[
                          {"symbol":"ETH-29MAR24-3500-C","baseCoin":"ETH","quoteCoin":"USD",
                           "strike":"3500","optionsType":"Call","deliveryTime":"1711670400000",
                           "priceFilter":{"tickSize":"0.1"},
                           "lotSizeFilter":{"qtyStep":"0.01","minOrderQty":"0.01"}}
                        ]}}
                        """);

        InstrumentDescriptor[] options = api.getAllInstrumentsForType(InstrumentType.OPTION);
        assertEquals(1, options.length);
        assertEquals("ETH-29MAR24-3500-C", options[0].getExchangeSymbol());
        assertEquals("ETH/USD-20240329-3500-C", options[0].getCommonSymbol());
    }

    @Test
    void resolvesSocksProxyWhenProxyEnabled() {
        try {
            ProxyConfig.getInstance().setSocksProxy("127.0.0.1", 1080);

            StubBybitRestApi api = new StubBybitRestApi();
            Proxy proxy = api.resolveProxy();
            assertNotNull(proxy);
            assertEquals(Proxy.Type.SOCKS, proxy.type());
        } finally {
            ProxyConfig.getInstance().setRunningLocally(false);
        }
    }

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
        return map;
    }

    private static class StubBybitRestApi extends BybitRestApi {

        private final Map<String, JsonObject> responses = new HashMap<>();

        StubBybitRestApi() {
            super("https://example.test/v5");
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
            params.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append(entry.getKey()).append('=').append(entry.getValue()).append('&'));
            return builder.toString();
        }
    }
}
