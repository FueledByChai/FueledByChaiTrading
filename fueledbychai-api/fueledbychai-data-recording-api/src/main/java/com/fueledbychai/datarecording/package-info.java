/**
 * Contract for recording raw market data and own trading events to durable storage
 * for offline feature research, regression, and backtesting.
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li><b>Record the rawest primitive each venue emits.</b> Delta-native venues
 *       (Paradex, Deribit, OKX, Bybit, Lighter) emit incremental book events with a
 *       sequence number &mdash; capture them as {@link com.fueledbychai.datarecording.RecordedBookEvent}.
 *       Snapshot-native venues (Hyperliquid, Hibachi) only ever emit full book state
 *       &mdash; capture {@link com.fueledbychai.datarecording.RecordedBookSnapshot}. Do not
 *       force a uniform model; record what the exchange actually sends. Snapshots and
 *       (where available) deltas are both kept &mdash; the snapshot doubles as a resync
 *       <i>anchor</i> for the delta stream.</li>
 *   <li><b>Capture, don't pre-aggregate.</b> Features (OBI, microprice, OFI,
 *       cancel-rate, markout) are derived downstream from these raw rows. A new feature
 *       must never require a recorder change &mdash; that is what prevents data holes.</li>
 *   <li><b>The trade tape is universal.</b> Every venue has a trades channel; it carries
 *       the validated public-trade-markout toxicity signal and is independent of the book
 *       feed type. Always capture {@link com.fueledbychai.datarecording.RecordedTrade}.</li>
 *   <li><b>Own fills join with market data.</b> The trading app emits its own fills in this
 *       same schema ({@link com.fueledbychai.datarecording.RecordedOwnEvent}) so they join
 *       cleanly against the recorder's market data without re-authenticating the account.</li>
 * </ul>
 *
 * <h2>Sequence gaps and the book_epoch / book_valid contract</h2>
 * A dropped or out-of-order delta corrupts the reconstructed book until the next snapshot
 * anchor. {@link com.fueledbychai.datarecording.BookEpochSequencer} detects this from the
 * exchange sequence number and stamps a monotonic {@code bookEpoch} plus a validity flag, so
 * downstream consumers exclude corrupted spans and never compute a windowed feature across an
 * epoch boundary (a re-anchor must not masquerade as a flow spike). This applies only to
 * delta-native venues that carry a sequence number; snapshot-native capture leaves
 * {@code exchangeSeq == null} and {@code bookEpoch == 0}.
 *
 * <h2>Time</h2>
 * All timestamps are epoch microseconds. Each record carries both the source/exchange
 * {@code eventTimestampMicros} and the local {@code recvTimestampMicros} stamped when the
 * feed callback fired &mdash; the gap between them is itself a latency signal.
 */
package com.fueledbychai.datarecording;
