package com.fueledbychai.marketdata.hibachi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * Regression for the live_book bounded-window pruning. live_book keeps only
 * `depth` levels per side and never sends qty=0 removals for levels that
 * scroll out of the window, so without pruning a stale far-side level
 * eventually becomes the "best" and crosses the book by 100+ bps.
 */
class HibachiBookWindowTest {

    private static TreeMap<BigDecimal, BigDecimal> bidMap() {
        return new TreeMap<>(Comparator.reverseOrder());
    }

    @Test
    void prunesStaleAskBelowWindow_uncrossesBook() {
        // Asks (ascending). A stale ask at 68.00 lingers from an earlier price
        // range; current window is [72.58, 72.67]. Bids top at 72.54.
        TreeMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
        asks.put(new BigDecimal("68.00"), new BigDecimal("5"));   // stale → best ask = 68.00 (crosses!)
        asks.put(new BigDecimal("72.5832"), new BigDecimal("10"));
        asks.put(new BigDecimal("72.6684"), new BigDecimal("12"));

        // Before pruning, the stale level is the (wrong) best ask.
        assertEquals(new BigDecimal("68.00"), asks.firstKey());

        HibachiQuoteEngine.pruneOutsideWindow(asks, new BigDecimal("72.5832"), new BigDecimal("72.6684"));

        assertFalse(asks.containsKey(new BigDecimal("68.00")), "stale out-of-window ask must be dropped");
        assertEquals(new BigDecimal("72.5832"), asks.firstKey(), "best ask is now the live touch");
    }

    @Test
    void prunesStaleBidAboveWindow() {
        // Bids (descending, reverseOrder). Stale high bid at 75.00 from an
        // earlier range; current window is [72.5050, 72.5421].
        TreeMap<BigDecimal, BigDecimal> bids = bidMap();
        bids.put(new BigDecimal("75.00"), new BigDecimal("3"));   // stale → would be best bid
        bids.put(new BigDecimal("72.5421"), new BigDecimal("14"));
        bids.put(new BigDecimal("72.5050"), new BigDecimal("9"));

        // start=72.5421 (best/high), end=72.5050 (low) — order-agnostic.
        HibachiQuoteEngine.pruneOutsideWindow(bids, new BigDecimal("72.5421"), new BigDecimal("72.5050"));

        assertFalse(bids.containsKey(new BigDecimal("75.00")), "stale out-of-window bid must be dropped");
        assertEquals(new BigDecimal("72.5421"), bids.firstKey(), "best bid is the live touch");
        assertTrue(bids.containsKey(new BigDecimal("72.5050")), "in-window level retained");
    }

    @Test
    void nullBoundsAreNoOp() {
        TreeMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
        asks.put(new BigDecimal("72.5"), new BigDecimal("1"));
        HibachiQuoteEngine.pruneOutsideWindow(asks, null, new BigDecimal("72.6"));
        assertEquals(1, asks.size(), "missing window bound must not wipe the book");
    }
}
