package com.fueledbychai.hibachi.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class HibachiMarketSubscribeMessageTest {

    @Test
    void subscribe_singleTopic_buildsExpectedJson() {
        // Reference fixture from Python SDK: tests/unit/ws/test_market_ws.py
        String json = HibachiMarketSubscribeMessage.subscribe("BTC/USDT-P", "mark_price");
        JSONObject root = new JSONObject(json);
        assertEquals("subscribe", root.getString("method"));
        JSONObject parameters = root.getJSONObject("parameters");
        assertEquals(1, parameters.getJSONArray("subscriptions").length());
        JSONObject sub = parameters.getJSONArray("subscriptions").getJSONObject(0);
        assertEquals("BTC/USDT-P", sub.getString("symbol"));
        assertEquals("mark_price", sub.getString("topic"));
    }

    @Test
    void subscribe_outerKeyIsParametersNotParams() {
        // Critical — market WS uses "parameters" while trade/account WS use "params".
        // See api_ws_market.py:110-116.
        String json = HibachiMarketSubscribeMessage.subscribe("ETH/USDT-P", "orderbook");
        JSONObject root = new JSONObject(json);
        org.junit.jupiter.api.Assertions.assertTrue(root.has("parameters"));
        org.junit.jupiter.api.Assertions.assertFalse(root.has("params"));
    }

    @Test
    void subscribe_multipleSubscriptions_preservesOrder() {
        String json = HibachiMarketSubscribeMessage.subscribe(List.of(
                new HibachiMarketSubscribeMessage.Subscription("BTC/USDT-P", "ask_bid_price"),
                new HibachiMarketSubscribeMessage.Subscription("BTC/USDT-P", "mark_price")));
        JSONObject root = new JSONObject(json);
        JSONObject sub0 = root.getJSONObject("parameters").getJSONArray("subscriptions").getJSONObject(0);
        JSONObject sub1 = root.getJSONObject("parameters").getJSONArray("subscriptions").getJSONObject(1);
        assertEquals("ask_bid_price", sub0.getString("topic"));
        assertEquals("mark_price", sub1.getString("topic"));
    }

    @Test
    void unsubscribe_usesUnsubscribeMethod() {
        String json = HibachiMarketSubscribeMessage.unsubscribe("BTC/USDT-P", "trades");
        assertEquals("unsubscribe", new JSONObject(json).getString("method"));
    }

    @Test
    void emptySubscriptionList_throws() {
        assertThrows(IllegalArgumentException.class, () -> HibachiMarketSubscribeMessage.subscribe(List.of()));
    }

    @Test
    void blankSymbolOrTopic_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new HibachiMarketSubscribeMessage.Subscription("", "mark_price"));
        assertThrows(IllegalArgumentException.class,
                () -> new HibachiMarketSubscribeMessage.Subscription("BTC/USDT-P", ""));
    }
}
