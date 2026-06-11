package com.fueledbychai.datarecording;

/**
 * Tracks exchange-sequence continuity for a single {@code (exchange, symbol)} delta stream
 * and assigns a {@code bookEpoch} + validity flag, so a dropped or out-of-order delta does
 * not silently corrupt downstream feature computation.
 *
 * <p>Semantics:
 * <ul>
 *   <li>A reconstruction is valid only after an {@link #anchor(long) anchor} (a full snapshot).
 *       Each anchor starts a new, contiguous {@code epoch}.</li>
 *   <li>{@link #delta(long)} is in-sequence iff its sequence is exactly {@code lastSeq + 1}.
 *       A gap (or a delta arriving before any anchor) marks the stream <b>invalid</b> until
 *       the next anchor; the book must not be trusted in that span.</li>
 * </ul>
 *
 * <p>Not thread-safe; hold one instance per stream, mutated on that stream's feed thread.
 * This is the same logic the normalization layer applies offline, so recorder and backtest
 * agree on epoch boundaries. Reset windowed features (OFI, cancel-rate, EWMAs) whenever
 * {@link #epoch()} changes &mdash; a re-anchor must not look like a flow spike.
 */
public final class BookEpochSequencer {

    /** Outcome of applying a sequence number. */
    public record Result(long epoch, boolean valid, boolean gap) {
    }

    private long epoch = 0L;
    private long lastSeq = Long.MIN_VALUE;
    private boolean valid = false;

    /**
     * Apply a full-snapshot resync. Starts a new epoch and marks the stream valid.
     *
     * @param seq the sequence number the snapshot is current as of
     * @return the new epoch (valid, no gap)
     */
    public Result anchor(long seq) {
        epoch++;
        lastSeq = seq;
        valid = true;
        return new Result(epoch, true, false);
    }

    /**
     * Apply an incremental delta's sequence number.
     *
     * @param seq the delta's exchange sequence number
     * @return current epoch + validity; {@code gap == true} on the delta that first breaks
     *         continuity (which also flips the stream to invalid until the next anchor)
     */
    public Result delta(long seq) {
        if (!valid) {
            // No valid anchor yet, or already gapped — stays invalid until re-anchored.
            return new Result(epoch, false, false);
        }
        if (lastSeq != Long.MIN_VALUE && seq != lastSeq + 1) {
            valid = false;
            return new Result(epoch, false, true);
        }
        lastSeq = seq;
        return new Result(epoch, true, false);
    }

    /**
     * Apply a delta whose continuity is expressed by a <i>previous-id</i> rather than {@code +1}.
     * This delta is in-sequence iff {@code prevSeq} equals the last delivered {@code seq}. Venues:
     * Binance futures ({@code pu == last u}), OKX {@code books} ({@code prevSeqId == last seqId}),
     * Binance spot ({@code firstUpdateId - 1 == last finalUpdateId}). The first delta after an
     * {@link #anchor(long)} is accepted unconditionally (no prior {@code seq} to match yet) and
     * seeds {@code lastSeq}; thereafter a mismatch marks the stream invalid until re-anchor.
     *
     * @param seq     this delta's final sequence id (becomes the new {@code lastSeq})
     * @param prevSeq the sequence id this delta must follow
     * @return current epoch + validity; {@code gap == true} on the delta that first breaks continuity
     */
    public Result delta(long seq, long prevSeq) {
        if (!valid) {
            return new Result(epoch, false, false);
        }
        if (lastSeq != Long.MIN_VALUE && prevSeq != lastSeq) {
            valid = false;
            return new Result(epoch, false, true);
        }
        lastSeq = seq;
        return new Result(epoch, true, false);
    }

    public long epoch() {
        return epoch;
    }

    public boolean isValid() {
        return valid;
    }

    public long lastSeq() {
        return lastSeq;
    }
}
