package com.fueledbychai.datarecording;

/**
 * Sink that persists recorded market-data and own-trading rows to durable storage. The
 * contract implemented by storage backends (e.g. a partitioned-parquet writer under
 * {@code implementations/data-recording-api}).
 *
 * <p>Implementations are expected to be called from feed/callback threads and should be
 * non-blocking for the caller &mdash; typically by handing rows to a bounded queue drained
 * by a writer pool. The {@code record*} methods must be safe to call concurrently from
 * multiple feed threads.
 */
public interface MarketDataRecorder extends AutoCloseable {

    /** Persist a full book-state snapshot (or resync anchor). */
    void recordBookSnapshot(RecordedBookSnapshot snapshot);

    /** Persist a single incremental book delta (delta-native venues). */
    void recordBookEvent(RecordedBookEvent event);

    /** Persist a single public trade. */
    void recordTrade(RecordedTrade trade);

    /** Persist a single own order/fill lifecycle event. */
    void recordOwnEvent(RecordedOwnEvent event);

    /**
     * Persist a single funding-rate observation. Defaults to a no-op so existing sinks remain
     * source-compatible; backends that record funding override this.
     */
    default void recordFunding(RecordedFundingRate funding) {
        // no-op by default
    }

    /** Flush any buffered rows to durable storage. Best-effort; may be a no-op. */
    void flush();

    /** Flush and release resources. After close, further {@code record*} calls are illegal. */
    @Override
    void close();
}
