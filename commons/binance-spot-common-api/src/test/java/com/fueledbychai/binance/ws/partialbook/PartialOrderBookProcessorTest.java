package com.fueledbychai.binance.ws.partialbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class PartialOrderBookProcessorTest {

    @Test
    void parseDirectPartialBookMessageWithEventTime() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"lastUpdateId\":12345,"
                + "\"E\":1700000000123,"
                + "\"bids\":[[\"100.00\",\"1.50\"]],"
                + "\"asks\":[[\"100.10\",\"1.20\"]]"
                + "}";

        OrderBookSnapshot snapshot = processor.parse(message);

        assertNotNull(snapshot);
        assertEquals(12345L, snapshot.getLastUpdateId());
        assertEquals(1700000000123L, snapshot.getEventTime());
        assertEquals("100.00", snapshot.getBestBid().getPrice());
        assertEquals("100.10", snapshot.getBestAsk().getPrice());
    }

    @Test
    void parseCombinedStreamMessageWithEventTime() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"stream\":\"btcusdt@depth20@100ms\","
                + "\"data\":{"
                + "\"lastUpdateId\":12346,"
                + "\"E\":1700000001123,"
                + "\"bids\":[[\"101.00\",\"2.50\"]],"
                + "\"asks\":[[\"101.10\",\"2.20\"]]"
                + "}"
                + "}";

        OrderBookSnapshot snapshot = processor.parse(message);

        assertNotNull(snapshot);
        assertEquals(12346L, snapshot.getLastUpdateId());
        assertEquals(1700000001123L, snapshot.getEventTime());
        assertEquals("101.00", snapshot.getBestBid().getPrice());
        assertEquals("101.10", snapshot.getBestAsk().getPrice());
    }

    private static class TestableProcessor extends PartialOrderBookProcessor {
        TestableProcessor() {
            super(() -> {
            });
        }

        OrderBookSnapshot parse(String message) {
            return super.parseMessage(message);
        }
    }
}
