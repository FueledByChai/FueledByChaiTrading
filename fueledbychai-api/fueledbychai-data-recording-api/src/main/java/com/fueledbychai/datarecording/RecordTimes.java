package com.fueledbychai.datarecording;

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Epoch-microsecond time helpers for the recording contract. All persisted timestamps are
 * microseconds since the Unix epoch.
 */
public final class RecordTimes {

    private RecordTimes() {
    }

    /** Local wall-clock now, in epoch microseconds. Stamp this when a feed callback fires. */
    public static long nowMicros() {
        return micros(Instant.now());
    }

    /** Convert a {@link ZonedDateTime} (e.g. a feed event timestamp) to epoch microseconds; 0 if null. */
    public static long micros(ZonedDateTime zdt) {
        return zdt == null ? 0L : micros(zdt.toInstant());
    }

    /** Convert an {@link Instant} to epoch microseconds, preserving sub-millisecond precision. */
    public static long micros(Instant instant) {
        if (instant == null) {
            return 0L;
        }
        return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
    }
}
