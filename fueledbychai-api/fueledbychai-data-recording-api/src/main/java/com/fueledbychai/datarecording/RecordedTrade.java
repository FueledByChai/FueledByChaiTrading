package com.fueledbychai.datarecording;

import java.math.BigDecimal;

/**
 * A single public trade printed to the tape. Universal across venues and the source of the
 * validated public-trade-markout toxicity signal. {@code aggressor} is the taker side
 * (which resting side was lifted/hit); {@link Side#UNKNOWN} when the venue does not report it.
 *
 * @param exchange             venue identifier
 * @param symbol               instrument symbol
 * @param recvTimestampMicros  local receive time (epoch micros)
 * @param eventTimestampMicros source/exchange time (epoch micros), 0 if unknown
 * @param exchangeSeq          exchange trade id/sequence if available, else {@code null}
 * @param price                trade price
 * @param size                 trade size
 * @param aggressor            taker side (BUY = lifted ask, SELL = hit bid), or UNKNOWN
 */
public record RecordedTrade(
        String exchange,
        String symbol,
        long recvTimestampMicros,
        long eventTimestampMicros,
        Long exchangeSeq,
        BigDecimal price,
        double size,
        Side aggressor) {
}
