package com.fueledbychai.paradex.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.BarData;
import com.fueledbychai.paradex.common.api.historical.OHLCBar;

class ParadexUtilTest {

    @Test
    void testTranslateBar() {
        // Setup
        long epochTime = 1640995200L; // January 1, 2022 00:00:00 UTC
        OHLCBar bar = new OHLCBar(epochTime, 50000.00, 51000.00, 49000.00, 50500.00, 1000.5);

        // Execute
        BarData result = ParadexUtil.translateBar(bar);

        // Verify
        assertNotNull(result);
        assertEquals(Instant.ofEpochSecond(epochTime).atZone(ZoneOffset.UTC), result.getDateTime());
        assertEquals(new BigDecimal("50000.0"), result.getOpen());
        assertEquals(new BigDecimal("51000.0"), result.getHigh());
        assertEquals(new BigDecimal("49000.0"), result.getLow());
        assertEquals(new BigDecimal("50500.0"), result.getClose());
        assertEquals(new BigDecimal("1000.5"), result.getVolume());
    }

    @Test
    void testTranslateBarsList_EmptyList() {
        // Execute
        List<BarData> result = ParadexUtil.translateBars(Collections.emptyList());

        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testTranslateBarsList_MultipleBars() {
        // Setup
        OHLCBar bar1 = new OHLCBar(1640995200L, 50000.00, 51000.00, 49000.00, 50500.00, 1000.5);
        OHLCBar bar2 = new OHLCBar(1640998800L, 50500.00, 52000.00, 50000.00, 51500.00, 1500.25);

        List<OHLCBar> ohlcBars = Arrays.asList(bar1, bar2);

        // Execute
        List<BarData> result = ParadexUtil.translateBars(ohlcBars);

        // Verify
        assertNotNull(result);
        assertEquals(2, result.size());

        // Verify first bar
        BarData firstBar = result.get(0);
        assertEquals(Instant.ofEpochSecond(1640995200L).atZone(ZoneOffset.UTC), firstBar.getDateTime());
        assertEquals(new BigDecimal("50000.0"), firstBar.getOpen());
        assertEquals(new BigDecimal("51000.0"), firstBar.getHigh());
        assertEquals(new BigDecimal("49000.0"), firstBar.getLow());
        assertEquals(new BigDecimal("50500.0"), firstBar.getClose());
        assertEquals(new BigDecimal("1000.5"), firstBar.getVolume());

        // Verify second bar
        BarData secondBar = result.get(1);
        assertEquals(Instant.ofEpochSecond(1640998800L).atZone(ZoneOffset.UTC), secondBar.getDateTime());
        assertEquals(new BigDecimal("50500.0"), secondBar.getOpen());
        assertEquals(new BigDecimal("52000.0"), secondBar.getHigh());
        assertEquals(new BigDecimal("50000.0"), secondBar.getLow());
        assertEquals(new BigDecimal("51500.0"), secondBar.getClose());
        assertEquals(new BigDecimal("1500.25"), secondBar.getVolume());
    }

    @Test
    void testTranslateBar_WithNanoseconds() {
        // Test edge case with nanosecond precision (should be 0 based on the implementation)
        OHLCBar bar = new OHLCBar(1640995200L, 100.00, 110.00, 90.00, 105.00, 500.0);

        BarData result = ParadexUtil.translateBar(bar);

        // Verify that nanoseconds are set to 0 (as per the implementation)
        assertEquals(0, result.getDateTime().getNano());
    }

}
