package com.fueledbychai.lighter.common.api.ws.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;
import com.fueledbychai.lighter.common.api.ws.model.LighterTradesUpdate;

public class LighterTradesWebSocketProcessorTest {

    @Test
    void parseTradeMessageFromDocsFormat() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/trade\","
                + "\"channel\":\"trade:1\","
                + "\"trades\":[{"
                + "\"id\":\"1234\","
                + "\"tx_hash\":\"0xabc\","
                + "\"type\":\"buy\","
                + "\"market_id\":\"1\","
                + "\"size\":\"0.010\","
                + "\"price\":\"100000.0\","
                + "\"usd_amount\":\"1000.000\","
                + "\"ask_id\":\"10\","
                + "\"bid_id\":\"11\","
                + "\"ask_account_id\":\"501\","
                + "\"bid_account_id\":\"502\","
                + "\"is_maker_ask\":false,"
                + "\"block_height\":\"42000\","
                + "\"timestamp\":\"1700000000000\""
                + "}]"
                + "}";

        LighterTradesUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals("trade:1", update.getChannel());
        assertEquals("update/trade", update.getMessageType());
        assertEquals(1, update.getTrades().size());

        LighterTrade trade = update.getFirstTrade();
        assertNotNull(trade);
        assertEquals(1234L, trade.getId());
        assertEquals("0xabc", trade.getTxHash());
        assertEquals("buy", trade.getType());
        assertEquals(1, trade.getMarketId());
        assertEquals(new BigDecimal("0.010"), trade.getSize());
        assertEquals(new BigDecimal("100000.0"), trade.getPrice());
        assertEquals(new BigDecimal("1000.000"), trade.getUsdAmount());
        assertEquals(10L, trade.getAskId());
        assertEquals(11L, trade.getBidId());
        assertEquals(501L, trade.getAskAccountId());
        assertEquals(502L, trade.getBidAccountId());
        assertEquals(false, trade.getMakerAsk());
        assertEquals(42000L, trade.getBlockHeight());
        assertEquals(1700000000000L, trade.getTimestamp());
    }

    @Test
    void parseTradeSlashChannelWithFallbackMarketId() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/trade\","
                + "\"channel\":\"trade/53\","
                + "\"trades\":[{"
                + "\"id\":1235,"
                + "\"type\":\"sell\","
                + "\"size\":\"1.25\","
                + "\"price\":\"101.2\","
                + "\"is_maker_ask\":\"true\""
                + "}]"
                + "}";

        LighterTradesUpdate update = processor.parse(message);
        assertNotNull(update);
        LighterTrade trade = update.getFirstTrade();
        assertNotNull(trade);
        assertEquals(53, trade.getMarketId());
        assertEquals(true, trade.getMakerAsk());
        assertEquals(new BigDecimal("1.25"), trade.getSize());
        assertEquals(new BigDecimal("101.2"), trade.getPrice());
    }

    @Test
    void ignoreNonTradeChannel() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"type\":\"update/trade\",\"channel\":\"market_stats:1\",\"trades\":[]}";
        assertNull(processor.parse(message));
    }

    @Test
    void ignoreTradeMessageWithoutTradesArray() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"type\":\"update/trade\",\"channel\":\"trade:1\"}";
        assertNull(processor.parse(message));
    }

    private static class TestableProcessor extends LighterTradesWebSocketProcessor {
        TestableProcessor() {
            super(() -> {
            });
        }

        LighterTradesUpdate parse(String message) {
            return super.parseMessage(message);
        }
    }
}
