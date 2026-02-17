package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;

class LighterRestApiActiveOrdersTest {

    @Test
    void parseAccountActiveOrdersResponseMapsOrderFields() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        String response = "{"
                + "\"code\":200,"
                + "\"message\":\"ok\","
                + "\"orders\":[{"
                + "\"order_index\":111,"
                + "\"client_order_index\":222,"
                + "\"order_id\":\"o-1\","
                + "\"client_order_id\":\"co-1\","
                + "\"market_index\":1,"
                + "\"owner_account_index\":255,"
                + "\"initial_base_amount\":\"0.015\","
                + "\"price\":\"68900.5\","
                + "\"nonce\":7,"
                + "\"remaining_base_amount\":\"0.010\","
                + "\"is_ask\":false,"
                + "\"base_size\":1500,"
                + "\"base_price\":689005,"
                + "\"filled_base_amount\":\"0.005\","
                + "\"filled_quote_amount\":\"344.5025\","
                + "\"side\":\"buy\","
                + "\"type\":\"limit\","
                + "\"time_in_force\":\"good-till-time\","
                + "\"reduce_only\":false,"
                + "\"trigger_price\":\"0\","
                + "\"order_expiry\":1773652539565,"
                + "\"status\":\"open\","
                + "\"trigger_status\":\"na\","
                + "\"trigger_time\":0,"
                + "\"parent_order_index\":0,"
                + "\"parent_order_id\":\"\","
                + "\"to_trigger_order_id_0\":\"\","
                + "\"to_trigger_order_id_1\":\"\","
                + "\"to_cancel_order_id_0\":\"\","
                + "\"block_height\":12345,"
                + "\"timestamp\":1771233339092,"
                + "\"created_at\":1771233339092,"
                + "\"updated_at\":1771233339093,"
                + "\"transaction_time\":1771233339094"
                + "}]"
                + "}";

        List<LighterOrder> orders = api.parseOrders(response);

        assertEquals(1, orders.size());
        LighterOrder order = orders.get(0);
        assertEquals(111L, order.getOrderIndex());
        assertEquals(222L, order.getClientOrderIndex());
        assertEquals("o-1", order.getOrderId());
        assertEquals("co-1", order.getClientOrderId());
        assertEquals(1, order.getMarketIndex());
        assertEquals(255L, order.getOwnerAccountIndex());
        assertBigDecimalEquals("0.015", order.getInitialBaseAmount());
        assertBigDecimalEquals("68900.5", order.getPrice());
        assertEquals(7L, order.getNonce());
        assertBigDecimalEquals("0.010", order.getRemainingBaseAmount());
        assertEquals(Boolean.FALSE, order.getAsk());
        assertEquals(1500L, order.getBaseSize());
        assertEquals(689005L, order.getBasePrice());
        assertBigDecimalEquals("0.005", order.getFilledBaseAmount());
        assertBigDecimalEquals("344.5025", order.getFilledQuoteAmount());
        assertEquals("buy", order.getSide());
        assertEquals("limit", order.getType());
        assertEquals("good-till-time", order.getTimeInForce());
        assertEquals(Boolean.FALSE, order.getReduceOnly());
        assertBigDecimalEquals("0", order.getTriggerPrice());
        assertEquals(1773652539565L, order.getOrderExpiry());
        assertEquals("open", order.getStatus());
    }

    @Test
    void parseAccountActiveOrdersResponseReturnsEmptyWhenNoOrders() {
        TestableLighterRestApi api = new TestableLighterRestApi();
        String response = "{\"code\":200,\"orders\":[]}";

        List<LighterOrder> orders = api.parseOrders(response);

        assertTrue(orders.isEmpty());
    }

    @Test
    void getAccountActiveOrdersValidatesInputs() {
        TestableLighterRestApi api = new TestableLighterRestApi();

        assertThrows(IllegalArgumentException.class, () -> api.getAccountActiveOrders("", 1L, 1));
        assertThrows(IllegalArgumentException.class, () -> api.getAccountActiveOrders("token", -1L, 1));
        assertThrows(IllegalArgumentException.class, () -> api.getAccountActiveOrders("token", 1L, -1));
    }

    private static void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0,
                "Expected " + expected + " but got " + actual);
    }

    private static class TestableLighterRestApi extends LighterRestApi {
        TestableLighterRestApi() {
            super("https://example.com", true);
        }

        List<LighterOrder> parseOrders(String responseBody) {
            return parseAccountActiveOrdersResponse(responseBody);
        }
    }
}
