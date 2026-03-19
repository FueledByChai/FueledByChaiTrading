package com.fueledbychai.broker.drift;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Modifier;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.drift.common.api.model.DriftGatewayOrder;
import com.fueledbychai.drift.common.api.model.DriftGatewayOrderRequest;
import com.fueledbychai.drift.common.api.model.DriftGatewayPosition;
import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.util.ITickerRegistry;
import com.google.gson.JsonObject;

public class DriftTranslator {

    protected final ITickerRegistry tickerRegistry;

    public DriftTranslator(ITickerRegistry tickerRegistry) {
        if (tickerRegistry == null) {
            throw new IllegalArgumentException("tickerRegistry is required");
        }
        this.tickerRegistry = tickerRegistry;
    }

    public DriftGatewayOrderRequest toGatewayOrderRequest(OrderTicket order, Integer userOrderId, Long orderId) {
        validateOrder(order);
        DriftMarketType marketType = toMarketType(order.getTicker());
        BigDecimal signedAmount = signedAmount(order);
        String orderType = toGatewayOrderType(order);
        Long maxTs = order.getGoodUntilTime() == null ? null : order.getGoodUntilTime().toEpochSecond();
        return new DriftGatewayOrderRequest(order.getTicker().getIdAsInt(), marketType, signedAmount,
                order.getLimitPrice(), order.containsModifier(Modifier.POST_ONLY), orderType, userOrderId,
                order.containsModifier(Modifier.REDUCE_ONLY), maxTs, orderId, null);
    }

    public DriftGatewayOrder toGatewayOrder(JsonObject payload) {
        if (payload == null) {
            return null;
        }
        return new DriftGatewayOrder(getString(payload, "orderType"), getInt(payload, "marketIndex", 0),
                DriftMarketType.fromString(getString(payload, "marketType")), getBigDecimal(payload, "amount"),
                getBigDecimal(payload, "filled"), getBigDecimal(payload, "price"), getBoolean(payload, "postOnly"),
                getBoolean(payload, "reduceOnly"), getInteger(payload, "userOrderId"), getLongObject(payload, "orderId"),
                getBigDecimal(payload, "oraclePriceOffset"),
                firstNonBlank(getString(payload, "direction"), getString(payload, "side")));
    }

    public OrderTicket toOrderTicket(DriftGatewayOrder gatewayOrder, String clientOrderId) {
        if (gatewayOrder == null) {
            return null;
        }
        Ticker ticker = resolveTicker(gatewayOrder.getMarketType(), gatewayOrder.getMarketIndex());
        OrderTicket ticket = new OrderTicket();
        ticket.setTicker(ticker);
        ticket.setClientOrderId(clientOrderId);
        ticket.setOrderId(gatewayOrder.getOrderId() == null ? null : String.valueOf(gatewayOrder.getOrderId()));
        ticket.setDirection(resolveDirection(gatewayOrder.getAmount(), gatewayOrder.getDirection()));
        ticket.setSize(gatewayOrder.getAmount() == null ? BigDecimal.ZERO : gatewayOrder.getAmount().abs());
        ticket.setFilledSize(gatewayOrder.getFilled() == null ? BigDecimal.ZERO : gatewayOrder.getFilled().abs());
        ticket.setLimitPrice(gatewayOrder.getPrice());
        ticket.setType(fromGatewayOrderType(gatewayOrder.getOrderType()));
        if (gatewayOrder.isPostOnly()) {
            ticket.addModifier(Modifier.POST_ONLY);
        }
        if (gatewayOrder.isReduceOnly()) {
            ticket.addModifier(Modifier.REDUCE_ONLY);
        }
        BigDecimal filled = gatewayOrder.getFilled() == null ? BigDecimal.ZERO : gatewayOrder.getFilled().abs();
        ticket.setCurrentStatus(
                filled.compareTo(BigDecimal.ZERO) > 0 ? OrderStatus.Status.PARTIAL_FILL : OrderStatus.Status.NEW);
        return ticket;
    }

    public Position toPosition(DriftGatewayPosition gatewayPosition) {
        if (gatewayPosition == null) {
            return null;
        }
        Ticker ticker = resolveTicker(gatewayPosition.getMarketType(), gatewayPosition.getMarketIndex());
        BigDecimal amount = gatewayPosition.getAmount() == null ? BigDecimal.ZERO : gatewayPosition.getAmount();
        Side side = amount.signum() >= 0 ? Side.LONG : Side.SHORT;
        return new Position(ticker, side, amount.abs(),
                gatewayPosition.getAverageEntry() == null ? BigDecimal.ZERO : gatewayPosition.getAverageEntry(),
                Position.Status.OPEN).setLiquidationPrice(gatewayPosition.getLiquidationPrice()).setSide(side);
    }

    public Fill toFill(JsonObject payload, String clientOrderId) {
        if (payload == null) {
            return null;
        }
        DriftMarketType marketType = DriftMarketType.fromString(getString(payload, "marketType"));
        int marketIndex = getInt(payload, "marketIndex", 0);
        Ticker ticker = resolveTicker(marketType, marketIndex);

        String orderId = firstNonBlank(getString(payload, "orderId"), getString(payload, "takerOrderId"),
                getString(payload, "makerOrderId"));
        String signature = firstNonBlank(getString(payload, "signature"), getString(payload, "txSig"));
        String eventIndex = firstNonBlank(getString(payload, "txIdx"), getString(payload, "fillRecordId"));
        String side = firstNonBlank(getString(payload, "side"), getString(payload, "direction"));

        Fill fill = new Fill();
        fill.setTicker(ticker);
        fill.setPrice(getBigDecimal(payload, "price"));
        fill.setSize(abs(getBigDecimal(payload, "amount")));
        fill.setCommission(getBigDecimal(payload, "fee"));
        fill.setOrderId(orderId);
        fill.setClientOrderId(clientOrderId);
        fill.setFillId(buildFillId(signature, eventIndex, orderId, marketIndex));
        fill.setTime(resolveTimestamp(payload));
        fill.setTaker(resolveTaker(payload, orderId));
        fill.setSide(resolveDirection(getBigDecimal(payload, "amount"), side));
        return fill;
    }

    protected void validateOrder(OrderTicket order) {
        if (order == null) {
            throw new IllegalArgumentException("order is required");
        }
        if (order.getTicker() == null) {
            throw new IllegalArgumentException("order ticker is required");
        }
        if (order.getSize() == null || order.getSize().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("order size must be positive");
        }
        if (order.getType() == OrderTicket.Type.LIMIT && order.getLimitPrice() == null) {
            throw new IllegalArgumentException("limit orders require limitPrice");
        }
        if (order.getType() != OrderTicket.Type.LIMIT && order.getType() != OrderTicket.Type.MARKET) {
            throw new UnsupportedOperationException(
                    "Drift gateway currently supports market and limit orders in this implementation");
        }
    }

    protected DriftMarketType toMarketType(Ticker ticker) {
        return ticker.getInstrumentType() == InstrumentType.CRYPTO_SPOT ? DriftMarketType.SPOT : DriftMarketType.PERP;
    }

    protected BigDecimal signedAmount(OrderTicket order) {
        BigDecimal size = order.getSize();
        return order.isBuyOrder() ? size : size.negate();
    }

    protected String toGatewayOrderType(OrderTicket order) {
        return switch (order.getType()) {
        case MARKET -> "market";
        case LIMIT -> "limit";
        default -> throw new UnsupportedOperationException("Unsupported Drift order type: " + order.getType());
        };
    }

    protected OrderTicket.Type fromGatewayOrderType(String orderType) {
        if (orderType == null) {
            return OrderTicket.Type.LIMIT;
        }
        return switch (orderType) {
        case "market" -> OrderTicket.Type.MARKET;
        default -> OrderTicket.Type.LIMIT;
        };
    }

    protected TradeDirection resolveDirection(BigDecimal signedAmount, String direction) {
        if (direction != null && !direction.isBlank()) {
            String normalized = direction.toLowerCase();
            if ("long".equals(normalized) || "buy".equals(normalized)) {
                return TradeDirection.BUY;
            }
            if ("short".equals(normalized) || "sell".equals(normalized)) {
                return TradeDirection.SELL;
            }
        }
        return signedAmount == null || signedAmount.signum() >= 0 ? TradeDirection.BUY : TradeDirection.SELL;
    }

    protected ZonedDateTime resolveTimestamp(JsonObject payload) {
        if (payload.has("ts") && !payload.get("ts").isJsonNull()) {
            long rawTs = payload.get("ts").getAsLong();
            Instant instant = rawTs >= 1_000_000_000_000L ? Instant.ofEpochMilli(rawTs) : Instant.ofEpochSecond(rawTs);
            return ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"));
        }
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    protected Ticker resolveTicker(DriftMarketType marketType, int marketIndex) {
        InstrumentType instrumentType = marketType == DriftMarketType.SPOT ? InstrumentType.CRYPTO_SPOT
                : InstrumentType.PERPETUAL_FUTURES;
        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(instrumentType, String.valueOf(marketIndex));
        if (ticker == null) {
            throw new IllegalStateException(
                    "Unable to resolve Drift ticker for " + marketType + " marketIndex " + marketIndex);
        }
        return ticker;
    }

    protected String getString(JsonObject object, String field) {
        if (object == null || field == null || !object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        return object.get(field).getAsString();
    }

    protected int getInt(JsonObject object, String field, int defaultValue) {
        if (object == null || field == null || !object.has(field) || object.get(field).isJsonNull()) {
            return defaultValue;
        }
        return object.get(field).getAsInt();
    }

    protected Integer getInteger(JsonObject object, String field) {
        if (object == null || field == null || !object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        return object.get(field).getAsInt();
    }

    protected Long getLongObject(JsonObject object, String field) {
        if (object == null || field == null || !object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        return object.get(field).getAsLong();
    }

    protected boolean getBoolean(JsonObject object, String field) {
        return object != null && field != null && object.has(field) && !object.get(field).isJsonNull()
                && object.get(field).getAsBoolean();
    }

    protected BigDecimal getBigDecimal(JsonObject object, String field) {
        String value = getString(object, field);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    protected BigDecimal abs(BigDecimal value) {
        return value == null ? null : value.abs();
    }

    protected String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    protected String buildFillId(String signature, String eventIndex, String orderId, int marketIndex) {
        if (signature == null || signature.isBlank()) {
            return marketIndex + ":" + firstNonBlank(orderId, eventIndex, String.valueOf(System.nanoTime()));
        }
        String suffix = firstNonBlank(eventIndex, orderId);
        return suffix == null ? signature : signature + ":" + suffix;
    }

    protected boolean resolveTaker(JsonObject payload, String orderId) {
        if (payload == null) {
            return false;
        }
        if (payload.has("taker") && !payload.get("taker").isJsonNull()) {
            try {
                return payload.get("taker").getAsBoolean();
            } catch (RuntimeException ex) {
                // Gateway payloads identify taker fills by order ids instead of a boolean.
            }
        }
        String takerOrderId = getString(payload, "takerOrderId");
        return orderId != null && orderId.equals(takerOrderId);
    }
}
