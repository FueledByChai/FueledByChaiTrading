package com.fueledbychai.drift.common.api.ws.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.drift.common.api.model.DriftOrderBookSnapshot;

class DriftOrderBookProcessorTest {

    @Test
    void parseWrappedOrderBookMessageUsesNestedDataPayload() {
        TestableProcessor processor = new TestableProcessor();

        DriftOrderBookSnapshot snapshot = processor.parse("""
                {
                  "channel": "orderbook",
                  "market": "SOL-PERP",
                  "marketType": "perp",
                  "data": {
                    "ts": 1710000000123,
                    "slot": 456,
                    "markPrice": "101250000",
                    "bestBidPrice": "101100000",
                    "bestAskPrice": "101300000",
                    "oracle": "101200000",
                    "bids": [
                      {
                        "price": "101100000",
                        "size": "2500000000"
                      }
                    ],
                    "asks": [
                      {
                        "price": "101300000",
                        "size": "1750000000"
                      }
                    ]
                  }
                }
                """);

        assertNotNull(snapshot);
        assertEquals("SOL-PERP", snapshot.getMarketName());
        assertEquals(new BigDecimal("101.100000"), snapshot.getBestBidPrice());
        assertEquals(new BigDecimal("101.300000"), snapshot.getBestAskPrice());
        assertEquals(new BigDecimal("2.500000000"), snapshot.getBids().get(0).getSize());
        assertEquals(new BigDecimal("1.750000000"), snapshot.getAsks().get(0).getSize());
    }

    @Test
    void parseWrappedOrderBookMessageUsesStringifiedDataPayload() {
        TestableProcessor processor = new TestableProcessor();

        DriftOrderBookSnapshot snapshot = processor.parse("""
                {
                  "channel": "orderbook_perp_0_grouped_10",
                  "data": "{\\"ts\\":1710000000123,\\"slot\\":456,\\"bestBidPrice\\":\\"101100000\\",\\"bestAskPrice\\":\\"101300000\\",\\"bids\\":[{\\"price\\":\\"101100000\\",\\"size\\":\\"2500000000\\"}],\\"asks\\":[{\\"price\\":\\"101300000\\",\\"size\\":\\"1750000000\\"}]}"
                }
                """);

        assertNotNull(snapshot);
        assertEquals(DriftMarketType.PERP, snapshot.getMarketType());
        assertEquals(0, snapshot.getMarketIndex());
        assertEquals(new BigDecimal("101.100000"), snapshot.getBestBidPrice());
        assertEquals(new BigDecimal("101.300000"), snapshot.getBestAskPrice());
    }

    @Test
    void ignoreMessageWithoutOrderBookPayload() {
        TestableProcessor processor = new TestableProcessor();

        assertNull(processor.parse("""
                {
                  "channel": "orderbook",
                  "market": "SOL-PERP",
                  "data": {
                    "slot": 456
                  }
                }
                """));
    }

    private static final class TestableProcessor extends DriftOrderBookProcessor {

        private TestableProcessor() {
            super(() -> {
            });
        }

        private DriftOrderBookSnapshot parse(String message) {
            return super.parseMessage(message);
        }
    }
}
