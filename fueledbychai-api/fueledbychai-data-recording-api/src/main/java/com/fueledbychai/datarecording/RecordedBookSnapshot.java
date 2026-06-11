package com.fueledbychai.datarecording;

import java.math.BigDecimal;
import java.util.List;

/**
 * A full top-of-book state snapshot: the top {@code N} levels of each side at a point in
 * time. The native primitive for snapshot-native venues (Hyperliquid, Hibachi), and a
 * periodic resync <i>anchor</i> for delta-native venues (set {@code anchor = true}).
 *
 * <p>Levels are best-first (bids: highest price first; asks: lowest price first).
 *
 * @param exchange             venue identifier (e.g. {@code "PARADEX"})
 * @param symbol               instrument symbol
 * @param recvTimestampMicros  local receive time (epoch micros) when the callback fired
 * @param eventTimestampMicros source/exchange time (epoch micros), 0 if unknown
 * @param exchangeSeq          exchange sequence/update id; {@code null} for snapshot-native venues
 * @param bookEpoch            monotonic contiguous-reconstruction id (see {@link BookEpochSequencer}); 0 if N/A
 * @param anchor               true if this snapshot can seed/resync a delta stream's epoch
 * @param bids                 top bid levels, best (highest) first
 * @param asks                 top ask levels, best (lowest) first
 */
public record RecordedBookSnapshot(
        String exchange,
        String symbol,
        long recvTimestampMicros,
        long eventTimestampMicros,
        Long exchangeSeq,
        long bookEpoch,
        boolean anchor,
        List<Level> bids,
        List<Level> asks) {

    /** A single price level. */
    public record Level(BigDecimal price, double size) {
    }
}
