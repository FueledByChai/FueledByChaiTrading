package com.fueledbychai.broker.lighter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.ILighterRestApi;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.LighterConfiguration;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStats;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStatsUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrdersUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterSendTxResponse;
import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;
import com.fueledbychai.lighter.common.api.ws.model.LighterTradesUpdate;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ExchangeWebSocketApiFactory;
import com.fueledbychai.util.FillDeduper;

public class LighterBroker extends AbstractBasicBroker {

    protected static final Logger logger = LoggerFactory.getLogger(LighterBroker.class);

    protected final ILighterRestApi restApi;
    protected final ILighterWebSocketApi websocketApi;
    protected final ILighterTranslator translator;
    protected final long accountIndex;
    protected final int apiKeyIndex;

    protected final Set<Integer> knownMarketIndexes = ConcurrentHashMap.newKeySet();
    protected final Set<Integer> subscribedMarketIndexes = ConcurrentHashMap.newKeySet();
    protected final FillDeduper fillDeduper = new FillDeduper();
    protected final AtomicLong nextClientOrderId = new AtomicLong(System.currentTimeMillis());

    protected volatile String authToken;
    protected volatile boolean connected;

    public LighterBroker() {
        this(ExchangeRestApiFactory.getPrivateApi(Exchange.LIGHTER, ILighterRestApi.class),
                ExchangeWebSocketApiFactory.getApi(Exchange.LIGHTER, ILighterWebSocketApi.class),
                LighterTranslator.getInstance(), LighterConfiguration.getInstance().getAccountIndex(),
                LighterConfiguration.getInstance().getApiKeyIndex());

        LighterConfiguration configuration = LighterConfiguration.getInstance();
        logger.info("LighterBroker initialized with configuration: environment={}, restUrl={}, webSocketUrl={}, accountIndex={}",
                configuration.getEnvironment(), configuration.getRestUrl(), configuration.getWebSocketUrl(),
                accountIndex);
    }

    public LighterBroker(ILighterRestApi restApi, ILighterWebSocketApi websocketApi) {
        this(restApi, websocketApi, LighterTranslator.getInstance(), LighterConfiguration.getInstance().getAccountIndex(),
                LighterConfiguration.getInstance().getApiKeyIndex());
    }

    public LighterBroker(ILighterRestApi restApi, ILighterWebSocketApi websocketApi, ILighterTranslator translator,
            long accountIndex, int apiKeyIndex) {
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        if (websocketApi == null) {
            throw new IllegalArgumentException("websocketApi is required");
        }
        if (translator == null) {
            throw new IllegalArgumentException("translator is required");
        }

        this.restApi = restApi;
        this.websocketApi = websocketApi;
        this.translator = translator;
        this.accountIndex = accountIndex;
        this.apiKeyIndex = apiKeyIndex;
    }

    @Override
    public String getBrokerName() {
        return "Lighter";
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        checkConnected();
        if (id == null || id.isBlank()) {
            return new BrokerRequestResult(false, true, "order id is required", BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        OrderTicket openOrder = orderRegistry.getOrderById(id);
        if (openOrder != null) {
            return cancelOrder(openOrder);
        }

        Long orderIndex = parsePositiveLongOrNull(id);
        if (orderIndex == null) {
            return new BrokerRequestResult(false, true, "Order not found for id " + id,
                    BrokerRequestResult.FailureType.ORDER_NOT_FOUND);
        }

        return cancelOrderAcrossMarkets(orderIndex.longValue());
    }

    @Override
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        checkConnected();
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return new BrokerRequestResult(false, true, "clientOrderId is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        OrderTicket order = orderRegistry.getOrderByClientId(clientOrderId);
        if (order == null) {
            order = requestOrderStatusByClientOrderId(clientOrderId);
        }

        if (order == null) {
            return new BrokerRequestResult(false, true, "Order not found for clientOrderId " + clientOrderId,
                    BrokerRequestResult.FailureType.ORDER_NOT_FOUND);
        }

        return cancelOrder(order);
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        checkConnected();
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required", BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        OrderTicket resolvedOrder = resolveOrderForCancel(order);
        if (resolvedOrder == null) {
            return new BrokerRequestResult(false, true, "Unable to resolve order for cancel",
                    BrokerRequestResult.FailureType.ORDER_NOT_FOUND);
        }

        try {
            long nonce = restApi.getNextNonce(accountIndex, apiKeyIndex);
            LighterCancelOrderRequest cancelRequest = translator.translateCancelOrder(resolvedOrder, accountIndex,
                    apiKeyIndex, nonce);
            LighterSendTxResponse response = websocketApi.cancelOrder(cancelRequest);
            if (response == null || !response.isSuccess()) {
                return buildFailedTxResult("Failed to cancel order", response);
            }

            resolvedOrder.setCurrentStatus(OrderStatus.Status.PENDING_CANCEL);
            OrderStatus pendingCancel = new OrderStatus(OrderStatus.Status.PENDING_CANCEL, resolvedOrder.getOrderId(),
                    resolvedOrder.getFilledSize(), resolvedOrder.getRemainingSize(), resolvedOrder.getFilledPrice(),
                    resolvedOrder.getTicker(), getCurrentTime());
            pendingCancel.setClientOrderId(resolvedOrder.getClientOrderId());
            super.fireOrderEvent(new OrderEvent(resolvedOrder, pendingCancel));
            return new BrokerRequestResult();
        } catch (Exception ex) {
            logger.error("Error canceling order {}", resolvedOrder.getOrderId(), ex);
            return new BrokerRequestResult(false, true, ex.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        checkConnected();
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required", BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        try {
            ensureClientOrderId(order);
            order.setOrderEntryTime(getCurrentTime());

            long nonce = restApi.getNextNonce(accountIndex, apiKeyIndex);
            LighterCreateOrderRequest createOrderRequest = translator.translateCreateOrder(order, accountIndex,
                    apiKeyIndex, nonce);

            ensureAccountOrdersSubscription(createOrderRequest.getMarketIndex());

            if (order.getOrderId() == null || order.getOrderId().isBlank()) {
                order.setOrderId(order.getClientOrderId());
            }
            orderRegistry.addOpenOrder(order);

            LighterSendTxResponse response = websocketApi.submitOrder(createOrderRequest);
            if (response == null || !response.isSuccess()) {
                order.setCurrentStatus(OrderStatus.Status.REJECTED);
                orderRegistry.addCompletedOrder(order);
                return buildFailedTxResult("Failed to place order", response);
            }

            return new BrokerRequestResult();
        } catch (Exception ex) {
            logger.error("Error placing Lighter order", ex);
            return new BrokerRequestResult(false, true, ex.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
        }
    }

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        checkConnected();
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required", BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        try {
            order.setOrderEntryTime(getCurrentTime());
            long nonce = restApi.getNextNonce(accountIndex, apiKeyIndex);
            LighterModifyOrderRequest modifyOrderRequest = translator.translateModifyOrder(order, accountIndex,
                    apiKeyIndex, nonce);

            ensureAccountOrdersSubscription(modifyOrderRequest.getMarketIndex());

            LighterSendTxResponse response = websocketApi.modifyOrder(modifyOrderRequest);
            if (response == null || !response.isSuccess()) {
                return buildFailedTxResult("Failed to modify order", response);
            }

            return new BrokerRequestResult();
        } catch (Exception ex) {
            logger.error("Error modifying Lighter order {}", order.getOrderId(), ex);
            return new BrokerRequestResult(false, true, ex.getMessage(), BrokerRequestResult.FailureType.UNKNOWN);
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

        try {
            authToken = refreshAuthToken();

            websocketApi.subscribeAccountStats(accountIndex, this::onLighterAccountStatsEvent);
            websocketApi.subscribeAccountAllTrades(accountIndex, authToken, this::onLighterAccountAllTradesEvent);
            subscribeAccountOrdersForKnownMarkets(authToken);

            connected = true;
            logger.info("Connected Lighter broker websocket streams for accountIndex={}", accountIndex);
        } catch (Exception ex) {
            connected = false;
            logger.error("Failed to connect Lighter broker", ex);
            throw ex;
        }
    }

    @Override
    protected void onDisconnect() {
        connected = false;
        subscribedMarketIndexes.clear();
        try {
            websocketApi.disconnectAll();
        } catch (Exception ex) {
            logger.warn("Error while disconnecting Lighter websocket clients", ex);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        checkConnected();
        if (orderId == null || orderId.isBlank()) {
            return null;
        }

        OrderTicket existing = orderRegistry.getOrderById(orderId);
        if (existing != null) {
            return existing;
        }

        List<OrderTicket> openOrders = getOpenOrders();
        for (OrderTicket openOrder : openOrders) {
            if (openOrder != null && orderId.equals(openOrder.getOrderId())) {
                return openOrder;
            }
        }

        return orderRegistry.getCompletedOrderById(orderId);
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        checkConnected();
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return null;
        }

        OrderTicket existing = orderRegistry.getOrderByClientId(clientOrderId);
        if (existing != null) {
            return existing;
        }

        List<OrderTicket> openOrders = getOpenOrders();
        for (OrderTicket openOrder : openOrders) {
            if (openOrder != null && clientOrderId.equals(openOrder.getClientOrderId())) {
                return openOrder;
            }
        }

        return orderRegistry.getCompletedOrderByClientId(clientOrderId);
    }

    @Override
    public BrokerStatus getBrokerStatus() {
        return connected ? BrokerStatus.OK : BrokerStatus.UNKNOWN;
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        checkConnected();
        String token = ensureAuthToken();
        List<OrderTicket> allOpenOrders = new ArrayList<>();

        Set<Integer> marketIndexes = getKnownMarketIndexes();
        for (Integer marketIndex : marketIndexes) {
            if (marketIndex == null) {
                continue;
            }

            try {
                List<LighterOrder> lighterOrders = restApi.getAccountActiveOrders(token, accountIndex,
                        marketIndex.intValue());
                allOpenOrders.addAll(translator.translateOrders(lighterOrders));
            } catch (Exception ex) {
                logger.warn("Unable to retrieve active orders for market {}", marketIndex, ex);
            }
        }

        if (!allOpenOrders.isEmpty()) {
            orderRegistry.replaceOpenOrders(allOpenOrders);
        }

        return allOpenOrders;
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        throw new UnsupportedOperationException("Cancel and replace order not implemented yet");
    }

    @Override
    public List<Position> getAllPositions() {
        checkConnected();
        return translator.translatePositions(restApi.getPositions(accountIndex));
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        throw new UnsupportedOperationException("Cancel all orders by ticker not implemented yet");
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        throw new UnsupportedOperationException("Cancel all orders not implemented yet");
    }

    protected void checkConnected() {
        if (!connected) {
            logger.warn("Lighter broker API method called while broker is not connected");
        }
    }

    protected void onLighterAccountStatsEvent(LighterAccountStatsUpdate update) {
        if (update == null || update.getStats() == null) {
            return;
        }

        LighterAccountStats stats = update.getStats();
        if (stats.getPortfolioValue() != null) {
            fireAccountEquityUpdated(stats.getPortfolioValue().doubleValue());
        }
        if (stats.getAvailableBalance() != null) {
            fireAvailableFundsUpdated(stats.getAvailableBalance().doubleValue());
        }
    }

    protected void onLighterAccountOrdersEvent(LighterOrdersUpdate update) {
        if (update == null || update.getOrders() == null || update.getOrders().isEmpty()) {
            return;
        }

        for (LighterOrder lighterOrder : update.getOrders()) {
            if (lighterOrder == null) {
                continue;
            }

            OrderTicket translatedOrder = translator.translateOrder(lighterOrder);
            if (translatedOrder == null) {
                continue;
            }

            OrderStatus orderStatus = translator.translateOrderStatus(lighterOrder);
            OrderTicket trackedOrder = resolveTrackedOrder(translatedOrder);
            mergeOrder(trackedOrder, translatedOrder);

            trackedOrder.setCurrentStatus(orderStatus.getStatus());
            trackedOrder.setFilledSize(orderStatus.getFilled());
            trackedOrder.setFilledPrice(orderStatus.getFillPrice());

            if (orderStatus.getStatus() == OrderStatus.Status.FILLED) {
                trackedOrder.setOrderFilledTime(orderStatus.getTimestamp());
            }

            if (orderStatus.getStatus() == OrderStatus.Status.FILLED || orderStatus.getStatus() == OrderStatus.Status.CANCELED
                    || orderStatus.getStatus() == OrderStatus.Status.REJECTED) {
                if (!isKnownOpenOrder(trackedOrder)) {
                    // BrokerOrderRegistry expects the ticker open-order map to exist before a
                    // completed transition.
                    orderRegistry.addOpenOrder(trackedOrder);
                }
                orderRegistry.addCompletedOrder(trackedOrder);
            } else {
                orderRegistry.addOpenOrder(trackedOrder);
            }

            super.fireOrderEvent(new OrderEvent(trackedOrder, orderStatus));
        }
    }

    protected void onLighterAccountAllTradesEvent(LighterTradesUpdate update) {
        if (update == null || update.getTrades() == null || update.getTrades().isEmpty()) {
            return;
        }

        for (LighterTrade trade : update.getTrades()) {
            Fill fill = translator.translateFill(trade, accountIndex);
            if (fill == null) {
                continue;
            }

            String fillId = fill.getFillId();
            if (fillId != null && !fillDeduper.firstTime(fillId)) {
                logger.debug("Duplicate fill received for fillId={}, ignoring", fillId);
                continue;
            }

            OrderTicket trackedOrder = fill.getOrderId() == null ? null : orderRegistry.getOrderById(fill.getOrderId());
            if (trackedOrder != null) {
                if (fill.getClientOrderId() == null || fill.getClientOrderId().isBlank()) {
                    fill.setClientOrderId(trackedOrder.getClientOrderId());
                }

                trackedOrder.setFilledSize(trackedOrder.getFilledSize().add(fill.getSize()));
                BigDecimal commission = fill.getCommission() == null ? BigDecimal.ZERO : fill.getCommission();
                trackedOrder.setCommission(trackedOrder.getCommission().add(commission));
                trackedOrder.addFill(fill);

                if (trackedOrder.getSize() != null && trackedOrder.getFilledSize().compareTo(trackedOrder.getSize()) >= 0) {
                    trackedOrder.setCurrentStatus(OrderStatus.Status.FILLED);
                } else {
                    trackedOrder.setCurrentStatus(OrderStatus.Status.PARTIAL_FILL);
                }
            }

            super.fireFillEvent(fill);
        }
    }

    protected OrderTicket resolveTrackedOrder(OrderTicket translatedOrder) {
        if (translatedOrder == null) {
            return null;
        }

        if (translatedOrder.getOrderId() != null && !translatedOrder.getOrderId().isBlank()) {
            OrderTicket byOrderId = orderRegistry.getOrderById(translatedOrder.getOrderId());
            if (byOrderId != null) {
                return byOrderId;
            }
        }

        if (translatedOrder.getClientOrderId() != null && !translatedOrder.getClientOrderId().isBlank()) {
            OrderTicket byClientOrderId = orderRegistry.getOrderByClientId(translatedOrder.getClientOrderId());
            if (byClientOrderId != null) {
                return byClientOrderId;
            }
        }

        return translatedOrder;
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
        if (source.getOrderEntryTime() != null) {
            target.setOrderEntryTime(source.getOrderEntryTime());
        }
        for (OrderTicket.Modifier modifier : source.getModifiers()) {
            target.addModifier(modifier);
        }
    }

    protected boolean isKnownOpenOrder(OrderTicket order) {
        if (order == null) {
            return false;
        }

        if (order.getOrderId() != null && !order.getOrderId().isBlank()
                && orderRegistry.getOpenOrderById(order.getOrderId()) != null) {
            return true;
        }

        if (order.getClientOrderId() != null && !order.getClientOrderId().isBlank()
                && orderRegistry.getOpenOrderByClientId(order.getClientOrderId()) != null) {
            return true;
        }

        return false;
    }

    protected String ensureAuthToken() {
        if (authToken == null || authToken.isBlank()) {
            return refreshAuthToken();
        }
        return authToken;
    }

    protected synchronized String refreshAuthToken() {
        authToken = restApi.getApiToken();
        return authToken;
    }

    protected void ensureClientOrderId(OrderTicket order) {
        String clientOrderId = order.getClientOrderId();
        if (clientOrderId == null || clientOrderId.isBlank()) {
            order.setClientOrderId(getNextOrderId());
        }
    }

    protected OrderTicket resolveOrderForCancel(OrderTicket order) {
        if (order.getOrderId() != null && !order.getOrderId().isBlank()) {
            return order;
        }

        String clientOrderId = order.getClientOrderId();
        if (clientOrderId != null && !clientOrderId.isBlank()) {
            OrderTicket fromRegistry = orderRegistry.getOrderByClientId(clientOrderId);
            if (fromRegistry != null) {
                return fromRegistry;
            }
        }

        return order;
    }

    protected BrokerRequestResult cancelOrderAcrossMarkets(long orderIndex) {
        String lastError = "Unable to cancel order " + orderIndex;
        Set<Integer> marketIndexes = getKnownMarketIndexes();

        if (marketIndexes.isEmpty()) {
            return new BrokerRequestResult(false, true, lastError, BrokerRequestResult.FailureType.ORDER_NOT_FOUND);
        }

        for (Integer marketIndex : marketIndexes) {
            if (marketIndex == null) {
                continue;
            }

            try {
                LighterCancelOrderRequest cancelRequest = new LighterCancelOrderRequest();
                cancelRequest.setMarketIndex(marketIndex.intValue());
                cancelRequest.setOrderIndex(orderIndex);
                cancelRequest.setNonce(restApi.getNextNonce(accountIndex, apiKeyIndex));
                cancelRequest.setApiKeyIndex(apiKeyIndex);
                cancelRequest.setAccountIndex(accountIndex);

                LighterSendTxResponse response = websocketApi.cancelOrder(cancelRequest);
                if (response != null && response.isSuccess()) {
                    return new BrokerRequestResult();
                }

                if (response != null && response.getMessage() != null && !response.getMessage().isBlank()) {
                    lastError = response.getMessage();
                }
            } catch (Exception ex) {
                logger.debug("Cancel by orderIndex={} failed for market={}", orderIndex, marketIndex, ex);
                if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    lastError = ex.getMessage();
                }
            }
        }

        return new BrokerRequestResult(false, true, lastError, BrokerRequestResult.FailureType.ORDER_NOT_FOUND);
    }

    protected BrokerRequestResult buildFailedTxResult(String prefix, LighterSendTxResponse response) {
        String message = prefix;
        if (response != null && response.getMessage() != null && !response.getMessage().isBlank()) {
            message = prefix + ": " + response.getMessage();
        }
        return new BrokerRequestResult(false, true, message, BrokerRequestResult.FailureType.UNKNOWN);
    }

    protected void subscribeAccountOrdersForKnownMarkets(String token) {
        for (Integer marketIndex : getKnownMarketIndexes()) {
            if (marketIndex == null) {
                continue;
            }
            websocketApi.subscribeAccountOrders(marketIndex.intValue(), accountIndex, token,
                    this::onLighterAccountOrdersEvent);
            subscribedMarketIndexes.add(marketIndex);
        }
    }

    protected void ensureAccountOrdersSubscription(int marketIndex) {
        if (!connected || marketIndex < 0) {
            return;
        }

        if (subscribedMarketIndexes.add(Integer.valueOf(marketIndex))) {
            websocketApi.subscribeAccountOrders(marketIndex, accountIndex, ensureAuthToken(),
                    this::onLighterAccountOrdersEvent);
        }
    }

    protected Set<Integer> getKnownMarketIndexes() {
        if (knownMarketIndexes.isEmpty()) {
            loadKnownMarketIndexes();
        }
        return new LinkedHashSet<>(knownMarketIndexes);
    }

    protected synchronized void loadKnownMarketIndexes() {
        if (!knownMarketIndexes.isEmpty()) {
            return;
        }

        addMarketIndexes(restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES));
        addMarketIndexes(restApi.getAllInstrumentsForType(InstrumentType.CRYPTO_SPOT));
    }

    protected void addMarketIndexes(InstrumentDescriptor[] descriptors) {
        if (descriptors == null || descriptors.length == 0) {
            return;
        }

        for (InstrumentDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                continue;
            }

            Integer marketIndex = parseMarketIndex(descriptor.getInstrumentId());
            if (marketIndex == null) {
                marketIndex = parseMarketIndex(descriptor.getExchangeSymbol());
            }
            if (marketIndex != null) {
                knownMarketIndexes.add(marketIndex);
            }
        }
    }

    protected Integer parseMarketIndex(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected Long parsePositiveLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? Long.valueOf(parsed) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
