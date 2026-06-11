package com.fueledbychai.datarecording;

import java.math.BigDecimal;

/**
 * A single incremental order-book delta from a delta-native venue: one price level was
 * added, changed, or deleted. This is the primitive that carries flow/intensity information
 * (OFI, cancel-rate, book churn) that full snapshots cannot &mdash; offsetting adds and
 * cancels between two snapshots net to nothing, but each is a distinct {@code RecordedBookEvent}.
 *
 * <p>Sequenced venues feed {@code exchangeSeq} to {@link BookEpochSequencer} to detect gaps and
 * stamp {@code bookEpoch}. Bounded-window venues (e.g. Hibachi) instead leave {@code exchangeSeq}
 * {@code null} and carry {@code windowStartPrice}/{@code windowEndPrice} &mdash; the top-N price
 * bounds this side reported on the frame &mdash; so the offline reconstruction can prune levels
 * that scrolled out of the window without an explicit delete (best/touch = start, deep edge = end).
 *
 * @param exchange             venue identifier
 * @param symbol               instrument symbol
 * @param recvTimestampMicros  local receive time (epoch micros)
 * @param eventTimestampMicros source/exchange time (epoch micros), 0 if unknown
 * @param exchangeSeq          exchange sequence/update id for this event ({@code null} if the venue omits it)
 * @param prevExchangeSeq      the {@code exchangeSeq} this event must follow for prev-id-continuity
 *                             venues (Binance {@code pu}, OKX {@code prevSeqId}); {@code null} for
 *                             {@code +1}-style or un-sequenced feeds
 * @param bookEpoch            contiguous-reconstruction id this event belongs to
 * @param side                 which side of the book
 * @param price                the affected price level
 * @param newSize              the level's size after the mutation (0 for {@link EventAction#DELETE})
 * @param action               add / change / delete
 * @param windowStartPrice     this side's window touch/best edge on this frame ({@code null} for full-depth venues)
 * @param windowEndPrice       this side's window deep edge on this frame ({@code null} for full-depth venues)
 */
public record RecordedBookEvent(
        String exchange,
        String symbol,
        long recvTimestampMicros,
        long eventTimestampMicros,
        Long exchangeSeq,
        Long prevExchangeSeq,
        long bookEpoch,
        BookSide side,
        BigDecimal price,
        double newSize,
        EventAction action,
        BigDecimal windowStartPrice,
        BigDecimal windowEndPrice) {
}
