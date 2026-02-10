package com.fueledbychai.lighter.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class LighterOrderBookWebSocketProcessorTest {

    @Test
    void parseOrderBookMessageFromDocsFormat() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/order_book\","
                + "\"channel\":\"order_book:1\","
                + "\"order_book\":{"
                + "\"code\":\"200\","
                + "\"asks\":[{\"price\":\"100.00\",\"size\":\"2.50\"}],"
                + "\"bids\":[{\"price\":\"99.50\",\"size\":\"3.00\"}]"
                + "},"
                + "\"timestamp\":\"1700000000000\","
                + "\"offset\":\"123\","
                + "\"nonce\":\"1001\","
                + "\"begin_nonce\":\"1000\""
                + "}";

        LighterOrderBookUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals("order_book:1", update.getChannel());
        assertEquals(1, update.getMarketId());
        assertEquals(200, update.getCode());
        assertEquals(123L, update.getOffset());
        assertEquals(1001L, update.getNonce());
        assertEquals(1000L, update.getBeginNonce());
        assertEquals(1700000000000L, update.getTimestamp());
        assertEquals("update/order_book", update.getMessageType());
        assertEquals(new BigDecimal("100.00"), update.getBestAsk().getPrice());
        assertEquals(new BigDecimal("2.50"), update.getBestAsk().getSize());
        assertEquals(new BigDecimal("99.50"), update.getBestBid().getPrice());
        assertEquals(new BigDecimal("3.00"), update.getBestBid().getSize());
    }

    @Test
    void parseOrderBookSlashChannelAndArrayLevels() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/order_book\","
                + "\"channel\":\"order_book/53\","
                + "\"order_book\":{"
                + "\"code\":200,"
                + "\"market_id\":53,"
                + "\"asks\":[[\"101.25\",\"1.20\"]],"
                + "\"bids\":[[\"101.20\",\"0.95\"]]"
                + "}"
                + "}";

        LighterOrderBookUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals("order_book/53", update.getChannel());
        assertEquals(53, update.getMarketId());
        assertEquals(200, update.getCode());
        assertEquals(new BigDecimal("101.25"), update.getBestAsk().getPrice());
        assertEquals(new BigDecimal("1.20"), update.getBestAsk().getSize());
        assertEquals(new BigDecimal("101.20"), update.getBestBid().getPrice());
        assertEquals(new BigDecimal("0.95"), update.getBestBid().getSize());
    }

    @Test
    void ignoreNonOrderBookChannel() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"type\":\"channel_data\",\"channel\":\"market_stats:1\",\"order_book\":{}}";
        assertNull(processor.parse(message));
    }

    @Test
    void ignoreMessageWithoutOrderBookPayload() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"type\":\"update/order_book\",\"channel\":\"order_book:1\"}";
        assertNull(processor.parse(message));
    }

    private static class TestableProcessor extends LighterOrderBookWebSocketProcessor {
        TestableProcessor() {
            super(() -> {
            });
        }

        LighterOrderBookUpdate parse(String message) {
            return super.parseMessage(message);
        }
    }
}
