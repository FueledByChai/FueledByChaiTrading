package com.fueledbychai.broker.drift;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.drift.common.api.DriftConfiguration;
import com.fueledbychai.drift.common.api.IDriftRestApi;
import com.fueledbychai.drift.common.api.IDriftWebSocketApi;
import com.fueledbychai.drift.common.api.model.DriftGatewayCancelRequest;
import com.fueledbychai.drift.common.api.model.DriftGatewayOrder;
import com.fueledbychai.drift.common.api.model.DriftGatewayPosition;
import com.fueledbychai.drift.common.api.model.DriftGatewayWebSocketEvent;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;
import com.fueledbychai.util.FillDeduper;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;
import com.google.gson.JsonObject;

public class DriftBroker extends AbstractBasicBroker {

    protected static final Logger logger = LoggerFactory.getLogger(DriftBroker.class);
    protected static final ZoneId UTC = ZoneId.of("UTC");
    protected static final int MIN_USER_ORDER_ID = 1;
    protected static final int MAX_USER_ORDER_ID = 255;

    protected final IDriftRestApi restApi;
    protected final IDriftWebSocketApi webSocketApi;
    protected final ITickerRegistry tickerRegistry;
    protected final DriftTranslator translator;
    protected final FillDeduper fillDeduper = new FillDeduper();
    protected final Map<String, Integer> clientOrderIdToUserOrderId = new ConcurrentHashMap<>();
    protected final Map<Integer, String> userOrderIdToClientOrderId = new ConcurrentHashMap<>();
    protected final Map<Long, String> exchangeOrderIdToClientOrderId = new ConcurrentHashMap<>();
    protected final AtomicInteger nextUserOrderId = new AtomicInteger(MIN_USER_ORDER_ID);
    protected final AtomicLong nextClientOrderId = new AtomicLong(System.currentTimeMillis());

    protected final int subAccountId;
    protected volatile boolean connected;

    public DriftBroker() {
        this(ExchangeRestApiFactory.getPrivateApi(Exchange.DRIFT, IDriftRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.DRIFT, IDriftWebSocketApi.class),
                TickerRegistryFactory.getInstance(Exchange.DRIFT), DriftConfiguration.getInstance().getSubAccountId());

        DriftConfiguration configuration = DriftConfiguration.getInstance();
        logger.info("DriftBroker initialized with configuration: environment={}, dataRestUrl={}, gatewayRestUrl={}, "
                + "gatewayWsUrl={}, subAccountId={}", configuration.getEnvironment(), configuration.getDataRestUrl(),
                configuration.getGatewayRestUrl(), configuration.getGatewayWebSocketUrl(), subAccountId);
    }

    public DriftBroker(IDriftRestApi restApi, IDriftWebSocketApi webSocketApi, ITickerRegistry tickerRegistry,
            int subAccountId) {
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
        this.translator = new DriftTranslator(tickerRegistry);
        this.subAccountId = subAccountId;
    }

    @Override
    public String getBrokerName() {
        return "Drift";
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        checkConnected();
        if (id == null || id.isBlank()) {
            return validationFailed("order id is required");
        }

        OrderTicket trackedOrder = orderRegistry.getOrderById(id);
        if (trackedOrder == null) {
            trackedOrder = orderRegistry.getOrderByClientId(id);
        }
        if (trackedOrder != null) {
            return cancelOrder(trackedOrder);
        }

        Long orderId = parsePositiveLongOrNull(id);
        if (orderId == null) {
            return notFound("Order not found for id " + id);
        }

        try {
            restApi.cancelOrder(new DriftGatewayCancelRequest(null, null, List.of(orderId), Collections.emptyList()));
            String clientOrderId = exchangeOrderIdToClientOrderId.get(orderId);
            if (clientOrderId != null) {
                OrderTicket byClientOrderId = orderRegistry.getOrderByClientId(clientOrderId);
                if (byClientOrderId != null) {
                    firePendingCancel(byClientOrderId);
                }
            }
            return new BrokerRequestResult();
        } catch (Exception ex) {
            logger.error("Error canceling Drift order {}", id, ex);
            return failure(ex.getMessage());
        }
    }

    @Override
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        checkConnected();
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return validationFailed("clientOrderId is required");
        }

        OrderTicket trackedOrder = orderRegistry.getOrderByClientId(clientOrderId);
        if (trackedOrder != null) {
            return cancelOrder(trackedOrder);
        }

        Integer userOrderId = clientOrderIdToUserOrderId.get(clientOrderId);
        if (userOrderId == null) {
            return notFound("Order not found for clientOrderId " + clientOrderId);
        }

        try {
            restApi.cancelOrder(
                    new DriftGatewayCancelRequest(null, null, Collections.emptyList(), List.of(userOrderId)));
            releaseUserOrderId(clientOrderId, userOrderId);
            return new BrokerRequestResult();
        } catch (Exception ex) {
            logger.error("Error canceling Drift order with clientOrderId={}", clientOrderId, ex);
            return failure(ex.getMessage());
        }
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        checkConnected();
        if (order == null) {
            return validationFailed("order is required");
        }

        OrderTicket trackedOrder = resolveTrackedOrder(order);
        Long orderId = resolveExchangeOrderId(trackedOrder == null ? order : trackedOrder);
        Integer userOrderId = resolveUserOrderId(trackedOrder == null ? order : trackedOrder);

        if (orderId == null && userOrderId == null) {
            return notFound("Unable to resolve order identifiers for cancel");
        }

        try {
            restApi.cancelOrder(new DriftGatewayCancelRequest(null, null,
                    orderId == null ? Collections.emptyList() : List.of(orderId),
                    userOrderId == null ? Collections.emptyList() : List.of(userOrderId)));
            if (trackedOrder != null) {
                firePendingCancel(trackedOrder);
            }
            return new BrokerRequestResult();
        } catch (Exception ex) {
            logger.error("Error canceling Drift order {}", order.getClientOrderId(), ex);
            return failure(ex.getMessage());
        }
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        checkConnected();
        if (ticker == null) {
            return validationFailed("ticker is required");
        }

        try {
            restApi.cancelAllOrders(Integer.valueOf(ticker.getIdAsInt()), translator.toMarketType(ticker));
            for (OrderTicket openOrder : orderRegistry.getOpenOrdersByTicker(ticker)) {
                firePendingCancel(openOrder);
            }
            return new BrokerRequestResult();
        } catch (Exception ex) {
            logger.error("Error canceling all Drift orders for {}", ticker.getSymbol(), ex);
            return failure(ex.getMessage());
        }
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        checkConnected();
        try {
            restApi.cancelAllOrders(null, null);
            for (OrderTicket openOrder : orderRegistry.getOpenOrders()) {
                firePendingCancel(openOrder);
            }
            return new BrokerRequestResult();
        } catch (Exception ex) {
            logger.error("Error canceling all Drift orders", ex);
            return failure(ex.getMessage());
        }
    }

    @Override
    public BrokerRequestResult cancelOrders(java.util.List<OrderTicket> orders) {
        throw new UnsupportedOperationException("Batch cancel not supported by DriftBroker");
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        checkConnected();
        if (order == null) {
            return validationFailed("order is required");
        }

        ensureClientOrderId(order);
        Integer userOrderId = reserveUserOrderId(order.getClientOrderId());

        try {
            order.setOrderEntryTime(getCurrentTime());
            orderRegistry.addOpenOrder(order);
            restApi.placeOrder(translator.toGatewayOrderRequest(order, userOrderId, null));
            return new BrokerRequestResult();
        } catch (IllegalArgumentException | UnsupportedOperationException ex) {
            releaseUserOrderId(order.getClientOrderId(), userOrderId);
            rejectOrder(order);
            return validationFailed(ex.getMessage());
        } catch (Exception ex) {
            releaseUserOrderId(order.getClientOrderId(), userOrderId);
            rejectOrder(order);
            logger.error("Error placing Drift order {}", order.getClientOrderId(), ex);
            return failure(ex.getMessage());
        }
    }

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        checkConnected();
        if (order == null) {
            return validationFailed("order is required");
        }

        OrderTicket trackedOrder = resolveTrackedOrder(order);
        OrderTicket orderToModify = trackedOrder == null ? order : trackedOrder;
        mergeOrder(orderToModify, order);

        Long orderId = resolveExchangeOrderId(orderToModify);
        Integer userOrderId = resolveUserOrderId(orderToModify);
        if (orderId == null && userOrderId == null) {
            return notFound("Unable to resolve order identifiers for modify");
        }

        try {
            orderToModify.setOrderEntryTime(getCurrentTime());
            restApi.modifyOrder(translator.toGatewayOrderRequest(orderToModify, userOrderId, orderId));
            orderRegistry.addOpenOrder(orderToModify);
            return new BrokerRequestResult();
        } catch (IllegalArgumentException | UnsupportedOperationException ex) {
            return validationFailed(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Error modifying Drift order {}", orderToModify.getClientOrderId(), ex);
            return failure(ex.getMessage());
        }
    }

    @Override
    public String getNextOrderId() {
        return String.valueOf(nextClientOrderId.incrementAndGet());
    }

    @Override
    public void connect() {
        if (connected) {
            return;
        }

        webSocketApi.subscribeGatewayEvents(subAccountId, this::handleGatewayEvent);
        connected = true;
        refreshAccountSnapshot();
        try {
            getOpenOrders();
        } catch (Exception ex) {
            logger.warn("Unable to prefetch Drift open orders on connect", ex);
        }
    }

    @Override
    protected void onDisconnect() {
        connected = false;
        webSocketApi.disconnectAll();
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
    public OrderTicket requestOrderStatus(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }

        OrderTicket trackedOrder = orderRegistry.getOrderById(orderId);
        if (trackedOrder != null) {
            return trackedOrder;
        }

        for (OrderTicket openOrder : getOpenOrders()) {
            if (orderId.equals(openOrder.getOrderId())) {
                return openOrder;
            }
        }

        return orderRegistry.getCompletedOrderById(orderId);
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return null;
        }

        OrderTicket trackedOrder = orderRegistry.getOrderByClientId(clientOrderId);
        if (trackedOrder != null) {
            return trackedOrder;
        }

        for (OrderTicket openOrder : getOpenOrders()) {
            if (clientOrderId.equals(openOrder.getClientOrderId())) {
                return openOrder;
            }
        }

        return orderRegistry.getCompletedOrderByClientId(clientOrderId);
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        checkConnected();
        List<DriftGatewayOrder> gatewayOrders = restApi.getOpenOrders();
        if (gatewayOrders == null || gatewayOrders.isEmpty()) {
            return Collections.emptyList();
        }

        List<OrderTicket> translated = new ArrayList<>();
        for (DriftGatewayOrder gatewayOrder : gatewayOrders) {
            if (gatewayOrder == null) {
                continue;
            }
            String clientOrderId = resolveClientOrderId(gatewayOrder.getOrderId(), gatewayOrder.getUserOrderId(), null);
            if ((clientOrderId == null || clientOrderId.isBlank()) && gatewayOrder.getOrderId() != null) {
                clientOrderId = String.valueOf(gatewayOrder.getOrderId());
            }
            OrderTicket order = translator.toOrderTicket(gatewayOrder, clientOrderId);
            if (order == null) {
                continue;
            }
            if (order.getClientOrderId() == null || order.getClientOrderId().isBlank()) {
                order.setClientOrderId(defaultClientOrderId(gatewayOrder.getOrderId(), gatewayOrder.getUserOrderId()));
            }
            indexOrderIdentifiers(order.getClientOrderId(), gatewayOrder.getUserOrderId(), gatewayOrder.getOrderId());
            translated.add(order);
        }

        if (!translated.isEmpty()) {
            orderRegistry.replaceOpenOrders(translated);
        }
        return translated;
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        BrokerRequestResult cancelResult = cancelOrder(originalOrderId);
        if (!cancelResult.isSuccess()) {
            throw new IllegalStateException(cancelResult.getMessage());
        }
        BrokerRequestResult placeResult = placeOrder(newOrder);
        if (!placeResult.isSuccess()) {
            throw new IllegalStateException(placeResult.getMessage());
        }
    }

    @Override
    public List<Position> getAllPositions() {
        checkConnected();
        List<DriftGatewayPosition> gatewayPositions = restApi.getPositions();
        if (gatewayPositions == null || gatewayPositions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Position> translated = new ArrayList<>();
        for (DriftGatewayPosition gatewayPosition : gatewayPositions) {
            if (gatewayPosition == null || gatewayPosition.getAmount() == null
                    || gatewayPosition.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Position position = translator.toPosition(gatewayPosition);
            if (position != null) {
                translated.add(position);
            }
        }
        return translated;
    }

    protected void handleGatewayEvent(DriftGatewayWebSocketEvent event) {
        if (event == null || event.getPayload() == null) {
            return;
        }

        String eventType = event.getEventType();
        if (eventType == null || eventType.isBlank()) {
            logger.debug("Ignoring Drift gateway event without eventType on channel {}", event.getChannel());
            return;
        }

        switch (eventType) {
        case "orderCreate" -> handleOrderCreate(event.getPayload());
        case "orderCancel" -> handleOrderTerminal(event.getPayload(), OrderStatus.Status.CANCELED,
                OrderStatus.CancelReason.USER_CANCELED);
        case "orderExpire" -> handleOrderTerminal(event.getPayload(), OrderStatus.Status.CANCELED,
                OrderStatus.CancelReason.UNKNOWN);
        case "orderCancelMissing" -> handleOrderTerminal(event.getPayload(), OrderStatus.Status.CANCELED,
                OrderStatus.CancelReason.USER_CANCELED);
        case "fill" -> handleFill(event.getPayload());
        case "fundingPayment" -> refreshAccountSnapshot();
        default -> logger.debug("Ignoring unsupported Drift gateway event type {}", eventType);
        }
    }

    protected void handleOrderCreate(JsonObject payload) {
        JsonObject orderPayload = getObject(payload, "order");
        DriftGatewayOrder gatewayOrder = translator.toGatewayOrder(orderPayload);
        if (gatewayOrder == null) {
            return;
        }

        String clientOrderId = resolveClientOrderId(gatewayOrder.getOrderId(), gatewayOrder.getUserOrderId(), null);
        if ((clientOrderId == null || clientOrderId.isBlank()) && gatewayOrder.getOrderId() != null) {
            clientOrderId = String.valueOf(gatewayOrder.getOrderId());
        }

        OrderTicket translatedOrder = translator.toOrderTicket(gatewayOrder, clientOrderId);
        if (translatedOrder == null) {
            return;
        }
        if (translatedOrder.getClientOrderId() == null || translatedOrder.getClientOrderId().isBlank()) {
            translatedOrder.setClientOrderId(defaultClientOrderId(gatewayOrder.getOrderId(), gatewayOrder.getUserOrderId()));
        }

        OrderTicket trackedOrder = resolveTrackedOrder(translatedOrder);
        mergeOrder(trackedOrder, translatedOrder);
        trackedOrder.setCurrentStatus(OrderStatus.Status.NEW);
        trackedOrder.setOrderEntryTime(resolveTimestamp(payload));

        indexOrderIdentifiers(trackedOrder.getClientOrderId(), gatewayOrder.getUserOrderId(), gatewayOrder.getOrderId());
        releaseUserOrderId(trackedOrder.getClientOrderId(), gatewayOrder.getUserOrderId());

        orderRegistry.addOpenOrder(trackedOrder);
        fireOrderStatus(trackedOrder, OrderStatus.Status.NEW, resolveTimestamp(payload), OrderStatus.CancelReason.NONE);
    }

    protected void handleOrderTerminal(JsonObject payload, OrderStatus.Status status,
            OrderStatus.CancelReason cancelReason) {
        Long orderId = getLongObject(payload, "orderId");
        Integer userOrderId = getInteger(payload, "userOrderId");
        String clientOrderId = resolveClientOrderId(orderId, userOrderId, null);
        OrderTicket trackedOrder = resolveTrackedOrder(orderId, clientOrderId);
        if (trackedOrder == null) {
            releaseUserOrderId(clientOrderId, userOrderId);
            return;
        }

        trackedOrder.setCurrentStatus(status);
        orderRegistry.addCompletedOrder(trackedOrder);
        fireOrderStatus(trackedOrder, status, resolveTimestamp(payload), cancelReason);
        releaseUserOrderId(trackedOrder.getClientOrderId(), userOrderId);
    }

    protected void handleFill(JsonObject payload) {
        Long orderId = getLongObject(payload, "orderId");
        if (orderId == null) {
            orderId = getLongObject(payload, "takerOrderId");
        }
        String clientOrderId = resolveClientOrderId(orderId, null, null);
        Fill fill = translator.toFill(payload, clientOrderId);
        if (fill == null || fill.getFillId() == null || !fillDeduper.firstTime(fill.getFillId())) {
            return;
        }

        OrderTicket trackedOrder = resolveTrackedOrder(fill.getOrderId(), fill.getClientOrderId());
        if (trackedOrder != null) {
            trackedOrder.addFill(fill);
            trackedOrder.setFilledSize(trackedOrder.getFilledSize().add(zeroIfNull(fill.getSize())));
            trackedOrder.setFilledPrice(trackedOrder.getAverageFillPriceFromFills());
            trackedOrder.setCommission(trackedOrder.getCommission().add(zeroIfNull(fill.getCommission())));

            OrderStatus.Status status = trackedOrder.getSize() != null
                    && trackedOrder.getFilledSize().compareTo(trackedOrder.getSize()) >= 0 ? OrderStatus.Status.FILLED
                            : OrderStatus.Status.PARTIAL_FILL;
            trackedOrder.setCurrentStatus(status);
            if (status == OrderStatus.Status.FILLED) {
                trackedOrder.setOrderFilledTime(fill.getTime());
                orderRegistry.addCompletedOrder(trackedOrder);
                releaseUserOrderId(trackedOrder.getClientOrderId(), clientOrderIdToUserOrderId.get(trackedOrder.getClientOrderId()));
            } else {
                orderRegistry.addOpenOrder(trackedOrder);
            }
            fireOrderStatus(trackedOrder, status, fill.getTime(), OrderStatus.CancelReason.NONE);
        }

        if (fill.getClientOrderId() == null || fill.getClientOrderId().isBlank()) {
            Long parsedOrderId = parsePositiveLongOrNull(fill.getOrderId());
            String resolvedClientOrderId = trackedOrder == null
                    ? (parsedOrderId == null ? null : exchangeOrderIdToClientOrderId.get(parsedOrderId))
                    : trackedOrder.getClientOrderId();
            if (resolvedClientOrderId != null) {
                fill.setClientOrderId(resolvedClientOrderId);
            }
        }
        if (fill.getOrderId() != null) {
            Long parsedOrderId = parsePositiveLongOrNull(fill.getOrderId());
            if (parsedOrderId != null && fill.getClientOrderId() != null && !fill.getClientOrderId().isBlank()) {
                exchangeOrderIdToClientOrderId.put(parsedOrderId, fill.getClientOrderId());
            }
        }

        fireFillEvent(fill);
        refreshAccountSnapshot();
    }

    protected void refreshAccountSnapshot() {
        try {
            BigDecimal totalCollateral = restApi.getTotalCollateral();
            if (totalCollateral != null) {
                fireAccountEquityUpdated(totalCollateral.doubleValue());
            }
            BigDecimal freeCollateral = restApi.getFreeCollateral();
            if (freeCollateral != null) {
                fireAvailableFundsUpdated(freeCollateral.doubleValue());
            }
        } catch (Exception ex) {
            logger.warn("Unable to refresh Drift account snapshot", ex);
        }
    }

    protected void firePendingCancel(OrderTicket order) {
        if (order == null) {
            return;
        }
        order.setCurrentStatus(OrderStatus.Status.PENDING_CANCEL);
        fireOrderStatus(order, OrderStatus.Status.PENDING_CANCEL, getCurrentTime(), OrderStatus.CancelReason.NONE);
    }

    protected void fireOrderStatus(OrderTicket order, OrderStatus.Status status, ZonedDateTime timestamp,
            OrderStatus.CancelReason cancelReason) {
        if (order == null) {
            return;
        }
        OrderStatus orderStatus = new OrderStatus(status, order.getOrderId(), zeroIfNull(order.getFilledSize()),
                zeroIfNull(order.getRemainingSize()), zeroIfNull(order.getFilledPrice()), order.getTicker(),
                timestamp == null ? getCurrentTime() : timestamp);
        orderStatus.setClientOrderId(order.getClientOrderId());
        orderStatus.setCancelReason(cancelReason);
        super.fireOrderEvent(new OrderEvent(order, orderStatus));
    }

    protected void rejectOrder(OrderTicket order) {
        if (order == null) {
            return;
        }
        order.setCurrentStatus(OrderStatus.Status.REJECTED);
        orderRegistry.addCompletedOrder(order);
    }

    protected void ensureClientOrderId(OrderTicket order) {
        if (order.getClientOrderId() == null || order.getClientOrderId().isBlank()) {
            order.setClientOrderId(getNextOrderId());
        }
    }

    protected Integer reserveUserOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return null;
        }
        Integer existing = clientOrderIdToUserOrderId.get(clientOrderId);
        if (existing != null) {
            return existing;
        }
        for (int i = 0; i < MAX_USER_ORDER_ID; i++) {
            int candidate = normalizeUserOrderId(nextUserOrderId.getAndIncrement());
            if (userOrderIdToClientOrderId.putIfAbsent(candidate, clientOrderId) == null) {
                clientOrderIdToUserOrderId.put(clientOrderId, candidate);
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a Drift userOrderId; all 255 ids are in use");
    }

    protected void releaseUserOrderId(String clientOrderId, Integer userOrderId) {
        if (clientOrderId != null && !clientOrderId.isBlank()) {
            Integer removedUserOrderId = clientOrderIdToUserOrderId.remove(clientOrderId);
            if (removedUserOrderId != null) {
                userOrderIdToClientOrderId.remove(removedUserOrderId, clientOrderId);
            }
        }
        if (userOrderId != null) {
            String mappedClientOrderId = userOrderIdToClientOrderId.remove(userOrderId);
            if (mappedClientOrderId != null) {
                clientOrderIdToUserOrderId.remove(mappedClientOrderId, userOrderId);
            }
        }
    }

    protected int normalizeUserOrderId(int rawValue) {
        int offset = Math.floorMod(rawValue - MIN_USER_ORDER_ID, MAX_USER_ORDER_ID);
        return MIN_USER_ORDER_ID + offset;
    }

    protected void indexOrderIdentifiers(String clientOrderId, Integer userOrderId, Long orderId) {
        if (clientOrderId != null && !clientOrderId.isBlank()) {
            if (userOrderId != null) {
                clientOrderIdToUserOrderId.put(clientOrderId, userOrderId);
                userOrderIdToClientOrderId.put(userOrderId, clientOrderId);
            }
            if (orderId != null) {
                exchangeOrderIdToClientOrderId.put(orderId, clientOrderId);
            }
        }
    }

    protected Long resolveExchangeOrderId(OrderTicket order) {
        if (order == null) {
            return null;
        }
        Long fromOrderId = parsePositiveLongOrNull(order.getOrderId());
        if (fromOrderId != null) {
            return fromOrderId;
        }
        if (order.getClientOrderId() == null || order.getClientOrderId().isBlank()) {
            return null;
        }
        for (Map.Entry<Long, String> entry : exchangeOrderIdToClientOrderId.entrySet()) {
            if (order.getClientOrderId().equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    protected Integer resolveUserOrderId(OrderTicket order) {
        if (order == null || order.getClientOrderId() == null || order.getClientOrderId().isBlank()) {
            return null;
        }
        return clientOrderIdToUserOrderId.get(order.getClientOrderId());
    }

    protected String resolveClientOrderId(Long orderId, Integer userOrderId, String fallback) {
        if (orderId != null) {
            String clientOrderId = exchangeOrderIdToClientOrderId.get(orderId);
            if (clientOrderId != null && !clientOrderId.isBlank()) {
                return clientOrderId;
            }
        }
        if (userOrderId != null) {
            String clientOrderId = userOrderIdToClientOrderId.get(userOrderId);
            if (clientOrderId != null && !clientOrderId.isBlank()) {
                return clientOrderId;
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return defaultClientOrderId(orderId, userOrderId);
    }

    protected String defaultClientOrderId(Long orderId, Integer userOrderId) {
        if (orderId != null) {
            return String.valueOf(orderId);
        }
        return userOrderId == null ? null : "drift-" + userOrderId;
    }

    protected OrderTicket resolveTrackedOrder(OrderTicket translatedOrder) {
        if (translatedOrder == null) {
            return null;
        }
        return resolveTrackedOrder(parsePositiveLongOrNull(translatedOrder.getOrderId()), translatedOrder.getClientOrderId(),
                translatedOrder);
    }

    protected OrderTicket resolveTrackedOrder(String orderId, String clientOrderId) {
        return resolveTrackedOrder(parsePositiveLongOrNull(orderId), clientOrderId, null);
    }

    protected OrderTicket resolveTrackedOrder(Long orderId, String clientOrderId) {
        return resolveTrackedOrder(orderId, clientOrderId, null);
    }

    protected OrderTicket resolveTrackedOrder(Long orderId, String clientOrderId, OrderTicket defaultOrder) {
        if (orderId != null) {
            OrderTicket byOrderId = orderRegistry.getOrderById(String.valueOf(orderId));
            if (byOrderId != null) {
                return byOrderId;
            }
        }
        if (clientOrderId != null && !clientOrderId.isBlank()) {
            OrderTicket byClientOrderId = orderRegistry.getOrderByClientId(clientOrderId);
            if (byClientOrderId != null) {
                return byClientOrderId;
            }
        }
        return defaultOrder;
    }

    protected void mergeOrder(OrderTicket target, OrderTicket source) {
        if (target == null || source == null || target == source) {
            return;
        }
        if (source.getOrderId() != null && !source.getOrderId().isBlank()) {
            target.setOrderId(source.getOrderId());
        }
        if (source.getClientOrderId() != null && !source.getClientOrderId().isBlank()) {
            target.setClientOrderId(source.getClientOrderId());
        }
        if (source.getTicker() != null) {
            target.setTicker(source.getTicker());
        }
        if (source.getDirection() != null) {
            target.setDirection(source.getDirection());
        }
        if (source.getType() != null) {
            target.setType(source.getType());
        }
        if (source.getDuration() != null) {
            target.setDuration(source.getDuration());
        }
        if (source.getSize() != null) {
            target.setSize(source.getSize());
        }
        if (source.getLimitPrice() != null) {
            target.setLimitPrice(source.getLimitPrice());
        }
        if (source.getStopPriceAsBigDecimal() != null) {
            target.setStopPrice(source.getStopPriceAsBigDecimal());
        }
        if (source.getOrderEntryTime() != null) {
            target.setOrderEntryTime(source.getOrderEntryTime());
        }
        if (source.getGoodUntilTime() != null) {
            target.setGoodUntilTime(source.getGoodUntilTime());
        }
        for (OrderTicket.Modifier modifier : source.getModifiers()) {
            target.addModifier(modifier);
        }
    }

    protected void checkConnected() {
        if (!connected) {
            logger.warn("Drift broker API method called while broker is not connected");
        }
    }

    protected BrokerRequestResult validationFailed(String message) {
        return new BrokerRequestResult(false, true, message, BrokerRequestResult.FailureType.VALIDATION_FAILED);
    }

    protected BrokerRequestResult notFound(String message) {
        return new BrokerRequestResult(false, true, message, BrokerRequestResult.FailureType.ORDER_NOT_FOUND);
    }

    protected BrokerRequestResult failure(String message) {
        return new BrokerRequestResult(false, true, message, BrokerRequestResult.FailureType.UNKNOWN);
    }

    protected Long parsePositiveLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? Long.valueOf(parsed) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected JsonObject getObject(JsonObject object, String field) {
        if (object == null || field == null || !object.has(field) || !object.get(field).isJsonObject()) {
            return new JsonObject();
        }
        return object.getAsJsonObject(field);
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

    protected ZonedDateTime resolveTimestamp(JsonObject payload) {
        if (payload == null || !payload.has("ts") || payload.get("ts").isJsonNull()) {
            return ZonedDateTime.now(UTC);
        }
        long rawTs = payload.get("ts").getAsLong();
        return rawTs >= 1_000_000_000_000L ? ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(rawTs), UTC)
                : ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(rawTs), UTC);
    }

    protected BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
