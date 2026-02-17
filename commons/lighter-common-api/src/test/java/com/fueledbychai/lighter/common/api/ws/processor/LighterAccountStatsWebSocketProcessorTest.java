package com.fueledbychai.lighter.common.api.ws.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStats;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStatsBreakdown;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStatsUpdate;

public class LighterAccountStatsWebSocketProcessorTest {

    @Test
    void parseAccountStatsMessageFromDocsFormat() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/user_stats\","
                + "\"channel\":\"user_stats:4294967296\","
                + "\"stats\":{"
                + "\"collateral\":\"1034.28111\","
                + "\"portfolio_value\":\"1034.28111\","
                + "\"leverage\":\"0.00043\","
                + "\"available_balance\":\"1033.836\","
                + "\"margin_usage\":\"0.00043\","
                + "\"buying_power\":\"5171.406\","
                + "\"cross_stats\":{"
                + "\"collateral\":\"500.000\","
                + "\"portfolio_value\":\"500.250\","
                + "\"leverage\":\"0.10000\","
                + "\"available_balance\":\"450.125\","
                + "\"margin_usage\":\"0.05500\","
                + "\"buying_power\":\"2250.625\""
                + "},"
                + "\"total_stats\":{"
                + "\"collateral\":\"1500.000\","
                + "\"portfolio_value\":\"1520.500\","
                + "\"leverage\":\"0.30000\","
                + "\"available_balance\":\"1400.250\","
                + "\"margin_usage\":\"0.12500\","
                + "\"buying_power\":\"7001.250\""
                + "}"
                + "}"
                + "}";

        LighterAccountStatsUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals("update/user_stats", update.getMessageType());
        assertEquals("user_stats:4294967296", update.getChannel());
        assertEquals(4294967296L, update.getAccountIndex());

        LighterAccountStats stats = update.getStats();
        assertNotNull(stats);
        assertEquals(new BigDecimal("1034.28111"), stats.getCollateral());
        assertEquals(new BigDecimal("1033.836"), stats.getAvailableBalance());

        LighterAccountStatsBreakdown crossStats = stats.getCrossStats();
        assertNotNull(crossStats);
        assertEquals(new BigDecimal("500.250"), crossStats.getPortfolioValue());
        assertEquals(new BigDecimal("0.05500"), crossStats.getMarginUsage());

        LighterAccountStatsBreakdown totalStats = stats.getTotalStats();
        assertNotNull(totalStats);
        assertEquals(new BigDecimal("1520.500"), totalStats.getPortfolioValue());
        assertEquals(new BigDecimal("7001.250"), totalStats.getBuyingPower());
    }

    @Test
    void parseAccountStatsFromLegacyChannelName() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/account_stats\","
                + "\"channel\":\"account_stats/255\","
                + "\"stats\":{"
                + "\"collateral\":\"100.0\","
                + "\"portfolio_value\":\"120.0\""
                + "}"
                + "}";

        LighterAccountStatsUpdate update = processor.parse(message);
        assertNotNull(update);
        assertEquals(255L, update.getAccountIndex());
        assertNotNull(update.getStats());
        assertEquals(new BigDecimal("120.0"), update.getStats().getPortfolioValue());
    }

    @Test
    void ignoreNonAccountStatsChannel() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"type\":\"update/user_stats\",\"channel\":\"trade:1\",\"stats\":{}}";
        assertNull(processor.parse(message));
    }

    private static class TestableProcessor extends LighterAccountStatsWebSocketProcessor {
        TestableProcessor() {
            super(() -> {
            });
        }

        LighterAccountStatsUpdate parse(String message) {
            return super.parseMessage(message);
        }
    }
}
