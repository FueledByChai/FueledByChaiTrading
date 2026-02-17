package com.fueledbychai.lighter.common.api.ws.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;
import com.fueledbychai.lighter.common.api.ws.model.LighterTradesUpdate;

public class LighterAccountAllTradesWebSocketProcessorTest {

    @Test
    void parseAccountAllTradesMessageFromDocsFormat() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/account_all_trades\","
                + "\"channel\":\"account_all_trades:255\","
                + "\"trades\":{\"1\":[{"
                + "\"id\":\"777\","
                + "\"tx_hash\":\"0xfeed\","
                + "\"type\":\"sell\","
                + "\"market_id\":\"1\","
                + "\"size\":\"0.500\","
                + "\"price\":\"69000.0\","
                + "\"usd_amount\":\"34500.000\","
                + "\"ask_id\":\"12\","
                + "\"bid_id\":\"13\","
                + "\"ask_account_id\":\"255\","
                + "\"bid_account_id\":\"501\","
                + "\"is_maker_ask\":true,"
                + "\"block_height\":\"42010\","
                + "\"timestamp\":\"1700000002000\""
                + "}]}"
                + "}";

        LighterTradesUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals("account_all_trades:255", update.getChannel());
        assertEquals("update/account_all_trades", update.getMessageType());
        assertEquals(1, update.getTrades().size());

        LighterTrade trade = update.getFirstTrade();
        assertNotNull(trade);
        assertEquals(777L, trade.getId());
        assertEquals(1, trade.getMarketId());
        assertEquals(new BigDecimal("0.500"), trade.getSize());
        assertEquals(new BigDecimal("69000.0"), trade.getPrice());
        assertEquals(true, trade.getMakerAsk());
    }

    @Test
    void parseAccountAllTradesDoesNotFallbackMarketIdFromAccountChannel() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/account_all_trades\","
                + "\"channel\":\"account_all_trades/255\","
                + "\"trades\":[{"
                + "\"id\":778,"
                + "\"type\":\"buy\","
                + "\"size\":\"0.01\","
                + "\"price\":\"69100.0\""
                + "}]"
                + "}";

        LighterTradesUpdate update = processor.parse(message);
        assertNotNull(update);
        LighterTrade trade = update.getFirstTrade();
        assertNotNull(trade);
        assertNull(trade.getMarketId());
    }

    @Test
    void ignoreNonAccountAllTradesChannel() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"type\":\"update/account_all_trades\",\"channel\":\"trade:1\",\"trades\":[]}";
        assertNull(processor.parse(message));
    }

    private static class TestableProcessor extends LighterAccountAllTradesWebSocketProcessor {
        TestableProcessor() {
            super(() -> {
            });
        }

        LighterTradesUpdate parse(String message) {
            return super.parseMessage(message);
        }
    }
}
