package com.fueledbychai.paradex.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.BarData;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.common.api.historical.OHLCBar;
import com.fueledbychai.paradex.common.api.order.OrderType;
import com.fueledbychai.paradex.common.api.order.ParadexOrder;
import com.fueledbychai.paradex.common.api.order.Side;

@ExtendWith(MockitoExtension.class)
class ParadexUtilTest {

    @Mock
    private ParadexOrder mockParadexOrder;

    @Mock
    private OrderTicket mockTradeOrder;

    @Mock
    private Ticker mockTicker;

    @Mock
    private OHLCBar mockOHLCBar;

    @Test
    void testTranslateBar() {
        // Setup
        long epochTime = 1640995200L; // January 1, 2022 00:00:00 UTC
        when(mockOHLCBar.getTime()).thenReturn(epochTime);
        when(mockOHLCBar.getOpen()).thenReturn(50000.00);
        when(mockOHLCBar.getHigh()).thenReturn(51000.00);
        when(mockOHLCBar.getLow()).thenReturn(49000.00);
        when(mockOHLCBar.getClose()).thenReturn(50500.00);
        when(mockOHLCBar.getVolume()).thenReturn(1000.5);

        // Execute
        BarData result = ParadexUtil.translateBar(mockOHLCBar);

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
        OHLCBar bar1 = mock(OHLCBar.class);
        when(bar1.getTime()).thenReturn(1640995200L);
        when(bar1.getOpen()).thenReturn(50000.00);
        when(bar1.getHigh()).thenReturn(51000.00);
        when(bar1.getLow()).thenReturn(49000.00);
        when(bar1.getClose()).thenReturn(50500.00);
        when(bar1.getVolume()).thenReturn(1000.5);

        OHLCBar bar2 = mock(OHLCBar.class);
        when(bar2.getTime()).thenReturn(1640998800L);
        when(bar2.getOpen()).thenReturn(50500.00);
        when(bar2.getHigh()).thenReturn(52000.00);
        when(bar2.getLow()).thenReturn(50000.00);
        when(bar2.getClose()).thenReturn(51500.00);
        when(bar2.getVolume()).thenReturn(1500.25);

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
        // Test edge case with nanosecond precision (should be 0 based on the
        // implementation)
        when(mockOHLCBar.getTime()).thenReturn(1640995200L);
        when(mockOHLCBar.getOpen()).thenReturn(100.00);
        when(mockOHLCBar.getHigh()).thenReturn(110.00);
        when(mockOHLCBar.getLow()).thenReturn(90.00);
        when(mockOHLCBar.getClose()).thenReturn(105.00);
        when(mockOHLCBar.getVolume()).thenReturn(500.0);

        BarData result = ParadexUtil.translateBar(mockOHLCBar);

        // Verify that nanoseconds are set to 0 (as per the implementation)
        assertEquals(0, result.getDateTime().getNano());
    }

}