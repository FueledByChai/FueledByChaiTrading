package com.fueledbychai.broker.lighter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.account.LighterPosition;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterOrderType;
import com.fueledbychai.lighter.common.api.order.LighterTimeInForce;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;
import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class LighterTranslator implements ILighterTranslator {

    protected static final Logger logger = LoggerFactory.getLogger(LighterTranslator.class);
    protected static final ZoneId UTC = ZoneId.of("UTC");
    protected static final InstrumentType[] SUPPORTED_TYPES = new InstrumentType[] {
            InstrumentType.PERPETUAL_FUTURES, InstrumentType.CRYPTO_SPOT
    };

    protected static ITickerRegistry tickerRegistry;
    protected static ILighterTranslator instance;

    public static ILighterTranslator getInstance() {
        if (instance == null) {
            instance = new LighterTranslator();
        }
        return instance;
    }

    protected static ITickerRegistry getTickerRegistry() {
        if (tickerRegistry == null) {
            tickerRegistry = TickerRegistryFactory.getInstance(Exchange.LIGHTER);
        }
        return tickerRegistry;
    }

    @Override
    public LighterCreateOrderRequest translateCreateOrder(OrderTicket order, long accountIndex, int apiKeyIndex, long nonce) {
        validateOrder(order);

        Ticker ticker = resolveTickerForOrder(order);
        int marketIndex = resolveMarketIndex(order, ticker);
        long clientOrderIndex = resolveClientOrderIndex(order);

        LighterCreateOrderRequest request = new LighterCreateOrderRequest();
        request.setMarketIndex(marketIndex);
        request.setClientOrderIndex(clientOrderIndex);
        request.setBaseAmount(scaleSize(order.getSize(), ticker));
        request.setPrice(resolveOrderPrice(order, ticker));
        request.setAsk(!order.isBuyOrder());
        request.setOrderType(translateOrderType(order.getType()));
        request.setTimeInForce(translateTimeInForce(order));
        request.setReduceOnly(order.containsModifier(OrderTicket.Modifier.REDUCE_ONLY));
        request.setTriggerPrice(scalePrice(order.getStopPriceAsBigDecimal(), ticker, 0));
        request.setOrderExpiry(resolveOrderExpiry(order));
        request.setNonce(nonce);
        request.setApiKeyIndex(apiKeyIndex);
        request.setAccountIndex(accountIndex);
        return request;
    }

    @Override
    public LighterModifyOrderRequest translateModifyOrder(OrderTicket order, long accountIndex, int apiKeyIndex, long nonce) {
        validateOrder(order);

        Ticker ticker = resolveTickerForOrder(order);
        int marketIndex = resolveMarketIndex(order, ticker);

        LighterModifyOrderRequest request = new LighterModifyOrderRequest();
        request.setMarketIndex(marketIndex);
        request.setOrderIndex(resolveOrderIndex(order));
        request.setBaseAmount(scaleSize(order.getSize(), ticker));
        request.setPrice(resolveOrderPrice(order, ticker));
        request.setAsk(!order.isBuyOrder());
        request.setOrderType(translateOrderType(order.getType()));
        request.setTimeInForce(translateTimeInForce(order));
        request.setReduceOnly(order.containsModifier(OrderTicket.Modifier.REDUCE_ONLY));
        request.setTriggerPrice(scalePrice(order.getStopPriceAsBigDecimal(), ticker, 0));
        request.setOrderExpiry(resolveOrderExpiry(order));
        request.setNonce(nonce);
        request.setApiKeyIndex(apiKeyIndex);
        request.setAccountIndex(accountIndex);
        return request;
    }

    @Override
    public LighterCancelOrderRequest translateCancelOrder(OrderTicket order, long accountIndex, int apiKeyIndex,
            long nonce) {
        validateOrder(order);

        Ticker ticker = resolveTickerForOrder(order);
        int marketIndex = resolveMarketIndex(order, ticker);

        LighterCancelOrderRequest request = new LighterCancelOrderRequest();
        request.setMarketIndex(marketIndex);
        request.setOrderIndex(resolveOrderIndex(order));
        request.setNonce(nonce);
        request.setApiKeyIndex(apiKeyIndex);
        request.setAccountIndex(accountIndex);
        return request;
    }

    @Override
    public OrderTicket translateOrder(LighterOrder order) {
        if (order == null) {
            return null;
        }

        Ticker ticker = resolveTicker(order.getMarketIndex(), null);
        OrderTicket orderTicket = new OrderTicket();
        orderTicket.setTicker(ticker);

        String clientOrderId = normalizeId(order.getClientOrderId(), order.getClientOrderIndex());
        String orderId = normalizeId(order.getOrderId(), order.getOrderIndex());

        orderTicket.setClientOrderId(clientOrderId);
        orderTicket.setOrderId(orderId);
        orderTicket.setDirection(resolveDirection(order));
        orderTicket.setType(resolveOrderType(order));
        orderTicket.setDuration(resolveDuration(order.getTimeInForce()));

        BigDecimal initialSize = order.getInitialBaseAmount();
        if (initialSize == null) {
            initialSize = unscale(order.getBaseSize(), getOrderSizeIncrement(ticker));
        }
        if (initialSize != null) {
            orderTicket.setSize(initialSize);
        }

        BigDecimal limitPrice = order.getPrice();
        if (limitPrice == null) {
            limitPrice = unscale(order.getBasePrice(), getMinimumTickSize(ticker));
        }
        if (limitPrice != null) {
            orderTicket.setLimitPrice(limitPrice);
        }

        if (Boolean.TRUE.equals(order.getReduceOnly())) {
            orderTicket.addModifier(OrderTicket.Modifier.REDUCE_ONLY);
        }

        if (isPostOnly(order.getTimeInForce())) {
            orderTicket.addModifier(OrderTicket.Modifier.POST_ONLY);
        }

        OrderStatus orderStatus = translateOrderStatus(order);
        orderTicket.setCurrentStatus(orderStatus.getStatus());

        if (orderStatus.getFilled() != null) {
            orderTicket.setFilledSize(orderStatus.getFilled());
        }

        if (orderStatus.getFillPrice() != null) {
            orderTicket.setFilledPrice(orderStatus.getFillPrice());
        }

        if (orderStatus.getStatus() == OrderStatus.Status.FILLED) {
            orderTicket.setOrderFilledTime(orderStatus.getTimestamp());
        }

        ZonedDateTime orderEntryTime = resolveOrderTimestamp(order);
        if (orderEntryTime != null) {
            orderTicket.setOrderEntryTime(orderEntryTime);
        }

        return orderTicket;
    }

    @Override
    public List<OrderTicket> translateOrders(List<LighterOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<OrderTicket> translated = new ArrayList<>(orders.size());
        for (LighterOrder order : orders) {
            OrderTicket orderTicket = translateOrder(order);
            if (orderTicket != null) {
                translated.add(orderTicket);
            }
        }
        return translated;
    }

    @Override
    public OrderStatus translateOrderStatus(LighterOrder order) {
        if (order == null) {
            return new OrderStatus(OrderStatus.Status.UNKNOWN, "", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    null, ZonedDateTime.now(UTC));
        }

        Ticker ticker = resolveTicker(order.getMarketIndex(), null);
        BigDecimal originalSize = order.getInitialBaseAmount();
        if (originalSize == null) {
            originalSize = unscale(order.getBaseSize(), getOrderSizeIncrement(ticker));
        }

        BigDecimal remaining = order.getRemainingBaseAmount();
        if (remaining == null && originalSize != null && order.getFilledBaseAmount() != null) {
            remaining = originalSize.subtract(order.getFilledBaseAmount());
        }

        BigDecimal filled = order.getFilledBaseAmount();
        if (filled == null && originalSize != null && remaining != null) {
            filled = originalSize.subtract(remaining);
        }
        if (filled == null) {
            filled = BigDecimal.ZERO;
        }

        if (remaining == null) {
            remaining = originalSize != null ? originalSize.subtract(filled) : BigDecimal.ZERO;
        }

        BigDecimal fillPrice = resolveFillPrice(order, filled);
        OrderStatus.Status status = resolveStatus(order.getStatus(), originalSize, filled, remaining);

        String orderId = normalizeId(order.getOrderId(), order.getOrderIndex());
        ZonedDateTime timestamp = resolveOrderTimestamp(order);
        if (timestamp == null) {
            timestamp = ZonedDateTime.now(UTC);
        }

        OrderStatus orderStatus = new OrderStatus(status, orderId, filled.max(BigDecimal.ZERO),
                remaining.max(BigDecimal.ZERO), fillPrice, ticker, timestamp);
        orderStatus.setClientOrderId(normalizeId(order.getClientOrderId(), order.getClientOrderIndex()));

        if (status == OrderStatus.Status.CANCELED) {
            orderStatus.setCancelReason(resolveCancelReason(order.getStatus()));
        }

        return orderStatus;
    }

    @Override
    public Fill translateFill(LighterTrade trade, long accountIndex) {
        if (trade == null || trade.getPrice() == null || trade.getSize() == null) {
            return null;
        }

        Long askAccountId = trade.getAskAccountId();
        Long bidAccountId = trade.getBidAccountId();
        boolean accountIsAsk = askAccountId != null && askAccountId.longValue() == accountIndex;
        boolean accountIsBid = bidAccountId != null && bidAccountId.longValue() == accountIndex;

        if (!accountIsAsk && !accountIsBid) {
            return null;
        }

        Boolean makerAsk = trade.getMakerAsk();

        TradeDirection side;
        boolean isTaker = false;
        Long orderId;

        if (accountIsAsk) {
            side = TradeDirection.SELL;
            orderId = trade.getAskId();
            if (makerAsk != null) {
                isTaker = !makerAsk.booleanValue();
            }
        } else {
            side = TradeDirection.BUY;
            orderId = trade.getBidId();
            if (makerAsk != null) {
                isTaker = makerAsk.booleanValue();
            }
        }

        Fill fill = new Fill();
        fill.setTicker(resolveTicker(trade.getMarketId(), null));
        fill.setPrice(trade.getPrice());
        fill.setSize(trade.getSize().abs());
        fill.setSide(side);
        fill.setOrderId(orderId == null ? null : String.valueOf(orderId));
        fill.setTaker(isTaker);
        fill.setCommission(resolveCommission(trade, isTaker));
        fill.setFillId(resolveFillId(trade, orderId));
        fill.setTime(resolveTradeTimestamp(trade));
        return fill;
    }

    @Override
    public List<Position> translatePositions(List<LighterPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Position> translated = new ArrayList<>(positions.size());
        for (LighterPosition position : positions) {
            if (position == null) {
                continue;
            }
            Position.Status status = position.getStatus() == LighterPosition.Status.OPEN ? Position.Status.OPEN
                    : Position.Status.CLOSED;
            Side side = position.getSide() == null ? Side.LONG : position.getSide();
            BigDecimal size = position.getSize() == null ? BigDecimal.ZERO : position.getSize();
            BigDecimal averagePrice = position.getAverageEntryPrice() == null ? BigDecimal.ZERO
                    : position.getAverageEntryPrice();

            Position translatedPosition = new Position(position.getTicker(), side, size, averagePrice, status);
            translatedPosition.setLiquidationPrice(position.getLiquidationPrice());
            translated.add(translatedPosition);
        }

        return translated;
    }

    protected void validateOrder(OrderTicket order) {
        if (order == null) {
            throw new IllegalArgumentException("order is required");
        }
        if (order.getTicker() == null) {
            throw new IllegalArgumentException("order ticker is required");
        }
        if (order.getSize() == null || order.getSize().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("order size must be > 0");
        }
        if (order.getType() == null) {
            throw new IllegalArgumentException("order type is required");
        }
    }

    protected Ticker resolveTickerForOrder(OrderTicket order) {
        Ticker ticker = order == null ? null : order.getTicker();
        if (ticker == null) {
            return null;
        }

        Integer id = parseNonNegativeTickerId(ticker);
        if (id != null) {
            return ticker;
        }

        Ticker registryTicker = resolveTicker(null, ticker.getSymbol());
        if (registryTicker != null) {
            applyCanonicalFields(ticker, registryTicker);
        }
        return ticker;
    }

    protected int resolveMarketIndex(OrderTicket order, Ticker resolvedTicker) {
        Integer resolvedTickerId = parseNonNegativeTickerId(resolvedTicker);
        if (resolvedTickerId != null) {
            return resolvedTickerId;
        }

        Integer orderTickerId = parseNonNegativeTickerId(order == null ? null : order.getTicker());
        if (orderTickerId != null) {
            return orderTickerId;
        }

        throw new IllegalArgumentException("Unable to resolve Lighter market index for order ticker "
                + (order != null && order.getTicker() != null ? order.getTicker().getSymbol() : "UNKNOWN"));
    }

    protected Integer parseNonNegativeTickerId(Ticker ticker) {
        if (ticker == null || ticker.getId() == null || ticker.getId().isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(ticker.getId().trim());
            if (parsed < 0) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected long resolveClientOrderIndex(OrderTicket order) {
        String clientOrderId = order.getClientOrderId();
        if (clientOrderId == null || clientOrderId.isBlank()) {
            throw new IllegalArgumentException("clientOrderId is required for Lighter orders");
        }

        return parsePositiveLong(clientOrderId, "clientOrderId");
    }

    protected long resolveOrderIndex(OrderTicket order) {
        String orderId = order.getOrderId();
        if (orderId == null || orderId.isBlank()) {
            String clientOrderId = order.getClientOrderId();
            if (clientOrderId != null && !clientOrderId.isBlank()) {
                return parsePositiveLong(clientOrderId, "clientOrderId");
            }
            throw new IllegalArgumentException("orderId is required for Lighter cancel/modify operations");
        }
        return parsePositiveLong(orderId, "orderId");
    }

    protected long parsePositiveLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(fieldName + " must be >= 0");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be numeric for Lighter. Actual: " + value);
        }
    }

    protected long scaleSize(BigDecimal size, Ticker ticker) {
        BigDecimal increment = getOrderSizeIncrement(ticker);
        return size.divide(increment, 0, RoundingMode.DOWN).longValue();
    }

    protected int resolveOrderPrice(OrderTicket order, Ticker ticker) {
        if (order.getType() == OrderTicket.Type.MARKET) {
            BigDecimal orderPrice = order.getLimitPrice();
            if (orderPrice == null || orderPrice.compareTo(BigDecimal.ZERO) <= 0) {
                orderPrice = order.getStopPriceAsBigDecimal();
            }
            if (orderPrice == null || orderPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return resolveDefaultMarketPrice(order);
            }
            return scalePrice(orderPrice, ticker, 0);
        }

        BigDecimal limitPrice = order.getLimitPrice();
        if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            if (order.getType() == OrderTicket.Type.STOP || order.getType() == OrderTicket.Type.STOP_LIMIT) {
                BigDecimal stopPrice = order.getStopPriceAsBigDecimal();
                if (stopPrice != null && stopPrice.compareTo(BigDecimal.ZERO) > 0) {
                    return scalePrice(stopPrice, ticker, 0);
                }
            }
            throw new IllegalArgumentException("limitPrice is required for non-market order translation");
        }
        return scalePrice(limitPrice, ticker, 0);
    }

    protected int resolveDefaultMarketPrice(OrderTicket order) {
        // Lighter encodes market orders with a price field. If caller does not provide
        // one, use side-aware aggressive defaults to preserve market semantics.
        return order != null && order.isBuyOrder() ? Integer.MAX_VALUE : 0;
    }

    protected int scalePrice(BigDecimal price, Ticker ticker, int fallback) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return fallback;
        }
        BigDecimal tick = getMinimumTickSize(ticker);
        return price.divide(tick, 0, RoundingMode.DOWN).intValue();
    }

    protected BigDecimal getOrderSizeIncrement(Ticker ticker) {
        if (ticker != null && ticker.getOrderSizeIncrement() != null
                && ticker.getOrderSizeIncrement().compareTo(BigDecimal.ZERO) > 0) {
            return ticker.getOrderSizeIncrement();
        }
        return BigDecimal.ONE;
    }

    protected BigDecimal getMinimumTickSize(Ticker ticker) {
        if (ticker != null && ticker.getMinimumTickSize() != null && ticker.getMinimumTickSize().compareTo(BigDecimal.ZERO) > 0) {
            return ticker.getMinimumTickSize();
        }
        return BigDecimal.ONE;
    }

    protected long resolveOrderExpiry(OrderTicket order) {
        if (order.getDuration() == OrderTicket.Duration.GOOD_UNTIL_TIME && order.getGoodUntilTime() != null) {
            return order.getGoodUntilTime().toInstant().toEpochMilli();
        }
        return LighterCreateOrderRequest.DEFAULT_ORDER_EXPIRY;
    }

    protected LighterOrderType translateOrderType(OrderTicket.Type type) {
        if (type == null) {
            return LighterOrderType.LIMIT;
        }

        return switch (type) {
        case MARKET -> LighterOrderType.MARKET;
        case LIMIT -> LighterOrderType.LIMIT;
        case STOP -> LighterOrderType.STOP_LOSS;
        case STOP_LIMIT -> LighterOrderType.STOP_LOSS_LIMIT;
        default -> throw new UnsupportedOperationException("Order type " + type + " is not supported");
        };
    }

    protected LighterTimeInForce translateTimeInForce(OrderTicket order) {
        if (order.containsModifier(OrderTicket.Modifier.POST_ONLY)) {
            return LighterTimeInForce.POST_ONLY;
        }

        if (order.getType() == OrderTicket.Type.MARKET || order.getDuration() == OrderTicket.Duration.IMMEDIATE_OR_CANCEL
                || order.getDuration() == OrderTicket.Duration.FILL_OR_KILL) {
            return LighterTimeInForce.IOC;
        }

        return LighterTimeInForce.GTT;
    }

    protected OrderTicket.Type resolveOrderType(LighterOrder order) {
        String type = normalize(order.getType());
        if (type == null) {
            return OrderTicket.Type.LIMIT;
        }

        return switch (type) {
        case "market" -> OrderTicket.Type.MARKET;
        case "stop", "stop_loss" -> OrderTicket.Type.STOP;
        case "stop_limit", "stop_loss_limit" -> OrderTicket.Type.STOP_LIMIT;
        default -> OrderTicket.Type.LIMIT;
        };
    }

    protected OrderTicket.Duration resolveDuration(String timeInForce) {
        String normalized = normalize(timeInForce);
        if (normalized == null) {
            return OrderTicket.Duration.GOOD_UNTIL_CANCELED;
        }

        if (normalized.contains("ioc")) {
            return OrderTicket.Duration.IMMEDIATE_OR_CANCEL;
        }
        if (normalized.contains("post")) {
            return OrderTicket.Duration.GOOD_UNTIL_CANCELED;
        }
        if (normalized.contains("gtt") || normalized.contains("good-till-time") || normalized.contains("good_till_time")) {
            return OrderTicket.Duration.GOOD_UNTIL_TIME;
        }
        if (normalized.contains("gtc") || normalized.contains("good-till-cancel")) {
            return OrderTicket.Duration.GOOD_UNTIL_CANCELED;
        }

        return OrderTicket.Duration.GOOD_UNTIL_CANCELED;
    }

    protected boolean isPostOnly(String timeInForce) {
        String normalized = normalize(timeInForce);
        return normalized != null && normalized.contains("post");
    }

    protected TradeDirection resolveDirection(LighterOrder order) {
        if (order.getAsk() != null) {
            return Boolean.TRUE.equals(order.getAsk()) ? TradeDirection.SELL : TradeDirection.BUY;
        }

        String side = normalize(order.getSide());
        if ("sell".equals(side)) {
            return TradeDirection.SELL;
        }

        return TradeDirection.BUY;
    }

    protected BigDecimal resolveFillPrice(LighterOrder order, BigDecimal filledSize) {
        if (order.getFilledQuoteAmount() != null && filledSize != null && filledSize.compareTo(BigDecimal.ZERO) > 0) {
            return order.getFilledQuoteAmount().divide(filledSize, 12, RoundingMode.HALF_UP);
        }

        if (order.getPrice() != null) {
            return order.getPrice();
        }

        Ticker ticker = resolveTicker(order.getMarketIndex(), null);
        return unscale(order.getBasePrice(), getMinimumTickSize(ticker));
    }

    protected OrderStatus.Status resolveStatus(String statusText, BigDecimal originalSize, BigDecimal filled,
            BigDecimal remaining) {
        String normalizedStatus = normalize(statusText);

        if (normalizedStatus != null) {
            if (normalizedStatus.contains("reject")) {
                return OrderStatus.Status.REJECTED;
            }
            if (normalizedStatus.contains("cancel") || normalizedStatus.contains("expire")) {
                return OrderStatus.Status.CANCELED;
            }
            if (normalizedStatus.contains("partial")) {
                return OrderStatus.Status.PARTIAL_FILL;
            }
            if (normalizedStatus.contains("fill")) {
                return OrderStatus.Status.FILLED;
            }
            if (normalizedStatus.contains("close")) {
                if (remaining != null && remaining.compareTo(BigDecimal.ZERO) == 0) {
                    return OrderStatus.Status.FILLED;
                }
                return OrderStatus.Status.CANCELED;
            }
            if (normalizedStatus.contains("open") || normalizedStatus.contains("new") || normalizedStatus.contains("pending")
                    || normalizedStatus.contains("trigger")) {
                if (filled != null && filled.compareTo(BigDecimal.ZERO) > 0 && remaining != null
                        && remaining.compareTo(BigDecimal.ZERO) > 0) {
                    return OrderStatus.Status.PARTIAL_FILL;
                }
                if (remaining != null && remaining.compareTo(BigDecimal.ZERO) == 0 && originalSize != null
                        && originalSize.compareTo(BigDecimal.ZERO) > 0) {
                    return OrderStatus.Status.FILLED;
                }
                return OrderStatus.Status.NEW;
            }
        }

        if (remaining != null && remaining.compareTo(BigDecimal.ZERO) == 0 && originalSize != null
                && originalSize.compareTo(BigDecimal.ZERO) > 0) {
            return OrderStatus.Status.FILLED;
        }

        if (filled != null && filled.compareTo(BigDecimal.ZERO) > 0) {
            return OrderStatus.Status.PARTIAL_FILL;
        }

        return OrderStatus.Status.UNKNOWN;
    }

    protected OrderStatus.CancelReason resolveCancelReason(String statusText) {
        String normalizedStatus = normalize(statusText);
        if (normalizedStatus != null && normalizedStatus.contains("post") && normalizedStatus.contains("cross")) {
            return OrderStatus.CancelReason.POST_ONLY_WOULD_CROSS;
        }
        return OrderStatus.CancelReason.USER_CANCELED;
    }

    protected String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    protected String normalizeId(String preferred, Long fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback == null) {
            return "";
        }
        return String.valueOf(fallback.longValue());
    }

    protected ZonedDateTime resolveOrderTimestamp(LighterOrder order) {
        if (order == null) {
            return null;
        }

        Long timestamp = firstNonNull(order.getUpdatedAt(), order.getTransactionTime(), order.getCreatedAt(),
                order.getTimestamp());
        if (timestamp == null) {
            return null;
        }

        return toZonedDateTime(timestamp.longValue());
    }

    protected ZonedDateTime resolveTradeTimestamp(LighterTrade trade) {
        if (trade == null || trade.getTimestamp() == null) {
            return ZonedDateTime.now(UTC);
        }
        return toZonedDateTime(trade.getTimestamp().longValue());
    }

    protected String resolveFillId(LighterTrade trade, Long orderId) {
        if (trade.getId() != null) {
            return String.valueOf(trade.getId().longValue());
        }

        String txHash = trade.getTxHash() == null ? "tx" : trade.getTxHash();
        String orderPart = orderId == null ? "order" : String.valueOf(orderId.longValue());
        String timePart = trade.getTimestamp() == null ? "time" : String.valueOf(trade.getTimestamp().longValue());
        return txHash + ":" + orderPart + ":" + timePart;
    }

    protected BigDecimal resolveCommission(LighterTrade trade, boolean isTaker) {
        if (isTaker && trade.getTakerFee() != null) {
            return trade.getTakerFee();
        }
        return BigDecimal.ZERO;
    }

    protected BigDecimal unscale(Long rawValue, BigDecimal increment) {
        if (rawValue == null || increment == null) {
            return null;
        }
        return increment.multiply(BigDecimal.valueOf(rawValue.longValue()));
    }

    protected Ticker resolveTicker(Integer marketIndex, String symbol) {
        Ticker ticker = findTickerByMarketIndex(marketIndex);
        if (ticker != null) {
            return ticker;
        }

        ticker = findTickerBySymbol(symbol);
        if (ticker != null) {
            return ticker;
        }

        return buildFallbackTicker(marketIndex, symbol);
    }

    protected Ticker findTickerByMarketIndex(Integer marketIndex) {
        if (marketIndex == null) {
            return null;
        }

        String marketKey = String.valueOf(marketIndex.intValue());
        ITickerRegistry registry = getTickerRegistry();
        for (InstrumentType instrumentType : SUPPORTED_TYPES) {
            Ticker ticker = registry.lookupByBrokerSymbol(instrumentType, marketKey);
            if (ticker != null) {
                return ticker;
            }
        }

        return null;
    }

    protected Ticker findTickerBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        ITickerRegistry registry = getTickerRegistry();
        for (InstrumentType instrumentType : SUPPORTED_TYPES) {
            Ticker byBrokerSymbol = registry.lookupByBrokerSymbol(instrumentType, symbol);
            if (byBrokerSymbol != null) {
                return byBrokerSymbol;
            }

            Ticker byCommonSymbol = registry.lookupByCommonSymbol(instrumentType, symbol);
            if (byCommonSymbol != null) {
                return byCommonSymbol;
            }

            if (!symbol.contains("/")) {
                Ticker derivedCommon = registry.lookupByCommonSymbol(instrumentType, symbol + "/USDC");
                if (derivedCommon != null) {
                    return derivedCommon;
                }
            }
        }

        return null;
    }

    protected Ticker buildFallbackTicker(Integer marketIndex, String symbol) {
        String resolvedSymbol = symbol;
        if (resolvedSymbol == null || resolvedSymbol.isBlank()) {
            resolvedSymbol = marketIndex == null ? "UNKNOWN" : String.valueOf(marketIndex.intValue());
        }

        Ticker ticker = new Ticker(resolvedSymbol);
        ticker.setExchange(Exchange.LIGHTER);
        ticker.setPrimaryExchange(Exchange.LIGHTER);
        ticker.setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        ticker.setMinimumTickSize(BigDecimal.ONE);
        ticker.setOrderSizeIncrement(BigDecimal.ONE);
        if (marketIndex != null) {
            ticker.setId(String.valueOf(marketIndex.intValue()));
        }
        return ticker;
    }

    protected void applyCanonicalFields(Ticker target, Ticker source) {
        if (target == null || source == null) {
            return;
        }

        if (target.getId() == null || target.getId().isBlank()) {
            target.setId(source.getId());
        }
        if (target.getInstrumentType() == null) {
            target.setInstrumentType(source.getInstrumentType());
        }
        if (target.getExchange() == null) {
            target.setExchange(source.getExchange());
        }

        target.setMinimumTickSize(source.getMinimumTickSize());
        target.setOrderSizeIncrement(source.getOrderSizeIncrement());
        target.setContractMultiplier(source.getContractMultiplier());
        target.setFundingRateInterval(source.getFundingRateInterval());
        target.setMinimumOrderSize(source.getMinimumOrderSize());
        target.setMinimumOrderSizeNotional(source.getMinimumOrderSizeNotional());
    }

    @SafeVarargs
    protected final <T> T firstNonNull(T... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected ZonedDateTime toZonedDateTime(long timestamp) {
        long epochMillis = timestamp;
        if (Math.abs(epochMillis) < 100_000_000_000L) {
            epochMillis = epochMillis * 1000L;
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), UTC);
    }
}
