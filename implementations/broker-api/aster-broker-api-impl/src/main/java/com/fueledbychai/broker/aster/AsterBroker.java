package com.fueledbychai.broker.aster;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.aster.common.api.IAsterRestApi;
import com.fueledbychai.aster.common.api.IAsterWebSocketApi;
import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderStatus.CancelReason;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Duration;
import com.fueledbychai.broker.order.OrderTicket.Modifier;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.time.Span;
import com.fueledbychai.time.WsLatency;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

public class AsterBroker extends AbstractBasicBroker {

    protected static final Logger logger = LoggerFactory.getLogger(AsterBroker.class);
    protected static final String LATENCY_LOGGER = "latency.aster";
    protected static final long USER_STREAM_KEEPALIVE_PERIOD_MINUTES = 30L;
    protected static final long ACCOUNT_SNAPSHOT_POLL_SECONDS = 1L;
    protected static final BigDecimal DEFAULT_TICK_SIZE = new BigDecimal("0.01");

    protected final IAsterRestApi restApi;
    protected final IAsterWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;
    protected final AtomicLong nextClientOrderId = new AtomicLong(System.currentTimeMillis());
    protected final Map<String, Position> positionsByKey = new ConcurrentHashMap<>();
    protected final Set<String> processedFillIds = ConcurrentHashMap.newKeySet();

    protected volatile boolean connected = false;
    protected volatile String listenKey;
    protected volatile ScheduledExecutorService listenKeyKeepAliveExecutor;
    protected volatile ScheduledExecutorService accountSnapshotExecutor;

    public AsterBroker() {
        this(ExchangeRestApiFactory.getPrivateApi(Exchange.ASTER, IAsterRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.ASTER, IAsterWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.ASTER));
    }

    protected AsterBroker(IAsterRestApi restApi, IAsterWebSocketApi webSocketApi, ITickerRegistry tickerRegistry) {
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        if (webSocketApi == null) {
            throw new IllegalArgumentException("webSocketApi is required");
        }
        if (tickerRegistry == null) {
            throw new IllegalArgumentException("tickerRegistry is required");
        }
        this.restApi = restApi;
        this.webSocketApi = webSocketApi;
        this.tickerRegistry = tickerRegistry;
    }

    @Override
    protected void onDisconnect() {
        connected = false;
        stopKeepAliveTask();
        stopAccountSnapshotPoller();
        try {
            if (listenKey != null && !listenKey.isBlank() && !restApi.isPublicApiOnly()) {
                restApi.closeUserDataStream(listenKey);
            }
        } catch (Exception e) {
            logger.debug("Ignoring Aster listen-key close failure", e);
        } finally {
            listenKey = null;
        }
        webSocketApi.disconnectAll();
    }

    @Override
    public String getBrokerName() {
        return "Aster";
    }

    @Override
    public synchronized void connect() {
        if (connected) {
            return;
        }
        if (restApi.isPublicApiOnly()) {
            throw new IllegalStateException("Aster broker requires private api credentials.");
        }

        webSocketApi.connect();
        listenKey = restApi.startUserDataStream();
        webSocketApi.subscribeUserData(listenKey, this::onUserDataMessage);
        startKeepAliveTask();
        connected = true;

        refreshOpenOrdersFromRest();
        refreshPositionsFromRest();
        refreshAccountSnapshotFromRest();
        startAccountSnapshotPoller();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public BrokerStatus getBrokerStatus() {
        return connected ? BrokerStatus.OK : BrokerStatus.UNKNOWN;
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        if (!connected) {
            return notConnectedResult();
        }
        if (id == null || id.isBlank()) {
            return new BrokerRequestResult(false, true, "order id is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        OrderTicket openOrder = orderRegistry.getOpenOrderById(id);
        if (openOrder == null) {
            return new BrokerRequestResult(false, true, "Open order not found for id " + id,
                    BrokerRequestResult.FailureType.ORDER_NOT_OPEN);
        }
        return cancelResolvedOrder(openOrder);
    }

    @Override
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        if (!connected) {
            return notConnectedResult();
        }
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return new BrokerRequestResult(false, true, "clientOrderId is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        OrderTicket openOrder = orderRegistry.getOpenOrderByClientId(clientOrderId);
        if (openOrder == null) {
            return new BrokerRequestResult(false, true, "Open order not found for clientOrderId " + clientOrderId,
                    BrokerRequestResult.FailureType.ORDER_NOT_OPEN);
        }
        return cancelResolvedOrder(openOrder);
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        if (!connected) {
            return notConnectedResult();
        }
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        OrderTicket resolved = resolveOpenOrder(order);
        if (resolved == null) {
            return new BrokerRequestResult(false, true, "Open order not found",
                    BrokerRequestResult.FailureType.ORDER_NOT_OPEN);
        }
        return cancelResolvedOrder(resolved);
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        if (!connected) {
            return notConnectedResult();
        }
        Ticker resolvedTicker = resolveTicker(ticker);
        if (resolvedTicker == null) {
            return new BrokerRequestResult(false, true, "ticker is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        if (!supportsBrokerTicker(resolvedTicker)) {
            return new BrokerRequestResult(false, true, "Aster broker currently supports perpetual futures only",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        try {
            try (var s = Span.start("AST_CANCEL_ALL_ORDERS_API_CALL", resolvedTicker.getSymbol(), LATENCY_LOGGER)) {
                restApi.cancelAllOpenOrders(resolvedTicker.getSymbol());
            }
            for (OrderTicket openOrder : new ArrayList<>(orderRegistry.getOpenOrdersByTicker(resolvedTicker))) {
                markOrderCanceled(openOrder, CancelReason.USER_CANCELED, getCurrentTime(), true);
            }
            return new BrokerRequestResult();
        } catch (Exception e) {
            logger.error("Error canceling Aster orders for {}", resolvedTicker.getSymbol(), e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        if (!connected) {
            return notConnectedResult();
        }

        Set<Ticker> tickers = new LinkedHashSet<>();
        for (OrderTicket order : orderRegistry.getOpenOrders()) {
            if (order != null && order.getTicker() != null) {
                tickers.add(order.getTicker());
            }
        }

        BrokerRequestResult lastFailure = null;
        for (Ticker ticker : tickers) {
            BrokerRequestResult result = cancelAllOrders(ticker);
            if (!result.isSuccess()) {
                lastFailure = result;
            }
        }
        return lastFailure == null ? new BrokerRequestResult() : lastFailure;
    }

    @Override
    public BrokerRequestResult cancelOrders(java.util.List<OrderTicket> orders) {
        throw new UnsupportedOperationException("Batch cancel not supported by AsterBroker");
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        if (!connected) {
            return notConnectedResult();
        }

        PreparedOrder preparedOrder = prepareOrderForSubmission(order);
        if (!preparedOrder.validationResult.isSuccess()) {
            return preparedOrder.validationResult;
        }

        try {
            applyPreparedOrder(order, preparedOrder);
            ensureOrderIds(order);
            order.setOrderEntryTime(getCurrentTime());

            JsonNode response;
            try (var s = Span.start("AST_PLACE_ORDER_API_CALL", order.getClientOrderId(), LATENCY_LOGGER)) {
                response = restApi.placeOrder(buildPlaceOrderParams(order));
            }
            ZonedDateTime timestamp = toTimestamp(response, "updateTime");
            OrderTicket trackedOrder = applyOrderSnapshot(response, timestamp, true);
            if (trackedOrder == null) {
                trackedOrder = order;
            }
            return new BrokerRequestResult();
        } catch (Exception e) {
            logger.error("Error placing Aster order", e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        if (!connected) {
            return notConnectedResult();
        }
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        OrderTicket existing = resolveOpenOrder(order);
        if (existing == null) {
            return new BrokerRequestResult(false, true, "Open order not found for modify",
                    BrokerRequestResult.FailureType.ORDER_NOT_OPEN);
        }

        BrokerRequestResult cancelResult = cancelResolvedOrder(existing);
        if (!cancelResult.isSuccess()) {
            return cancelResult;
        }

        if (order.getClientOrderId() == null || order.getClientOrderId().isBlank()
                || Objects.equals(order.getClientOrderId(), existing.getClientOrderId())) {
            order.setClientOrderId(getNextOrderId());
        }
        order.setOrderId("");
        return placeOrder(order);
    }

    @Override
    public String getNextOrderId() {
        return "aster-" + nextClientOrderId.incrementAndGet();
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }

        OrderTicket knownOrder = orderRegistry.getOrderById(orderId);
        if (!connected || knownOrder == null || knownOrder.getTicker() == null) {
            return knownOrder;
        }

        try {
            JsonNode response;
            try (var s = Span.start("AST_QUERY_ORDER_BY_ID_API_CALL", orderId, LATENCY_LOGGER)) {
                response = restApi.queryOrder(knownOrder.getTicker().getSymbol(), orderId, null);
            }
            return applyOrderSnapshot(response, toTimestamp(response, "updateTime"), false);
        } catch (Exception e) {
            logger.debug("Unable to refresh Aster order {}", orderId, e);
            return knownOrder;
        }
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return null;
        }

        OrderTicket knownOrder = orderRegistry.getOrderByClientId(clientOrderId);
        if (!connected || knownOrder == null || knownOrder.getTicker() == null) {
            return knownOrder;
        }

        try {
            JsonNode response;
            try (var s = Span.start("AST_QUERY_ORDER_BY_CLIENT_ID_API_CALL", clientOrderId, LATENCY_LOGGER)) {
                response = restApi.queryOrder(knownOrder.getTicker().getSymbol(), null, clientOrderId);
            }
            return applyOrderSnapshot(response, toTimestamp(response, "updateTime"), false);
        } catch (Exception e) {
            logger.debug("Unable to refresh Aster order {}", clientOrderId, e);
            return knownOrder;
        }
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        if (!connected) {
            return new ArrayList<>(orderRegistry.getOpenOrders());
        }

        try {
            refreshOpenOrdersFromRest();
            return new ArrayList<>(orderRegistry.getOpenOrders());
        } catch (Exception e) {
            logger.debug("Unable to refresh Aster open orders", e);
            return new ArrayList<>(orderRegistry.getOpenOrders());
        }
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        BrokerRequestResult cancelResult = cancelOrder(originalOrderId);
        if (!cancelResult.isSuccess()) {
            logger.warn("Cancel and replace failed during cancel: {}", cancelResult);
            return;
        }
        BrokerRequestResult placeResult = placeOrder(newOrder);
        if (!placeResult.isSuccess()) {
            logger.warn("Cancel and replace failed during new order placement: {}", placeResult);
        }
    }

    @Override
    public List<Position> getAllPositions() {
        if (connected) {
            try {
                refreshPositionsFromRest();
            } catch (Exception e) {
                logger.debug("Unable to refresh Aster positions", e);
            }
        }
        return new ArrayList<>(positionsByKey.values());
    }

    protected BrokerRequestResult cancelResolvedOrder(OrderTicket order) {
        try {
            JsonNode response;
            try (var s = Span.start("AST_CANCEL_ORDER_API_CALL", order.getClientOrderId(), LATENCY_LOGGER)) {
                response = restApi.cancelOrder(order.getTicker().getSymbol(), blankToNull(order.getOrderId()),
                        blankToNull(order.getClientOrderId()));
            }
            applyOrderSnapshot(response, toTimestamp(response, "updateTime"), true);
            return new BrokerRequestResult();
        } catch (Exception e) {
            logger.error("Error canceling Aster order {}", order.getOrderId(), e);
            return new BrokerRequestResult(false, true, e.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    protected BrokerRequestResult validateOrder(OrderTicket order) {
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        if (order.getTicker() == null) {
            return new BrokerRequestResult(false, true, "order ticker is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        if (!supportsBrokerTicker(order.getTicker())) {
            return new BrokerRequestResult(false, true, "Aster broker currently supports perpetual futures only",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        if (order.getSize() == null || order.getSize().signum() <= 0) {
            return new BrokerRequestResult(false, true, "order size must be > 0",
                    BrokerRequestResult.FailureType.INVALID_SIZE);
        }

        Type type = order.getType() == null ? Type.MARKET : order.getType();
        if ((type == Type.LIMIT || type == Type.STOP_LIMIT)
                && (order.getLimitPrice() == null || order.getLimitPrice().signum() <= 0)) {
            return new BrokerRequestResult(false, true, "limit orders require a positive limit price",
                    BrokerRequestResult.FailureType.INVALID_PRICE);
        }
        if ((type == Type.STOP || type == Type.STOP_LIMIT)
                && (order.getStopPrice() == null || order.getStopPrice().signum() <= 0)) {
            return new BrokerRequestResult(false, true, "stop orders require a positive stop price",
                    BrokerRequestResult.FailureType.INVALID_PRICE);
        }
        if (!supportsOrderType(type)) {
            return new BrokerRequestResult(false, true, "unsupported Aster order type " + type,
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        if (order.containsModifier(Modifier.POST_ONLY) && type != Type.LIMIT) {
            return new BrokerRequestResult(false, true, "post-only is only supported for limit orders",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }
        return new BrokerRequestResult();
    }

    protected boolean supportsOrderType(Type type) {
        return type == Type.MARKET || type == Type.LIMIT || type == Type.STOP || type == Type.STOP_LIMIT;
    }

    protected boolean supportsBrokerTicker(Ticker ticker) {
        return ticker == null || ticker.getInstrumentType() != InstrumentType.CRYPTO_SPOT;
    }

    protected void ensureOrderIds(OrderTicket order) {
        if (order.getClientOrderId() == null || order.getClientOrderId().isBlank()) {
            order.setClientOrderId(getNextOrderId());
        }
        if (order.getOrderId() == null) {
            order.setOrderId("");
        }
    }

    protected OrderTicket resolveOpenOrder(OrderTicket order) {
        if (order == null) {
            return null;
        }
        if (order.getOrderId() != null && !order.getOrderId().isBlank()) {
            OrderTicket foundByOrderId = orderRegistry.getOpenOrderById(order.getOrderId());
            if (foundByOrderId != null) {
                return foundByOrderId;
            }
        }
        if (order.getClientOrderId() != null && !order.getClientOrderId().isBlank()) {
            return orderRegistry.getOpenOrderByClientId(order.getClientOrderId());
        }
        return null;
    }

    protected BrokerRequestResult notConnectedResult() {
        return new BrokerRequestResult(false, false, "Broker is not connected",
                BrokerRequestResult.FailureType.UNKNOWN);
    }

    protected PreparedOrder prepareOrderForSubmission(OrderTicket order) {
        BrokerRequestResult validation = validateOrder(order);
        if (!validation.isSuccess()) {
            return PreparedOrder.failed(validation);
        }

        Ticker resolvedTicker = resolveSubmissionTicker(order.getTicker());
        Type type = order.getType() == null ? Type.MARKET : order.getType();
        boolean applyVenueConstraints = shouldApplyVenueConstraints(order.getTicker(), resolvedTicker);

        BigDecimal normalizedSize = order.getSize();
        if (applyVenueConstraints) {
            normalizedSize = normalizeDown(order.getSize(), resolvedTicker.getOrderSizeIncrement());
            if (normalizedSize == null || normalizedSize.signum() <= 0) {
                return PreparedOrder.failed(validationFailure(BrokerRequestResult.FailureType.INVALID_SIZE,
                        "order size " + formatDecimal(normalizedSize)
                                + " is invalid after applying Aster step size "
                                + formatDecimal(resolvedTicker.getOrderSizeIncrement()) + " for "
                                + resolvedSymbol(resolvedTicker, order.getTicker())));
            }
        }

        BigDecimal normalizedLimitPrice = order.getLimitPrice();
        if (applyVenueConstraints && (type == Type.LIMIT || type == Type.STOP_LIMIT)) {
            normalizedLimitPrice = normalizeDown(order.getLimitPrice(), resolvedTicker.getMinimumTickSize());
            if (normalizedLimitPrice == null || normalizedLimitPrice.signum() <= 0) {
                return PreparedOrder.failed(validationFailure(BrokerRequestResult.FailureType.INVALID_PRICE,
                        "limit price " + formatDecimal(normalizedLimitPrice)
                                + " is invalid after applying Aster tick size "
                                + formatDecimal(resolvedTicker.getMinimumTickSize()) + " for "
                                + resolvedSymbol(resolvedTicker, order.getTicker())));
            }
        }

        BigDecimal normalizedStopPrice = order.getStopPrice();
        if (applyVenueConstraints && (type == Type.STOP || type == Type.STOP_LIMIT)) {
            normalizedStopPrice = normalizeDown(order.getStopPrice(), resolvedTicker.getMinimumTickSize());
            if (normalizedStopPrice == null || normalizedStopPrice.signum() <= 0) {
                return PreparedOrder.failed(validationFailure(BrokerRequestResult.FailureType.INVALID_PRICE,
                        "stop price " + formatDecimal(normalizedStopPrice)
                                + " is invalid after applying Aster tick size "
                                + formatDecimal(resolvedTicker.getMinimumTickSize()) + " for "
                                + resolvedSymbol(resolvedTicker, order.getTicker())));
            }
        }

        BrokerRequestResult normalizedValidation = validateNormalizedOrder(resolvedTicker, order.getTicker(), type,
                normalizedSize, normalizedLimitPrice, normalizedStopPrice, applyVenueConstraints);
        if (!normalizedValidation.isSuccess()) {
            return PreparedOrder.failed(normalizedValidation);
        }

        return PreparedOrder.success(resolvedTicker, normalizedSize, normalizedLimitPrice, normalizedStopPrice);
    }

    protected void applyPreparedOrder(OrderTicket order, PreparedOrder preparedOrder) {
        if (order == null || preparedOrder == null) {
            return;
        }
        order.setTicker(preparedOrder.resolvedTicker);
        order.setSize(preparedOrder.normalizedSize);
        if (preparedOrder.normalizedLimitPrice != null) {
            order.setLimitPrice(preparedOrder.normalizedLimitPrice);
        }
        if (preparedOrder.normalizedStopPrice != null) {
            order.setStopPrice(preparedOrder.normalizedStopPrice);
        }
    }

    protected BrokerRequestResult validateNormalizedOrder(Ticker resolvedTicker, Ticker originalTicker, Type type,
            BigDecimal normalizedSize, BigDecimal normalizedLimitPrice, BigDecimal normalizedStopPrice,
            boolean hasVenueConstraints) {
        if (!hasVenueConstraints) {
            return new BrokerRequestResult();
        }

        String symbol = resolvedSymbol(resolvedTicker, originalTicker);
        BigDecimal minOrderSize = positiveOrNull(resolvedTicker.getMinimumOrderSize());
        if (minOrderSize != null && normalizedSize.compareTo(minOrderSize) < 0) {
            return validationFailure(BrokerRequestResult.FailureType.INVALID_SIZE,
                    "normalized order size " + formatDecimal(normalizedSize)
                            + " is below Aster minimum order size " + formatDecimal(minOrderSize) + " for "
                            + symbol);
        }

        BigDecimal minNotional = positiveOrNull(resolvedTicker.getMinimumOrderSizeNotional());
        BigDecimal validationPrice = resolveValidationPrice(type, normalizedLimitPrice, normalizedStopPrice);
        if (minNotional != null && validationPrice != null) {
            BigDecimal normalizedNotional = normalizedSize.multiply(validationPrice);
            if (normalizedNotional.compareTo(minNotional) < 0) {
                return validationFailure(BrokerRequestResult.FailureType.VALIDATION_FAILED,
                        "normalized order notional " + formatDecimal(normalizedNotional)
                                + " is below Aster minimum notional " + formatDecimal(minNotional) + " for "
                                + symbol);
            }
        }

        return new BrokerRequestResult();
    }

    protected BrokerRequestResult validationFailure(BrokerRequestResult.FailureType failureType, String message) {
        return new BrokerRequestResult(false, true, message, failureType);
    }

    protected BigDecimal normalizeDown(BigDecimal value, BigDecimal increment) {
        if (value == null || increment == null || increment.signum() <= 0) {
            return value;
        }

        BigDecimal normalizedIncrement = increment.stripTrailingZeros();
        BigDecimal normalizedValue = value.divideToIntegralValue(normalizedIncrement).multiply(normalizedIncrement);
        return normalizedValue.stripTrailingZeros();
    }

    protected BigDecimal resolveValidationPrice(Type type, BigDecimal normalizedLimitPrice, BigDecimal normalizedStopPrice) {
        if (type == Type.LIMIT || type == Type.STOP_LIMIT) {
            return normalizedLimitPrice;
        }
        if (type == Type.STOP) {
            return normalizedStopPrice;
        }
        return null;
    }

    protected BigDecimal positiveOrNull(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return null;
        }
        return value.stripTrailingZeros();
    }

    protected Ticker resolveSubmissionTicker(Ticker ticker) {
        if (ticker == null) {
            return null;
        }

        Ticker resolvedTicker = resolveTicker(ticker);
        if (resolvedTicker != null && resolvedTicker != ticker) {
            return resolvedTicker;
        }

        String symbol = ticker.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            return ticker;
        }

        InstrumentDescriptor descriptor = restApi.getInstrumentDescriptor(symbol);
        if (descriptor != null) {
            return new TickerTranslator().translateTicker(descriptor);
        }
        return resolvedTicker == null ? ticker : resolvedTicker;
    }

    protected boolean shouldApplyVenueConstraints(Ticker originalTicker, Ticker resolvedTicker) {
        if (resolvedTicker == null) {
            return false;
        }
        if (resolvedTicker != originalTicker) {
            return true;
        }
        if (positiveOrNull(originalTicker.getMinimumOrderSize()) != null) {
            return true;
        }
        if (positiveOrNull(originalTicker.getMinimumOrderSizeNotional()) != null) {
            return true;
        }
        BigDecimal orderSizeIncrement = positiveOrNull(originalTicker.getOrderSizeIncrement());
        if (orderSizeIncrement != null && orderSizeIncrement.compareTo(BigDecimal.ONE) != 0) {
            return true;
        }
        BigDecimal minimumTickSize = positiveOrNull(originalTicker.getMinimumTickSize());
        return minimumTickSize != null && minimumTickSize.compareTo(DEFAULT_TICK_SIZE) != 0;
    }

    protected String resolvedSymbol(Ticker resolvedTicker, Ticker originalTicker) {
        if (resolvedTicker != null && resolvedTicker.getSymbol() != null && !resolvedTicker.getSymbol().isBlank()) {
            return resolvedTicker.getSymbol();
        }
        return originalTicker == null ? "" : originalTicker.getSymbol();
    }

    protected Map<String, String> buildPlaceOrderParams(OrderTicket order) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("symbol", order.getTicker().getSymbol());
        params.put("side", order.isBuyOrder() ? "BUY" : "SELL");
        params.put("type", toExchangeOrderType(order.getType()));
        params.put("newClientOrderId", order.getClientOrderId());

        Type type = order.getType() == null ? Type.MARKET : order.getType();
        if (type == Type.LIMIT || type == Type.STOP_LIMIT) {
            params.put("timeInForce", toExchangeTimeInForce(order));
            params.put("price", formatDecimal(order.getLimitPrice()));
        }
        if (type == Type.MARKET || type == Type.LIMIT || type == Type.STOP || type == Type.STOP_LIMIT) {
            params.put("quantity", formatDecimal(order.getSize()));
        }
        if (type == Type.STOP || type == Type.STOP_LIMIT) {
            params.put("stopPrice", formatDecimal(order.getStopPrice()));
        }
        if (order.containsModifier(Modifier.REDUCE_ONLY)) {
            params.put("reduceOnly", "true");
        }
        params.put("newOrderRespType", "ACK");
        return params;
    }

    protected String toExchangeOrderType(Type type) {
        Type normalized = type == null ? Type.MARKET : type;
        return switch (normalized) {
            case MARKET -> "MARKET";
            case LIMIT -> "LIMIT";
            case STOP -> "STOP_MARKET";
            case STOP_LIMIT -> "STOP";
            default -> throw new IllegalArgumentException("Unsupported Aster order type " + normalized);
        };
    }

    protected String toExchangeTimeInForce(OrderTicket order) {
        if (order.containsModifier(Modifier.POST_ONLY)) {
            return "GTX";
        }
        Duration duration = order.getDuration();
        if (duration == Duration.FILL_OR_KILL) {
            return "FOK";
        }
        if (duration == Duration.IMMEDIATE_OR_CANCEL) {
            return "IOC";
        }
        return "GTC";
    }

    protected String formatDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    protected void refreshOpenOrdersFromRest() {
        JsonNode response;
        try (var s = Span.start("AST_GET_OPEN_ORDERS_API_CALL", "N/A", LATENCY_LOGGER)) {
            response = restApi.getOpenOrders(null);
        }
        List<OrderTicket> openOrders = new ArrayList<>();
        if (response != null && response.isArray()) {
            for (JsonNode node : response) {
                OrderTicket order = applyOrderSnapshot(node, toTimestamp(node, "updateTime"), false);
                if (order != null && !isTerminalStatus(order.getCurrentStatus())) {
                    openOrders.add(order);
                }
            }
        }
        orderRegistry.replaceOpenOrders(openOrders);
    }

    protected void refreshPositionsFromRest() {
        JsonNode response;
        try (var s = Span.start("AST_GET_POSITIONS_API_CALL", "N/A", LATENCY_LOGGER)) {
            response = restApi.getPositionRisk(null);
        }
        Map<String, Position> refreshed = new LinkedHashMap<>();
        if (response != null && response.isArray()) {
            for (JsonNode node : response) {
                Position position = toPosition(node, true);
                if (position != null) {
                    refreshed.put(positionKey(position.getTicker(), position.getSide()), position);
                }
            }
        }
        positionsByKey.clear();
        positionsByKey.putAll(refreshed);
    }

    protected void refreshAccountSnapshotFromRest() {
        JsonNode response;
        try (var s = Span.start("AST_GET_ACCOUNT_INFO_API_CALL", "N/A", LATENCY_LOGGER)) {
            response = restApi.getAccountInformation();
        }
        if (response == null || response.isNull() || response.isMissingNode()) {
            return;
        }

        BigDecimal accountEquity = firstNonNull(decimalValue(response, "totalMarginBalance"),
                decimalValue(response, "totalWalletBalance"));
        BigDecimal availableFunds = decimalValue(response, "availableBalance");

        if (accountEquity != null) {
            fireAccountEquityUpdated(accountEquity.doubleValue());
        }
        if (availableFunds != null) {
            fireAvailableFundsUpdated(availableFunds.doubleValue());
        }
    }

    protected void onUserDataMessage(JsonNode message) {
        if (message == null) {
            return;
        }

        String eventType = text(message, "e");
        if ("ORDER_TRADE_UPDATE".equalsIgnoreCase(eventType)) {
            onOrderTradeUpdate(message);
        } else if ("ACCOUNT_UPDATE".equalsIgnoreCase(eventType)) {
            onAccountUpdate(message);
        } else if ("listenKeyExpired".equalsIgnoreCase(eventType)) {
            logger.warn("Aster listenKey expired. Restarting user stream.");
            restartUserDataStream();
        }
    }

    protected void onOrderTradeUpdate(JsonNode message) {
        long recvMs = System.currentTimeMillis();
        JsonNode orderNode = message.path("o");
        if (orderNode.isMissingNode() || orderNode.isNull()) {
            return;
        }

        ZonedDateTime eventTime = toTimestamp(message, "E");
        String clientOrderId = firstNonBlank(text(orderNode, "c"), text(orderNode, "clientOrderId"));
        long eventTimeMs = message.path("E").asLong(0);

        OrderTicket order = applyOrderSnapshot(orderNode, eventTime, true);
        if (order == null) {
            return;
        }

        String executionType = text(orderNode, "x");
        String orderStatus = text(orderNode, "X");

        try {
            if ("TRADE".equalsIgnoreCase(executionType)) {
                WsLatency.onMessage("AST-Fill", clientOrderId, recvMs, eventTimeMs, LATENCY_LOGGER);
            } else {
                WsLatency.onMessage("AST-OrderStatus(" + orderStatus + ")", clientOrderId, recvMs, eventTimeMs, LATENCY_LOGGER);
            }
        } catch (Exception e) {
            logger.debug("Error processing latency for order trade update", e);
        }

        if ("TRADE".equalsIgnoreCase(executionType)) {
            Fill fill = toFill(order, orderNode, eventTime);
            if (fill != null && processedFillIds.add(fillKey(fill))) {
                order.addFill(fill);
                order.setFilledSize(order.getFilledSizeFromFills());
                order.setFilledPrice(order.getAverageFillPriceFromFills());
                fireFillEvent(fill);
            }
        }
    }

    protected void onAccountUpdate(JsonNode message) {
        JsonNode account = message.path("a");
        if (account.isMissingNode() || account.isNull()) {
            return;
        }

        JsonNode positions = account.path("P");
        if (positions.isArray()) {
            for (JsonNode positionNode : positions) {
                Position position = toPosition(positionNode, false);
                if (position == null) {
                    continue;
                }
                String key = positionKey(position.getTicker(), position.getSide());
                if (position.getStatus() == Position.Status.CLOSED) {
                    positionsByKey.remove(key);
                } else {
                    positionsByKey.put(key, position);
                }
            }
        }

        // Account equity is polled from REST on a 1-second interval since the websocket
        // ACCOUNT_UPDATE only provides wb (wallet balance) which excludes margin in use.
    }

    protected synchronized void startKeepAliveTask() {
        stopKeepAliveTask();
        listenKeyKeepAliveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "aster-listen-key-keepalive");
            thread.setDaemon(true);
            return thread;
        });
        listenKeyKeepAliveExecutor.scheduleAtFixedRate(() -> {
            try {
                if (connected && listenKey != null && !listenKey.isBlank()) {
                    restApi.keepAliveUserDataStream(listenKey);
                }
            } catch (Exception e) {
                logger.warn("Failed to keep Aster listenKey alive", e);
            }
        }, USER_STREAM_KEEPALIVE_PERIOD_MINUTES, USER_STREAM_KEEPALIVE_PERIOD_MINUTES, TimeUnit.MINUTES);
    }

    protected synchronized void stopKeepAliveTask() {
        if (listenKeyKeepAliveExecutor != null) {
            listenKeyKeepAliveExecutor.shutdownNow();
            listenKeyKeepAliveExecutor = null;
        }
    }

    protected synchronized void startAccountSnapshotPoller() {
        stopAccountSnapshotPoller();
        accountSnapshotExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "aster-account-snapshot-poll");
            thread.setDaemon(true);
            return thread;
        });
        accountSnapshotExecutor.scheduleAtFixedRate(() -> {
            try {
                if (connected) {
                    refreshAccountSnapshotFromRest();
                }
            } catch (Exception e) {
                logger.warn("Failed to refresh Aster account snapshot", e);
            }
        }, ACCOUNT_SNAPSHOT_POLL_SECONDS, ACCOUNT_SNAPSHOT_POLL_SECONDS, TimeUnit.SECONDS);
    }

    protected synchronized void stopAccountSnapshotPoller() {
        if (accountSnapshotExecutor != null) {
            accountSnapshotExecutor.shutdownNow();
            accountSnapshotExecutor = null;
        }
    }

    protected synchronized void restartUserDataStream() {
        if (!connected) {
            return;
        }
        try {
            webSocketApi.disconnectUserData();
            listenKey = restApi.startUserDataStream();
            webSocketApi.subscribeUserData(listenKey, this::onUserDataMessage);
        } catch (Exception e) {
            logger.error("Failed to restart Aster user stream", e);
        }
    }

    protected OrderTicket applyOrderSnapshot(JsonNode source, ZonedDateTime timestamp, boolean fireEvent) {
        if (source == null || source.isNull() || source.isMissingNode()) {
            return null;
        }

        String orderId = firstNonBlank(text(source, "orderId"), text(source, "i"));
        String clientOrderId = firstNonBlank(text(source, "clientOrderId"), text(source, "c"));
        OrderTicket order = findTrackedOrder(orderId, clientOrderId);
        OrderStatus.Status previousStatus = order == null ? null : order.getCurrentStatus();
        BigDecimal previousFilledSize = order == null ? null : order.getFilledSize();
        BigDecimal previousFilledPrice = order == null ? null : order.getFilledPrice();

        if (order == null) {
            order = new OrderTicket();
        }

        populateOrder(order, source);
        ZonedDateTime effectiveTimestamp = timestamp == null ? getCurrentTime() : timestamp;

        if (isTerminalStatus(order.getCurrentStatus())) {
            orderRegistry.addCompletedOrder(order);
        } else {
            orderRegistry.addOpenOrder(order);
        }

        if (fireEvent && hasOrderChanged(previousStatus, previousFilledSize, previousFilledPrice, order)) {
            OrderStatus status = new OrderStatus(order.getCurrentStatus(), order.getOrderId(), order.getFilledSize(),
                    order.getRemainingSize(), order.getFilledPrice(), order.getTicker(), effectiveTimestamp);
            status.setClientOrderId(order.getClientOrderId());
            if (order.getCurrentStatus() == OrderStatus.Status.CANCELED && order.containsModifier(Modifier.POST_ONLY)
                    && order.getFilledSize().compareTo(BigDecimal.ZERO) == 0) {
                status.setCancelReason(CancelReason.POST_ONLY_WOULD_CROSS);
            }
            fireOrderEvent(new OrderEvent(order, status));
        }
        return order;
    }

    protected boolean hasOrderChanged(OrderStatus.Status previousStatus, BigDecimal previousFilledSize,
            BigDecimal previousFilledPrice, OrderTicket currentOrder) {
        if (previousStatus == null) {
            return true;
        }
        if (!Objects.equals(previousStatus, currentOrder.getCurrentStatus())) {
            return true;
        }
        if (!Objects.equals(previousFilledSize, currentOrder.getFilledSize())) {
            return true;
        }
        return !Objects.equals(previousFilledPrice, currentOrder.getFilledPrice());
    }

    protected void populateOrder(OrderTicket order, JsonNode source) {
        String symbol = firstNonBlank(text(source, "symbol"), text(source, "s"));
        if (!symbol.isBlank()) {
            order.setTicker(resolveTickerBySymbol(symbol));
        }

        String orderId = firstNonBlank(text(source, "orderId"), text(source, "i"));
        if (!orderId.isBlank()) {
            order.setOrderId(orderId);
        }

        String clientOrderId = firstNonBlank(text(source, "clientOrderId"), text(source, "c"));
        if (!clientOrderId.isBlank()) {
            order.setClientOrderId(clientOrderId);
        }

        String side = firstNonBlank(text(source, "side"), text(source, "S"));
        if (!side.isBlank()) {
            order.setTradeDirection("BUY".equalsIgnoreCase(side) ? TradeDirection.BUY : TradeDirection.SELL);
        }

        String type = firstNonBlank(text(source, "type"), text(source, "o"));
        if (!type.isBlank()) {
            order.setType(toOrderType(type));
        }

        String timeInForce = firstNonBlank(text(source, "timeInForce"), text(source, "f"));
        if (!timeInForce.isBlank()) {
            order.setDuration(toDuration(timeInForce));
            if ("GTX".equalsIgnoreCase(timeInForce) || "HIDDEN".equalsIgnoreCase(timeInForce)) {
                order.addModifier(Modifier.POST_ONLY);
            }
        }

        BigDecimal size = firstNonNull(decimalValue(source, "origQty"), decimalValue(source, "q"));
        if (size != null) {
            order.setSize(size);
        }

        BigDecimal limitPrice = firstNonNull(decimalValue(source, "price"), decimalValue(source, "p"));
        if (limitPrice != null && limitPrice.signum() > 0) {
            order.setLimitPrice(limitPrice);
        }

        BigDecimal stopPrice = firstNonNull(decimalValue(source, "stopPrice"), decimalValue(source, "sp"));
        if (stopPrice != null && stopPrice.signum() > 0) {
            order.setStopPrice(stopPrice);
        }

        BigDecimal filledSize = firstNonNull(decimalValue(source, "executedQty"), decimalValue(source, "z"));
        if (filledSize != null) {
            order.setFilledSize(filledSize);
        }

        BigDecimal filledPrice = firstNonNull(decimalValue(source, "avgPrice"), decimalValue(source, "ap"));
        if (filledPrice != null) {
            order.setFilledPrice(filledPrice);
        }

        BigDecimal commission = firstNonNull(decimalValue(source, "commission"), decimalValue(source, "n"));
        if (commission != null) {
            order.setCommission(commission.abs());
        }

        Boolean reduceOnly = firstNonNull(booleanValue(source, "reduceOnly"), booleanValue(source, "R"));
        if (Boolean.TRUE.equals(reduceOnly)) {
            order.addModifier(Modifier.REDUCE_ONLY);
        }

        String status = firstNonBlank(text(source, "status"), text(source, "X"));
        if (!status.isBlank()) {
            order.setCurrentStatus(toOrderStatus(status));
        }
    }

    protected OrderTicket findTrackedOrder(String orderId, String clientOrderId) {
        OrderTicket order = null;
        if (orderId != null && !orderId.isBlank()) {
            order = orderRegistry.getOpenOrderById(orderId);
            if (order == null) {
                order = orderRegistry.getCompletedOrderById(orderId);
            }
        }
        if (order == null && clientOrderId != null && !clientOrderId.isBlank()) {
            order = orderRegistry.getOpenOrderByClientId(clientOrderId);
            if (order == null) {
                order = orderRegistry.getCompletedOrderByClientId(clientOrderId);
            }
        }
        return order;
    }

    protected Fill toFill(OrderTicket order, JsonNode source, ZonedDateTime eventTime) {
        BigDecimal lastFillQty = decimalValue(source, "l");
        if (lastFillQty == null || lastFillQty.signum() <= 0) {
            return null;
        }

        BigDecimal lastFillPrice = decimalValue(source, "L");
        if (lastFillPrice == null || lastFillPrice.signum() <= 0) {
            lastFillPrice = order.getFilledPrice();
        }

        Fill fill = new Fill();
        fill.setTicker(order.getTicker());
        fill.setOrderId(order.getOrderId());
        fill.setClientOrderId(order.getClientOrderId());
        fill.setPrice(lastFillPrice);
        fill.setSize(lastFillQty);
        fill.setTime(eventTime);
        fill.setCommission(firstNonNull(decimalValue(source, "n"), BigDecimal.ZERO).abs());
        fill.setFillId(text(source, "t"));
        fill.setTaker(!source.path("m").asBoolean(false));
        fill.setSide(order.isBuyOrder() ? TradeDirection.BUY : TradeDirection.SELL);
        return fill;
    }

    protected void markOrderCanceled(OrderTicket order, CancelReason cancelReason, ZonedDateTime timestamp,
            boolean fireEvent) {
        order.setCurrentStatus(OrderStatus.Status.CANCELED);
        orderRegistry.addCompletedOrder(order);
        if (fireEvent) {
            OrderStatus status = new OrderStatus(OrderStatus.Status.CANCELED, order.getOrderId(), order.getFilledSize(),
                    order.getRemainingSize(), order.getFilledPrice(), order.getTicker(), timestamp);
            status.setClientOrderId(order.getClientOrderId());
            status.setCancelReason(cancelReason == null ? CancelReason.NONE : cancelReason);
            fireOrderEvent(new OrderEvent(order, status));
        }
    }

    protected Position toPosition(JsonNode source, boolean includeLiquidationPrice) {
        String symbol = text(source, "symbol", "s");
        if (symbol.isBlank()) {
            return null;
        }

        BigDecimal rawSize = firstNonNull(decimalValue(source, "positionAmt"), decimalValue(source, "pa"));
        if (rawSize == null) {
            return null;
        }

        String positionSide = firstNonBlank(text(source, "positionSide"), text(source, "ps"));
        Side side = resolvePositionSide(positionSide, rawSize);
        BigDecimal normalizedSize = rawSize.abs();

        Position position = new Position(resolveTickerBySymbol(symbol), side, normalizedSize,
                firstNonNull(decimalValue(source, "entryPrice"), decimalValue(source, "ep"), BigDecimal.ZERO),
                normalizedSize.signum() == 0 ? Position.Status.CLOSED : Position.Status.OPEN);
        if (includeLiquidationPrice) {
            position.setLiquidationPrice(decimalValue(source, "liquidationPrice"));
        }
        return position;
    }

    protected Side resolvePositionSide(String positionSide, BigDecimal size) {
        if ("SHORT".equalsIgnoreCase(positionSide)) {
            return Side.SHORT;
        }
        if ("LONG".equalsIgnoreCase(positionSide)) {
            return Side.LONG;
        }
        return size != null && size.signum() < 0 ? Side.SHORT : Side.LONG;
    }

    protected String positionKey(Ticker ticker, Side side) {
        String symbol = ticker == null ? "" : ticker.getSymbol();
        return symbol + "|" + (side == null ? Side.LONG : side);
    }

    protected Ticker resolveTicker(Ticker ticker) {
        if (ticker == null) {
            return null;
        }
        Ticker resolved = tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, ticker.getSymbol());
        if (resolved != null) {
            return resolved;
        }
        resolved = tickerRegistry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, ticker.getSymbol());
        if (resolved != null) {
            return resolved;
        }
        return ticker;
    }

    protected Ticker resolveTickerBySymbol(String symbol) {
        Ticker ticker = tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (ticker != null) {
            return ticker;
        }
        ticker = tickerRegistry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, symbol);
        if (ticker != null) {
            return ticker;
        }
        return new Ticker(symbol)
                .setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setFundingRateInterval(8);
    }

    protected Type toOrderType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.US);
        return switch (normalized) {
            case "LIMIT" -> Type.LIMIT;
            case "STOP" -> Type.STOP_LIMIT;
            case "STOP_MARKET", "TAKE_PROFIT", "TAKE_PROFIT_MARKET", "TRAILING_STOP_MARKET" -> Type.STOP;
            default -> Type.MARKET;
        };
    }

    protected Duration toDuration(String timeInForce) {
        String normalized = timeInForce == null ? "" : timeInForce.trim().toUpperCase(Locale.US);
        return switch (normalized) {
            case "IOC" -> Duration.IMMEDIATE_OR_CANCEL;
            case "FOK" -> Duration.FILL_OR_KILL;
            default -> Duration.GOOD_UNTIL_CANCELED;
        };
    }

    protected OrderStatus.Status toOrderStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.US);
        return switch (normalized) {
            case "NEW" -> OrderStatus.Status.NEW;
            case "PARTIALLY_FILLED" -> OrderStatus.Status.PARTIAL_FILL;
            case "FILLED" -> OrderStatus.Status.FILLED;
            case "CANCELED", "EXPIRED" -> OrderStatus.Status.CANCELED;
            case "REJECTED" -> OrderStatus.Status.REJECTED;
            default -> OrderStatus.Status.UNKNOWN;
        };
    }

    protected boolean isTerminalStatus(OrderStatus.Status status) {
        return status == OrderStatus.Status.FILLED || status == OrderStatus.Status.CANCELED
                || status == OrderStatus.Status.REJECTED;
    }

    protected BigDecimal decimalValue(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    protected BigDecimal firstNonNull(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected Boolean firstNonNull(Boolean left, Boolean right) {
        return left != null ? left : right;
    }

    protected Boolean booleanValue(JsonNode node, String field) {
        if (node == null || field == null || !node.has(field)) {
            return null;
        }
        return node.path(field).asBoolean();
    }

    protected String text(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            if (field == null) {
                continue;
            }
            String value = node.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    protected String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right == null ? "" : right;
    }

    protected ZonedDateTime toTimestamp(JsonNode node, String field) {
        long epochMillis = node == null ? 0L : node.path(field).asLong(0L);
        if (epochMillis <= 0L) {
            return ZonedDateTime.now(ZoneId.of("UTC"));
        }
        return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.of("UTC"));
    }

    protected String fillKey(Fill fill) {
        String fillId = fill.getFillId();
        if (fillId != null && !fillId.isBlank()) {
            return fill.getOrderId() + "|" + fillId;
        }
        return fill.getOrderId() + "|" + fill.getTime() + "|" + fill.getPrice() + "|" + fill.getSize();
    }

    protected String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    protected static final class PreparedOrder {
        private final BrokerRequestResult validationResult;
        private final Ticker resolvedTicker;
        private final BigDecimal normalizedSize;
        private final BigDecimal normalizedLimitPrice;
        private final BigDecimal normalizedStopPrice;

        private PreparedOrder(BrokerRequestResult validationResult, Ticker resolvedTicker, BigDecimal normalizedSize,
                BigDecimal normalizedLimitPrice, BigDecimal normalizedStopPrice) {
            this.validationResult = validationResult;
            this.resolvedTicker = resolvedTicker;
            this.normalizedSize = normalizedSize;
            this.normalizedLimitPrice = normalizedLimitPrice;
            this.normalizedStopPrice = normalizedStopPrice;
        }

        private static PreparedOrder success(Ticker resolvedTicker, BigDecimal normalizedSize,
                BigDecimal normalizedLimitPrice, BigDecimal normalizedStopPrice) {
            return new PreparedOrder(new BrokerRequestResult(), resolvedTicker, normalizedSize, normalizedLimitPrice,
                    normalizedStopPrice);
        }

        private static PreparedOrder failed(BrokerRequestResult validationResult) {
            return new PreparedOrder(validationResult, null, null, null, null);
        }
    }
}
