package com.fueledbychai.drift.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.fueledbychai.drift.common.api.model.DriftMarketType;

class DriftWebSocketApiTest {

    @Test
    void buildOrderBookSubscribeMessageIncludesFullLiquidityFlags() {
        TestableDriftWebSocketApi api = new TestableDriftWebSocketApi();

        JSONObject message = new JSONObject(api.buildSubscribeMessage("SOL-PERP", DriftMarketType.PERP));

        assertEquals("subscribe", message.getString("type"));
        assertEquals("orderbook", message.getString("channel"));
        assertEquals("SOL-PERP", message.getString("market"));
        assertEquals("perp", message.getString("marketType"));
        assertEquals(10, message.getInt("grouping"));
        assertTrue(message.getBoolean("includeVamm"));
        assertTrue(message.getBoolean("includeIndicative"));
    }

    private static final class TestableDriftWebSocketApi extends DriftWebSocketApi {

        private TestableDriftWebSocketApi() {
            super("wss://dlob.drift.trade/ws", "wss://example.test/gateway");
        }

        private String buildSubscribeMessage(String marketName, DriftMarketType marketType) {
            return super.buildOrderBookSubscribeMessage(marketName, marketType);
        }
    }
}
