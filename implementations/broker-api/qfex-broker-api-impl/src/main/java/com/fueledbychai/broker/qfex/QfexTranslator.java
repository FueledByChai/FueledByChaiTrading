package com.fueledbychai.broker.qfex;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.qfex.common.api.ws.QfexStream;

/**
 * Maps FueledByChai orders to QFEX trade-socket messages and back.
 *
 * <p>Order types: a POST_ONLY limit is QFEX's {@code ALO} type (not a flag);
 * STOP becomes {@code STOP_LOSS}, whose {@code price} is the trigger checked
 * against mark price and which fires as a market order. Reduce-only is a
 * separate boolean, so unlike Hibachi it combines with post-only.
 * STOP_LIMIT has no QFEX equivalent.
 */
final class QfexTranslator {

    static final String ALO = "ALO";
    static final String LIMIT = "LIMIT";
    static final String MARKET = "MARKET";
    static final String STOP_LOSS = "STOP_LOSS";

    private static final Set<String> CANCELLED = Set.of("CANCELLED", "CANCELLED_STP", "IOC_CANCELLED");

    private QfexTranslator() {
    }

    static ObjectNode addOrder(OrderTicket o) {
        String type = orderType(o);
        ObjectNode msg = message("add_order");
        ObjectNode p = (ObjectNode) msg.get("params");
        p.put("symbol", o.getTicker().getSymbol());
        p.put("side", side(o));
        p.put("order_type", type);
        p.put("order_time_in_force", timeInForce(o, type));
        p.put("quantity", requirePositive(o.getSize(), "size"));
        p.put("price", price(o, type));
        p.put("take_profit", 0);
        p.put("stop_loss", 0);
        p.put("reduce_only", has(o, OrderTicket.Modifier.REDUCE_ONLY));
        if (o.getClientOrderId() != null && !o.getClientOrderId().isBlank()) {
            p.put("client_order_id", o.getClientOrderId());
        }
        return msg;
    }

    static ObjectNode modifyOrder(OrderTicket o) {
        String type = orderType(o);
        ObjectNode msg = message("modify_order");
        ObjectNode p = (ObjectNode) msg.get("params");
        p.put("order_id", o.getOrderId());
        p.put("symbol", o.getTicker().getSymbol());
        p.put("side", side(o));
        p.put("order_type", type);
        // Required since 2026-09-09 and must match the resting order.
        p.put("reduce_only", has(o, OrderTicket.Modifier.REDUCE_ONLY));
        p.put("quantity", requirePositive(o.getSize(), "size"));
        p.put("price", price(o, type));
        p.put("take_profit", 0);
        p.put("stop_loss", 0);
        return msg;
    }

    static ObjectNode cancelOrder(OrderTicket o) {
        if (isStop(o)) {
            ObjectNode msg = message("cancel_stop_order");
            ((ObjectNode) msg.get("params")).put("stop_order_id", o.getOrderId());
            return msg;
        }
        ObjectNode msg = message("cancel_order");
        ObjectNode p = (ObjectNode) msg.get("params");
        p.put("order_id", o.getOrderId());
        p.put("symbol", o.getTicker().getSymbol());
        p.put("cancel_order_id_type", "order_id");
        return msg;
    }

    static ObjectNode cancelAll(String symbolOrNull) {
        ObjectNode msg = message("cancel_all_orders");
        if (symbolOrNull != null) {
            ((ObjectNode) msg.get("params")).put("symbol", symbolOrNull);
        }
        return msg;
    }

    static ObjectNode getUserOrders(int limit, int offset) {
        ObjectNode msg = message("get_user_orders");
        ((ObjectNode) msg.get("params")).put("limit", limit).put("offset", offset);
        return msg;
    }

    static String orderType(OrderTicket o) {
        OrderTicket.Type t = o.getType() == null ? OrderTicket.Type.MARKET : o.getType();
        return switch (t) {
            case LIMIT -> has(o, OrderTicket.Modifier.POST_ONLY) ? ALO : LIMIT;
            case STOP -> STOP_LOSS;
            case MARKET -> MARKET;
            default -> throw new IllegalArgumentException("QFEX does not support " + t + " orders");
        };
    }

    static String timeInForce(OrderTicket o, String type) {
        if (MARKET.equals(type)) {
            return "IOC";
        }
        if (ALO.equals(type) || STOP_LOSS.equals(type)) {
            return "GTC";
        }
        if (o.getDuration() == OrderTicket.Duration.IMMEDIATE_OR_CANCEL) {
            return "IOC";
        }
        if (o.getDuration() == OrderTicket.Duration.FILL_OR_KILL) {
            return "FOK";
        }
        return "GTC";
    }

    static boolean isStop(OrderTicket o) {
        return o.getType() == OrderTicket.Type.STOP;
    }

    static String side(OrderTicket o) {
        TradeDirection d = o.getTradeDirection();
        if (d == null) {
            throw new IllegalArgumentException("trade direction is required");
        }
        return d == TradeDirection.BUY || d == TradeDirection.BUY_TO_COVER ? "BUY" : "SELL";
    }

    private static BigDecimal price(OrderTicket o, String type) {
        return switch (type) {
            case LIMIT, ALO -> requirePositive(o.getLimitPrice(), "limitPrice");
            case STOP_LOSS -> requirePositive(o.getStopPrice(), "stopPrice");
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * QFEX order status to FueledByChai status. {@code FILLED} can be a partial
     * fill; the remaining quantity decides. Every venue-specific reject code
     * maps to REJECTED.
     */
    static OrderStatus.Status toStatus(String status, BigDecimal remaining) {
        if (status == null) {
            return OrderStatus.Status.UNKNOWN;
        }
        if (CANCELLED.contains(status)) {
            return OrderStatus.Status.CANCELED;
        }
        return switch (status) {
            case "ACK", "MODIFIED" -> OrderStatus.Status.NEW;
            case "FILLED" -> remaining != null && remaining.signum() > 0
                    ? OrderStatus.Status.PARTIAL_FILL : OrderStatus.Status.FILLED;
            case "IOC_PARTIALLY_FILLED" -> OrderStatus.Status.CANCELED;
            default -> OrderStatus.Status.REJECTED;
        };
    }

    /** Statuses that mean the request was accepted (the order exists or already executed). */
    static boolean isAccepted(String status) {
        return List.of("ACK", "MODIFIED", "FILLED", "IOC_PARTIALLY_FILLED", "IOC_CANCELLED").contains(status);
    }

    static Fill toFill(JsonNode f, Ticker ticker) {
        Fill fill = new Fill();
        fill.setTicker(ticker);
        fill.setOrderId(text(f, "order_id"));
        String clientId = text(f, "client_order_id");
        fill.setClientOrderId(clientId == null || clientId.isBlank() ? null : clientId);
        fill.setFillId(text(f, "trade_id"));
        fill.setPrice(decimal(f, "price"));
        fill.setSize(decimal(f, "quantity"));
        fill.setCommission(decimal(f, "fee"));
        fill.setRealizedPnl(decimal(f, "realised_pnl"));
        String side = text(f, "side");
        fill.setSide("SELL".equalsIgnoreCase(side) ? TradeDirection.SELL : TradeDirection.BUY);
        String aggressor = text(f, "aggressor_side");
        fill.setTaker(aggressor != null && aggressor.equalsIgnoreCase(side));
        fill.setTime(epochSeconds(f.path("timestamp")));
        return fill;
    }

    /** An open order from {@code get_user_orders} as a ticket. */
    static OrderTicket toTicket(JsonNode n, Ticker ticker) {
        OrderTicket t = new OrderTicket();
        t.setTicker(ticker);
        t.setOrderId(text(n, "order_id"));
        String clientId = text(n, "client_order_id");
        if (clientId != null && !clientId.isBlank()) {
            t.setClientOrderId(clientId);
        }
        t.setTradeDirection("SELL".equalsIgnoreCase(text(n, "side")) ? TradeDirection.SELL : TradeDirection.BUY);
        String type = text(n, "type");
        BigDecimal price = decimal(n, "price");
        if (STOP_LOSS.equals(type) || "TAKE_PROFIT".equals(type) || "STOP_MARKET".equals(type)) {
            t.setType(OrderTicket.Type.STOP);
            t.setStopPrice(price);
        } else if (MARKET.equals(type)) {
            t.setType(OrderTicket.Type.MARKET);
        } else {
            t.setType(OrderTicket.Type.LIMIT);
            t.setLimitPrice(price);
            if (ALO.equals(type)) {
                t.addModifier(OrderTicket.Modifier.POST_ONLY);
            }
        }
        BigDecimal qty = decimal(n, "quantity");
        BigDecimal remaining = decimal(n, "quantity_remaining");
        if (qty != null) {
            t.setSize(qty);
            if (remaining != null) {
                t.setFilledSize(qty.subtract(remaining).max(BigDecimal.ZERO));
            }
        }
        t.setCurrentStatus(OrderStatus.Status.NEW);
        return t;
    }

    static ZonedDateTime epochSeconds(JsonNode ts) {
        if (ts == null || ts.isMissingNode() || ts.isNull()) {
            return ZonedDateTime.now(ZoneOffset.UTC);
        }
        if (ts.isNumber()) {
            long micros = Math.round(ts.asDouble() * 1_000_000d);
            return Instant.ofEpochSecond(micros / 1_000_000, (micros % 1_000_000) * 1_000).atZone(ZoneOffset.UTC);
        }
        try {
            return Instant.parse(ts.asText()).atZone(ZoneOffset.UTC);
        } catch (RuntimeException e) {
            return ZonedDateTime.now(ZoneOffset.UTC);
        }
    }

    static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    static BigDecimal decimal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.asText()).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean has(OrderTicket o, OrderTicket.Modifier m) {
        return o.getModifiers() != null && o.getModifiers().contains(m);
    }

    private static BigDecimal requirePositive(BigDecimal v, String what) {
        if (v == null || v.signum() <= 0) {
            throw new IllegalArgumentException(what + " must be > 0");
        }
        return v;
    }

    private static ObjectNode message(String type) {
        ObjectNode msg = QfexStream.MAPPER.createObjectNode();
        msg.put("type", type);
        msg.putObject("params");
        return msg;
    }
}
