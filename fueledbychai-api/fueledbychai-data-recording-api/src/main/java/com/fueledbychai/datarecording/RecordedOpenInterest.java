package com.fueledbychai.datarecording;

import java.math.BigDecimal;

/**
 * A single open-interest observation for a perpetual instrument, captured by a periodic poll.
 *
 * <p>Open interest (total outstanding contracts) is positioning data you cannot reconstruct from
 * the public trade/book streams, so it must be recorded live. {@link #openInterest} is in the
 * venue's reported units (base/contracts); {@link #openInterestUsd} is the notional value when the
 * venue reports it or it can be derived (oi x mark), else {@code null}. {@link #rawJson} keeps the
 * unparsed payload as a safety net against per-venue shape differences.
 *
 * @param exchange              venue identifier
 * @param symbol                instrument symbol
 * @param recvTimestampMicros   local poll-receive time (epoch micros)
 * @param eventTimestampMicros  venue-reported timestamp (epoch micros), 0 if unknown
 * @param openInterest          open interest in the venue's reported units (base/contracts)
 * @param openInterestUsd       USD notional if reported/derivable, else {@code null}
 * @param rawJson               unparsed venue response payload (safety net), or {@code null}
 */
public record RecordedOpenInterest(
        String exchange,
        String symbol,
        long recvTimestampMicros,
        long eventTimestampMicros,
        BigDecimal openInterest,
        BigDecimal openInterestUsd,
        String rawJson) {
}
