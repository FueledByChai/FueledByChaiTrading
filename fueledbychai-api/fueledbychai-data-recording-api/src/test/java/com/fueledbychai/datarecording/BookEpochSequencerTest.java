package com.fueledbychai.datarecording;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookEpochSequencerTest {

    @Test
    public void deltaBeforeAnchorIsInvalid() {
        BookEpochSequencer seq = new BookEpochSequencer();
        BookEpochSequencer.Result r = seq.delta(100);
        assertFalse("no anchor yet -> invalid", r.valid());
        assertFalse(r.gap());
    }

    @Test
    public void contiguousDeltasStayValidInOneEpoch() {
        BookEpochSequencer seq = new BookEpochSequencer();
        seq.anchor(10);
        assertEquals(1, seq.epoch());
        assertTrue(seq.delta(11).valid());
        assertTrue(seq.delta(12).valid());
        assertTrue(seq.delta(13).valid());
        assertEquals(1, seq.epoch());
        assertEquals(13, seq.lastSeq());
    }

    @Test
    public void gapMarksInvalidUntilNextAnchor() {
        BookEpochSequencer seq = new BookEpochSequencer();
        seq.anchor(10);
        assertTrue(seq.delta(11).valid());

        BookEpochSequencer.Result gap = seq.delta(13); // skipped 12
        assertTrue("the breaking delta reports a gap", gap.gap());
        assertFalse(gap.valid());

        // Still invalid while no anchor — even an in-order-looking delta is untrusted.
        assertFalse(seq.delta(14).valid());

        // Re-anchor starts a fresh, valid epoch.
        BookEpochSequencer.Result anchored = seq.anchor(20);
        assertTrue(anchored.valid());
        assertEquals(2, seq.epoch());
        assertTrue(seq.delta(21).valid());
    }

    @Test
    public void outOfOrderDeltaIsAGap() {
        BookEpochSequencer seq = new BookEpochSequencer();
        seq.anchor(10);
        seq.delta(11);
        assertTrue(seq.delta(10).gap()); // regression
    }
}
