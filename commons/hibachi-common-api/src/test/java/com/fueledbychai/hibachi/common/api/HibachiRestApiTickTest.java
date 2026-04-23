package com.fueledbychai.hibachi.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link HibachiRestApi#resolveEffectiveTickSize(BigDecimal, List)} — the
 * tick-aggregation rule that prevents Hibachi's native 1e-7/1e-9 ticks from
 * collapsing every tick-denominated strategy parameter.
 */
class HibachiRestApiTickTest {

    @Test
    void solPicksFinestGranularityAboveNativeTick() {
        // Real SOL/USDT-P data from /market/exchange-info on 2026-04-22.
        BigDecimal rawTick = new BigDecimal("0.0000001");
        List<String> granularities = List.of("0.001", "0.01", "0.1", "1");
        BigDecimal effective = HibachiRestApi.resolveEffectiveTickSize(rawTick, granularities);
        assertEquals(new BigDecimal("0.001"), effective);
    }

    @Test
    void missingGranularitiesFallsBackToNativeTick() {
        BigDecimal rawTick = new BigDecimal("0.0000001");
        assertEquals(rawTick, HibachiRestApi.resolveEffectiveTickSize(rawTick, null));
        assertEquals(rawTick, HibachiRestApi.resolveEffectiveTickSize(rawTick, List.of()));
    }

    @Test
    void malformedGranularitiesAreSkipped() {
        BigDecimal rawTick = new BigDecimal("0.0000001");
        List<String> granularities = List.of("garbage", "", "0.01", "also-bad");
        BigDecimal effective = HibachiRestApi.resolveEffectiveTickSize(rawTick, granularities);
        assertEquals(new BigDecimal("0.01"), effective);
    }

    @Test
    void granularitiesFinerThanNativeTickAreIgnored() {
        // Hypothetical: granularities list includes an entry finer than the raw
        // tick (shouldn't happen in practice, but guard anyway — we can't place
        // an order finer than native tick even if the book claims to aggregate
        // at that level).
        BigDecimal rawTick = new BigDecimal("0.01");
        List<String> granularities = List.of("0.001", "0.1", "1");
        BigDecimal effective = HibachiRestApi.resolveEffectiveTickSize(rawTick, granularities);
        assertEquals(new BigDecimal("0.1"), effective);
    }

    @Test
    void allGranularitiesFinerThanNativeTickFallsBackToRaw() {
        BigDecimal rawTick = new BigDecimal("0.1");
        List<String> granularities = List.of("0.001", "0.01");
        BigDecimal effective = HibachiRestApi.resolveEffectiveTickSize(rawTick, granularities);
        assertEquals(rawTick, effective);
    }

    @Test
    void negativeOrZeroGranularitiesAreSkipped() {
        BigDecimal rawTick = new BigDecimal("0.0000001");
        List<String> granularities = List.of("-0.001", "0", "0.01");
        BigDecimal effective = HibachiRestApi.resolveEffectiveTickSize(rawTick, granularities);
        assertEquals(new BigDecimal("0.01"), effective);
    }
}
