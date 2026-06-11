package com.fueledbychai.marketdata;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

/**
 * A raw, un-throttled order-book update as received from a delta-native venue's feed: either
 * a full-book resync <i>snapshot</i> or an incremental <i>delta</i> message, carrying the
 * exchange sequence number so downstream consumers can detect gaps.
 *
 * <p>This is distinct from {@link OrderBookUpdateListener}, which delivers periodic merged
 * book <i>state</i> with no sequence number. {@code RawBookUpdate} preserves the per-event
 * flow (each insert/update/delete) and the message sequence &mdash; the information needed for
 * order-flow-imbalance, cancel-rate, and gap/epoch tracking that merged snapshots cannot
 * provide. It is emitted only by venues whose exchange feed is genuinely incremental.
 *
 * <p>Two feed shapes are supported. <b>Sequenced full-depth</b> venues (e.g. Paradex) carry a
 * monotonic {@link #getSequence() sequence} and no {@link #getWindow() window}; a gap in the
 * sequence invalidates the reconstruction. <b>Bounded-window</b> venues (e.g. Hibachi) publish
 * only the top-N levels per side with absolute sizes, omit the sequence ({@code -1}), and carry a
 * {@link Window} of per-side price bounds so a consumer can prune levels that scroll out of the
 * window without an explicit delete.
 *
 * <p>For a snapshot, {@link #isSnapshot()} is {@code true} and {@code entries} is the full
 * book, every entry an {@link Action#INSERT}. For a delta, entries are the actual mutations.
 */
public final class RawBookUpdate {

    /** Side of the book an entry belongs to. */
    public enum Side {
        BUY, SELL
    }

    /** The mutation an entry applies. Snapshot entries are always {@link #INSERT}. */
    public enum Action {
        INSERT, UPDATE, DELETE
    }

    /**
     * A single price-level change.
     *
     * @param side   book side
     * @param action insert / update / delete
     * @param price  price level
     * @param size   level size after the change (0 for {@link Action#DELETE})
     */
    public record Entry(Side side, Action action, BigDecimal price, double size) {
    }

    /**
     * The price window a <i>bounded</i> (top-N) book feed reports for this frame. Venues like
     * Hibachi publish only the top {@code depth} levels per side and never send an explicit
     * delete for a level that scrolls out of that window &mdash; so a reconstruction must prune
     * each side to {@code [min(start,end), max(start,end)]} every frame. {@code startPrice} is the
     * touch (best) edge, {@code endPrice} the deep edge; both arrive on every frame even when that
     * side had no level change. {@code null} fields mean the venue did not report a bound (or, on a
     * full-depth venue, that no window applies).
     */
    public record Window(BigDecimal bidStartPrice, BigDecimal bidEndPrice,
                         BigDecimal askStartPrice, BigDecimal askEndPrice) {
    }

    /** Sentinel for "no sequence reported" (used for {@link #getSequence()} and {@link #getPrevSequence()}). */
    public static final long NO_SEQUENCE = -1L;

    private final boolean snapshot;
    private final long sequence;
    private final long prevSequence;
    private final ZonedDateTime timestamp;
    private final List<Entry> entries;
    private final Window window;

    /**
     * Full-depth / sequenced-venue constructor (no window bounds, {@code +1}-style continuity).
     *
     * @param snapshot  true for a full-book resync anchor, false for an incremental delta
     * @param sequence  exchange sequence/update number for this message; negative if the venue omits it
     * @param timestamp source/exchange timestamp for this message
     * @param entries   the level changes (full book for a snapshot, mutations for a delta)
     */
    public RawBookUpdate(boolean snapshot, long sequence, ZonedDateTime timestamp, List<Entry> entries) {
        this(snapshot, sequence, NO_SEQUENCE, timestamp, entries, null);
    }

    /**
     * Bounded-window-venue constructor (un-sequenced; epochs anchor on snapshots).
     *
     * @param snapshot  true for a full-book resync anchor, false for an incremental delta
     * @param sequence  exchange sequence/update number for this message; negative if the venue omits it
     * @param timestamp source/exchange timestamp for this message
     * @param entries   the level changes (full book for a snapshot, mutations for a delta)
     * @param window    per-side top-N price bounds for this frame, or {@code null} if not a windowed feed
     */
    public RawBookUpdate(boolean snapshot, long sequence, ZonedDateTime timestamp, List<Entry> entries, Window window) {
        this(snapshot, sequence, NO_SEQUENCE, timestamp, entries, window);
    }

    /**
     * Canonical constructor.
     *
     * <p>{@code prevSequence} expresses <i>previous-id</i> continuity for venues whose deltas are
     * not strictly {@code +1}: this delta is in-sequence iff {@code prevSequence} equals the last
     * delivered {@code sequence}. Binance futures uses {@code pu}, OKX {@code books} uses
     * {@code prevSeqId}, Binance spot uses {@code firstUpdateId - 1}. Pass {@link #NO_SEQUENCE} for
     * {@code +1}-style (Paradex) or un-sequenced (Hibachi) feeds.
     *
     * @param snapshot     true for a full-book resync anchor, false for an incremental delta
     * @param sequence     exchange final-update sequence for this message; {@link #NO_SEQUENCE} if omitted
     * @param prevSequence the {@code sequence} this delta must follow, or {@link #NO_SEQUENCE} if N/A
     * @param timestamp    source/exchange timestamp for this message
     * @param entries      the level changes (full book for a snapshot, mutations for a delta)
     * @param window       per-side top-N price bounds for this frame, or {@code null} if not a windowed feed
     */
    public RawBookUpdate(boolean snapshot, long sequence, long prevSequence, ZonedDateTime timestamp,
            List<Entry> entries, Window window) {
        this.snapshot = snapshot;
        this.sequence = sequence;
        this.prevSequence = prevSequence;
        this.timestamp = timestamp;
        this.entries = entries == null ? Collections.emptyList() : entries;
        this.window = window;
    }

    public boolean isSnapshot() {
        return snapshot;
    }

    public long getSequence() {
        return sequence;
    }

    /**
     * The {@code sequence} this delta must immediately follow for prev-id-continuity venues
     * (Binance {@code pu}, OKX {@code prevSeqId}, Binance-spot {@code U-1}); {@link #NO_SEQUENCE}
     * for {@code +1}-style or un-sequenced feeds.
     */
    public long getPrevSequence() {
        return prevSequence;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /** Per-side top-N price bounds for this frame, or {@code null} for a full-depth (non-windowed) feed. */
    public Window getWindow() {
        return window;
    }
}
