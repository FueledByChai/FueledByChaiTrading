package com.fueledbychai.lighter.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class LighterMarketStatsWebSocketProcessorTest {

    @Test
    void parseSingleMarketStatsMessage() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"channel_data\","
                + "\"channel\":\"market_stats:53\","
                + "\"market_stats\":{"
                + "\"market_id\":53,"
                + "\"mark_price\":\"61.562\","
                + "\"index_price\":\"61.536\","
                + "\"daily_base_volume\":\"57295.260000\","
                + "\"daily_quote_volume\":\"3566354.617968\","
                + "\"open_interest\":\"499340.0\","
                + "\"funding_rate\":\"0.000055\","
                + "\"daily_low\":\"55.268\","
                + "\"daily_high\":\"62.226\","
                + "\"daily_price_change_24h\":\"5.297\","
                + "\"daily_price_change_percent_24h\":\"9.415\","
                + "\"last_price\":\"61.538\""
                + "}"
                + "}";

        LighterMarketStatsUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals("market_stats:53", update.getChannel());
        assertEquals(1, update.getMarketStatsByMarketId().size());

        LighterMarketStats stats = update.getMarketStats("53");
        assertNotNull(stats);
        assertEquals(53, stats.getMarketId());
        assertEquals(new BigDecimal("61.562"), stats.getMarkPrice());
        assertEquals(new BigDecimal("0.000055"), stats.getFundingRate());
    }

    @Test
    void parseAllMarketStatsMessage() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"channel_data\","
                + "\"channel\":\"market_stats/all\","
                + "\"market_stats\":{"
                + "\"1073\":{"
                + "\"market_id\":1073,"
                + "\"mark_price\":\"5.234\","
                + "\"index_price\":\"5.220\","
                + "\"daily_base_volume\":\"100.0\","
                + "\"daily_quote_volume\":\"523.4\","
                + "\"daily_low\":\"4.100\","
                + "\"daily_high\":\"5.500\","
                + "\"daily_price_change_24h\":\"0.123\","
                + "\"daily_price_change_percent_24h\":\"2.4\","
                + "\"last_price\":\"5.210\""
                + "},"
                + "\"1074\":{"
                + "\"market_id\":1074,"
                + "\"mark_price\":\"3.100\","
                + "\"index_price\":\"3.105\","
                + "\"daily_base_volume\":\"200.0\","
                + "\"daily_quote_volume\":\"620.0\","
                + "\"daily_low\":\"3.000\","
                + "\"daily_high\":\"3.250\","
                + "\"daily_price_change_24h\":\"0.050\","
                + "\"daily_price_change_percent_24h\":\"1.6\","
                + "\"last_price\":\"3.102\""
                + "}"
                + "}"
                + "}";

        LighterMarketStatsUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals("market_stats/all", update.getChannel());
        assertEquals(2, update.getMarketStatsByMarketId().size());
        assertEquals(new BigDecimal("5.234"), update.getMarketStats("1073").getMarkPrice());
        assertEquals(new BigDecimal("3.102"), update.getMarketStats("1074").getLastPrice());
    }

    @Test
    void ignoreNonMarketStatsChannel() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"type\":\"channel_data\",\"channel\":\"order_book/53\",\"market_stats\":{}}";
        assertNull(processor.parse(message));
    }

    @Test
    void parseSlashChannelForBackwardCompatibility() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"channel_data\","
                + "\"channel\":\"market_stats/53\","
                + "\"market_stats\":{"
                + "\"market_id\":53,"
                + "\"mark_price\":\"61.562\""
                + "}"
                + "}";

        LighterMarketStatsUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals("market_stats/53", update.getChannel());
        assertEquals(new BigDecimal("61.562"), update.getMarketStats("53").getMarkPrice());
    }

    private static class TestableProcessor extends LighterMarketStatsWebSocketProcessor {
        TestableProcessor() {
            super(() -> {
            });
        }

        LighterMarketStatsUpdate parse(String message) {
            return super.parseMessage(message);
        }
    }
}
