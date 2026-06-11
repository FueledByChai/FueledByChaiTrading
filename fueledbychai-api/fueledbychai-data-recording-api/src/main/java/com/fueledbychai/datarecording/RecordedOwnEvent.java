package com.fueledbychai.datarecording;

import java.math.BigDecimal;

/**
 * An own order/fill lifecycle event, written by the trading app in the same schema as the
 * market data so fills join cleanly against book/trade history for markout analysis.
 *
 * @param exchange             venue identifier
 * @param symbol               instrument symbol
 * @param recvTimestampMicros  local receive time (epoch micros)
 * @param eventTimestampMicros source/exchange time (epoch micros), 0 if unknown
 * @param category             normalized lifecycle category
 * @param rawStatus            raw broker/venue status string, preserved verbatim ({@code null} if N/A)
 * @param clientOrderId        our client order id
 * @param exchangeOrderId      venue order id ({@code null} if not yet assigned)
 * @param fillId               venue fill/execution id (fills only; {@code null} otherwise)
 * @param side                 our order side
 * @param price                limit/fill price ({@code null} if N/A)
 * @param size                 order/fill size
 * @param commission           fee/commission ({@code null} if unknown)
 * @param taker                true if this execution removed liquidity (taker), false maker, {@code null} if N/A
 */
public record RecordedOwnEvent(
        String exchange,
        String symbol,
        long recvTimestampMicros,
        long eventTimestampMicros,
        OwnEventCategory category,
        String rawStatus,
        String clientOrderId,
        String exchangeOrderId,
        String fillId,
        Side side,
        BigDecimal price,
        double size,
        BigDecimal commission,
        Boolean taker) {
}
