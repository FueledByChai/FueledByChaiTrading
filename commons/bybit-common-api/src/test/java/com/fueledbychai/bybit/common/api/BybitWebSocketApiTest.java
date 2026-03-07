package com.fueledbychai.bybit.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.fueledbychai.bybit.common.api.ws.model.BybitTickerUpdate;
import com.fueledbychai.data.InstrumentType;

class BybitWebSocketApiTest {

    @Test
    void resolveCategoryTreatsOptionLikeSymbolsAsOptionWhenInstrumentTypeIsMissing() {
        TestableBybitWebSocketApi api = new TestableBybitWebSocketApi();

        assertEquals(BybitWsCategory.OPTION, api.resolveCategoryForTest("BTC-7MAR26-60000-C-USDT", null));
        assertEquals(BybitWsCategory.OPTION, api.resolveCategoryForTest("ETH-27DEC26-3500-P", InstrumentType.FUTURES));
    }

    @Test
    void parseTickerUpdateReadsAlternateBestBidAskFieldNames() {
        TestableBybitWebSocketApi api = new TestableBybitWebSocketApi();
        JsonObject data = new JsonObject();
        data.addProperty("bidPrice", "101.5");
        data.addProperty("bidSize", "3");
        data.addProperty("askPrice", "102.0");
        data.addProperty("askSize", "4");
        data.addProperty("lastPrice", "101.8");
        data.addProperty("lastSize", "2");
        data.addProperty("markPrice", "101.9");
        data.addProperty("bidIv", "0.25");
        data.addProperty("askIv", "0.28");
        data.addProperty("markIv", "0.27");

        BybitTickerUpdate update = api.parseTickerUpdateForTest(BybitWsCategory.OPTION, "BTC-7MAR26-60000-C-USDT",
                data, 123L);

        assertNotNull(update);
        assertEquals(new BigDecimal("101.5"), update.getBestBidPrice());
        assertEquals(new BigDecimal("3"), update.getBestBidSize());
        assertEquals(new BigDecimal("102.0"), update.getBestAskPrice());
        assertEquals(new BigDecimal("4"), update.getBestAskSize());
        assertEquals(new BigDecimal("2"), update.getLastSize());
        assertEquals(new BigDecimal("0.25"), update.getBidIv());
        assertEquals(new BigDecimal("0.28"), update.getAskIv());
        assertEquals(new BigDecimal("0.27"), update.getMarkIv());
    }

    private static class TestableBybitWebSocketApi extends BybitWebSocketApi {

        TestableBybitWebSocketApi() {
            super(BybitConfiguration.getInstance());
        }

        BybitWsCategory resolveCategoryForTest(String symbol, InstrumentType instrumentType) {
            return resolveCategory(symbol, instrumentType);
        }

        BybitTickerUpdate parseTickerUpdateForTest(BybitWsCategory category, String symbol, JsonObject data,
                Long rootTimestamp) {
            return parseTickerUpdate(category, symbol, data, rootTimestamp);
        }
    }
}
