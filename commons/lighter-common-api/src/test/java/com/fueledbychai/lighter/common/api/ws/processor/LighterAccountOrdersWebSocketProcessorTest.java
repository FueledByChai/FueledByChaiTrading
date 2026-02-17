package com.fueledbychai.lighter.common.api.ws.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrdersUpdate;

public class LighterAccountOrdersWebSocketProcessorTest {

    @Test
    void parseAccountOrdersMessageFromDocsFormat() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/account_orders\","
                + "\"channel\":\"account_orders:1\","
                + "\"account\":\"255\","
                + "\"nonce\":\"170001\","
                + "\"orders\":{\"1\":[{"
                + "\"order_index\":\"1001\","
                + "\"client_order_index\":\"7777\","
                + "\"order_id\":\"1001\","
                + "\"client_order_id\":\"7777\","
                + "\"market_index\":\"1\","
                + "\"owner_account_index\":\"255\","
                + "\"initial_base_amount\":\"0.020\","
                + "\"price\":\"70123.4\","
                + "\"nonce\":\"22\","
                + "\"remaining_base_amount\":\"0.010\","
                + "\"is_ask\":false,"
                + "\"base_size\":\"2000\","
                + "\"base_price\":\"701234\","
                + "\"filled_base_amount\":\"0.010\","
                + "\"filled_quote_amount\":\"701.234\","
                + "\"side\":\"buy\","
                + "\"type\":\"limit\","
                + "\"time_in_force\":\"gtt\","
                + "\"reduce_only\":false,"
                + "\"trigger_price\":\"0\","
                + "\"order_expiry\":\"1770000000000\","
                + "\"status\":\"open\","
                + "\"trigger_status\":\"inactive\","
                + "\"trigger_time\":\"0\","
                + "\"parent_order_index\":\"0\","
                + "\"parent_order_id\":\"0\","
                + "\"to_trigger_order_id_0\":\"0\","
                + "\"to_trigger_order_id_1\":\"0\","
                + "\"to_cancel_order_id_0\":\"0\","
                + "\"block_height\":\"42011\","
                + "\"timestamp\":\"1700000003000\","
                + "\"created_at\":\"1700000003000\","
                + "\"updated_at\":\"1700000003001\","
                + "\"transaction_time\":\"1700000003002\""
                + "}]}}";

        LighterOrdersUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals("account_orders:1", update.getChannel());
        assertEquals("update/account_orders", update.getMessageType());
        assertEquals(255L, update.getAccountIndex());
        assertEquals(170001L, update.getNonce());
        assertEquals(1, update.getOrdersByMarket().size());
        assertEquals(1, update.getTotalOrderCount());

        LighterOrder order = update.getFirstOrder();
        assertNotNull(order);
        assertEquals(1001L, order.getOrderIndex());
        assertEquals(7777L, order.getClientOrderIndex());
        assertEquals("1001", order.getOrderId());
        assertEquals("7777", order.getClientOrderId());
        assertEquals(1, order.getMarketIndex());
        assertEquals(255L, order.getOwnerAccountIndex());
        assertEquals(new BigDecimal("0.020"), order.getInitialBaseAmount());
        assertEquals(new BigDecimal("70123.4"), order.getPrice());
        assertEquals(new BigDecimal("0.010"), order.getRemainingBaseAmount());
        assertEquals(false, order.getAsk());
        assertEquals(new BigDecimal("0.010"), order.getFilledBaseAmount());
        assertEquals(new BigDecimal("701.234"), order.getFilledQuoteAmount());
        assertEquals("buy", order.getSide());
        assertEquals("open", order.getStatus());
    }

    @Test
    void parseAccountOrdersSlashChannelWithFallbackMarketFromChannel() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/account_orders\","
                + "\"channel\":\"account_orders/53/255\","
                + "\"account\":255,"
                + "\"nonce\":3,"
                + "\"orders\":{\"53\":[{"
                + "\"order_index\":1002,"
                + "\"price\":\"70200.0\","
                + "\"remaining_base_amount\":\"0.015\""
                + "}]}"
                + "}";

        LighterOrdersUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals(1, update.getOrdersByMarket().size());
        LighterOrder order = update.getFirstOrder();
        assertNotNull(order);
        assertEquals(53, order.getMarketIndex());
        assertEquals(new BigDecimal("70200.0"), order.getPrice());
    }

    @Test
    void ignoreNonAccountOrdersChannel() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"type\":\"update/account_orders\",\"channel\":\"trade:1\",\"orders\":{}}";
        assertNull(processor.parse(message));
    }

    @Test
    void ignoreAccountOrdersMessageWithoutOrders() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"type\":\"update/account_orders\",\"channel\":\"account_orders:1\"}";
        assertNull(processor.parse(message));
    }

    private static class TestableProcessor extends LighterAccountOrdersWebSocketProcessor {
        TestableProcessor() {
            super(() -> {
            });
        }

        LighterOrdersUpdate parse(String message) {
            return super.parseMessage(message);
        }
    }
}
