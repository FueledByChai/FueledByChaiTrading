package com.fueledbychai.datarecording;

import java.math.BigDecimal;

/**
 * A single funding-rate observation for a perpetual instrument, captured by a periodic poll.
 *
 * <h3>Cross-venue normalization</h3>
 * Funding intervals are <em>not</em> uniform: a venue may settle one asset every 8h and a more
 * volatile one every 4h or 1h, and the interval can differ per asset on the same venue (Paradex
 * exposes a real per-asset {@code funding_period_hours}; most others default to 8h). The raw
 * {@code fundingRate} is therefore meaningless to compare across venues without its period — so
 * every row carries {@link #fundingIntervalHours} (from the instrument descriptor) alongside the
 * raw rate. {@link #annualizedRate} is a convenience normalization
 * ({@code fundingRate * (8760 / fundingIntervalHours)}) that <em>assumes the venue reports the
 * rate per its own funding interval</em>; the raw rate + interval are always preserved so a
 * consumer can re-derive any basis (per-8h-equivalent, daily, APR) if a venue's convention
 * differs. {@link #rawJson} keeps the unparsed venue payload as a safety net.
 *
 * @param exchange              venue identifier
 * @param symbol                instrument symbol
 * @param recvTimestampMicros   local poll-receive time (epoch micros)
 * @param eventTimestampMicros  venue-reported funding timestamp (epoch micros), 0 if unknown
 * @param fundingRate           raw funding rate as reported by the venue for its own interval
 * @param fundingIntervalHours  settlement interval for this asset, in hours (0 if unknown)
 * @param nextFundingTimeMicros next scheduled settlement time (epoch micros), 0 if unknown
 * @param markPrice             venue mark price at observation, or {@code null} if not reported
 * @param annualizedRate        convenience normalization (see above), or {@code null} if interval unknown
 * @param rawJson               unparsed venue response payload (safety net), or {@code null}
 */
public record RecordedFundingRate(
        String exchange,
        String symbol,
        long recvTimestampMicros,
        long eventTimestampMicros,
        BigDecimal fundingRate,
        int fundingIntervalHours,
        long nextFundingTimeMicros,
        BigDecimal markPrice,
        BigDecimal annualizedRate,
        String rawJson) {

    private static final BigDecimal HOURS_PER_YEAR = BigDecimal.valueOf(24L * 365L);

    /**
     * Compute {@link #annualizedRate} from a raw rate and interval, assuming the rate is per
     * funding interval. Returns {@code null} when the interval is non-positive (can't normalize)
     * or the rate is {@code null}.
     */
    public static BigDecimal annualize(BigDecimal fundingRate, int fundingIntervalHours) {
        if (fundingRate == null || fundingIntervalHours <= 0) {
            return null;
        }
        return fundingRate.multiply(HOURS_PER_YEAR)
                .divide(BigDecimal.valueOf(fundingIntervalHours), java.math.MathContext.DECIMAL64);
    }
}
