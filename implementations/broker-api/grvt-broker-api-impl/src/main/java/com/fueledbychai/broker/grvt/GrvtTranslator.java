package com.fueledbychai.broker.grvt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Duration;
import com.fueledbychai.broker.order.OrderTicket.Modifier;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;
import com.fueledbychai.grvt.common.api.model.GrvtOrderLeg;
import com.fueledbychai.grvt.common.api.model.GrvtTimeInForce;

/**
 * Converts FueledByChai {@link OrderTicket}s into GRVT order payloads and maps GRVT order states
 * back to domain {@link OrderStatus.Status} values.
 */
public class GrvtTranslator implements IGrvtTranslator {

    private static final long ORDER_TTL_NANOS = 24L * 60L * 60L * 1_000_000_000L;

    private static final GrvtTranslator INSTANCE = new GrvtTranslator();

    public static GrvtTranslator getInstance() {
        return INSTANCE;
    }

    @Override
    public GrvtOrder toGrvtOrder(OrderTicket order, String subAccountId) {
        if (order == null) {
            throw new IllegalArgumentException("order is required");
        }
        if (order.getTicker() == null || order.getTicker().getSymbol() == null) {
            throw new IllegalArgumentException("order ticker is required");
        }

        Type type = order.getType() == null ? Type.MARKET : order.getType();
        boolean market = type == Type.MARKET;
        BigDecimal limitPrice = market || order.getLimitPrice() == null ? BigDecimal.ZERO : order.getLimitPrice();

        GrvtOrderLeg leg = new GrvtOrderLeg(order.getTicker().getSymbol(), order.getSize(), limitPrice,
                order.isBuyOrder());

        GrvtOrder grvtOrder = new GrvtOrder();
        grvtOrder.setSubAccountId(subAccountId);
        grvtOrder.setMarket(market);
        grvtOrder.setTimeInForce(toTimeInForce(order));
        grvtOrder.setPostOnly(order.containsModifier(Modifier.POST_ONLY));
        grvtOrder.setReduceOnly(order.containsModifier(Modifier.REDUCE_ONLY));
        grvtOrder.setLegs(List.of(leg));
        grvtOrder.setClientOrderId(order.getClientOrderId());
        grvtOrder.getSignature().setNonce(randomUint32());
        grvtOrder.getSignature().setExpiration(Long.toString(expirationNanos()));
        return grvtOrder;
    }

    protected GrvtTimeInForce toTimeInForce(OrderTicket order) {
        if (order.containsModifier(Modifier.RPI)) {
            return GrvtTimeInForce.RETAIL_PRICE_IMPROVEMENT;
        }
        if (order.containsModifier(Modifier.POST_ONLY)) {
            return GrvtTimeInForce.GOOD_TILL_TIME;
        }
        Duration duration = order.getDuration();
        if (duration == Duration.FILL_OR_KILL) {
            return GrvtTimeInForce.FILL_OR_KILL;
        }
        if (duration == Duration.IMMEDIATE_OR_CANCEL) {
            return GrvtTimeInForce.IMMEDIATE_OR_CANCEL;
        }
        return GrvtTimeInForce.GOOD_TILL_TIME;
    }

    @Override
    public OrderStatus.Status toOrderStatus(String grvtStatus, BigDecimal tradedSize, BigDecimal remainingSize) {
        String normalized = grvtStatus == null ? "" : grvtStatus.trim().toUpperCase(Locale.US);
        boolean partiallyFilled = tradedSize != null && tradedSize.signum() > 0;
        switch (normalized) {
            case "PENDING":
                return OrderStatus.Status.NEW;
            case "OPEN":
                return partiallyFilled ? OrderStatus.Status.PARTIAL_FILL : OrderStatus.Status.NEW;
            case "FILLED":
                return OrderStatus.Status.FILLED;
            case "CANCELLED":
            case "CANCELED":
            case "EXPIRED":
                return OrderStatus.Status.CANCELED;
            case "REJECTED":
                return OrderStatus.Status.REJECTED;
            default:
                return OrderStatus.Status.UNKNOWN;
        }
    }

    protected long randomUint32() {
        return ThreadLocalRandom.current().nextLong(0L, 1L << 32);
    }

    protected long expirationNanos() {
        return System.currentTimeMillis() * 1_000_000L + ORDER_TTL_NANOS;
    }
}
