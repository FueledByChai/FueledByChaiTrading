package com.fueledbychai.broker.okx;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderStatus.CancelReason;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.data.Ticker;

/**
 * Baseline OKX broker implementation.
 *
 * This implementation provides local order lifecycle tracking (new/replace/
 * cancel) and event dispatch. Exchange-side private order routing can be added
 * incrementally behind the same interface.
 */
public class OkxBroker extends AbstractBasicBroker {

    protected static final Logger logger = LoggerFactory.getLogger(OkxBroker.class);

    protected final AtomicLong nextOrderId = new AtomicLong(System.currentTimeMillis());
    protected volatile boolean connected;

    @Override
    protected void onDisconnect() {
        connected = false;
    }

    @Override
    public String getBrokerName() {
        return "Okx";
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
        return cancelOpenOrder(openOrder, CancelReason.USER_CANCELED);
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
        return cancelOpenOrder(openOrder, CancelReason.USER_CANCELED);
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

        OrderTicket openOrder = resolveOpenOrder(order);
        if (openOrder == null) {
            return new BrokerRequestResult(false, true, "Open order not found",
                    BrokerRequestResult.FailureType.ORDER_NOT_OPEN);
        }
        return cancelOpenOrder(openOrder, CancelReason.USER_CANCELED);
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        if (!connected) {
            return notConnectedResult();
        }
        if (ticker == null) {
            return new BrokerRequestResult(false, true, "ticker is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        List<OrderTicket> openOrders = new ArrayList<>(orderRegistry.getOpenOrdersByTicker(ticker));
        for (OrderTicket order : openOrders) {
            cancelOpenOrder(order, CancelReason.USER_CANCELED);
        }
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        if (!connected) {
            return notConnectedResult();
        }

        List<OrderTicket> openOrders = new ArrayList<>(orderRegistry.getOpenOrders());
        for (OrderTicket order : openOrders) {
            cancelOpenOrder(order, CancelReason.USER_CANCELED);
        }
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelOrders(List<OrderTicket> orders) {
        throw new UnsupportedOperationException("Batch cancel not supported by OkxBroker");
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        if (!connected) {
            return notConnectedResult();
        }
        BrokerRequestResult validation = validateOrder(order);
        if (!validation.isSuccess()) {
            return validation;
        }

        ensureOrderIds(order);
        order.setOrderEntryTime(getCurrentTime());
        if (order.getCurrentStatus() != OrderStatus.Status.NEW) {
            order.setCurrentStatus(OrderStatus.Status.NEW);
        }

        if (order.getFilledSize() == null) {
            order.setFilledSize(BigDecimal.ZERO);
        }
        if (order.getFilledPrice() == null) {
            order.setFilledPrice(BigDecimal.ZERO);
        }

        orderRegistry.addOpenOrder(order);

        OrderStatus status = new OrderStatus(OrderStatus.Status.NEW, order.getOrderId(), order.getFilledSize(),
                order.getRemainingSize(), order.getFilledPrice(), order.getTicker(), getCurrentTime());
        status.setClientOrderId(order.getClientOrderId());
        fireOrderEvent(new OrderEvent(order, status));

        return new BrokerRequestResult();
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

        boolean changed = applyOrderUpdates(existing, order);
        if (!changed) {
            return new BrokerRequestResult(false, false, "No order parameters changed",
                    BrokerRequestResult.FailureType.NO_ORDER_PARAMS_CHANGED);
        }

        existing.setCurrentStatus(OrderStatus.Status.REPLACED);
        orderRegistry.addOpenOrder(existing);

        OrderStatus status = new OrderStatus(OrderStatus.Status.REPLACED, existing.getOrderId(), existing.getOrderId(),
                existing.getFilledSize(), existing.getRemainingSize(), existing.getFilledPrice(), existing.getTicker(),
                getCurrentTime());
        status.setClientOrderId(existing.getClientOrderId());
        fireOrderEvent(new OrderEvent(existing, status));

        return new BrokerRequestResult();
    }

    @Override
    public String getNextOrderId() {
        return String.valueOf(nextOrderId.incrementAndGet());
    }

    @Override
    public void connect() {
        connected = true;
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
        return orderRegistry.getOrderById(orderId);
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return null;
        }
        return orderRegistry.getOrderByClientId(clientOrderId);
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        return new ArrayList<>(orderRegistry.getOpenOrders());
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
        return Collections.emptyList();
    }

    protected BrokerRequestResult cancelOpenOrder(OrderTicket order, CancelReason cancelReason) {
        if (order == null) {
            return new BrokerRequestResult(false, true, "order is required",
                    BrokerRequestResult.FailureType.VALIDATION_FAILED);
        }

        order.setCurrentStatus(OrderStatus.Status.CANCELED);
        orderRegistry.addCompletedOrder(order);

        OrderStatus status = new OrderStatus(OrderStatus.Status.CANCELED, order.getOrderId(), order.getFilledSize(),
                order.getRemainingSize(), order.getFilledPrice(), order.getTicker(), getCurrentTime());
        status.setClientOrderId(order.getClientOrderId());
        status.setCancelReason(cancelReason == null ? CancelReason.NONE : cancelReason);
        fireOrderEvent(new OrderEvent(order, status));

        return new BrokerRequestResult();
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

        return new BrokerRequestResult();
    }

    protected void ensureOrderIds(OrderTicket order) {
        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            order.setOrderId(getNextOrderId());
        }
        if (order.getClientOrderId() == null || order.getClientOrderId().isBlank()) {
            order.setClientOrderId(order.getOrderId());
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

    protected boolean applyOrderUpdates(OrderTicket existing, OrderTicket incoming) {
        boolean changed = false;

        if (incoming.getTicker() != null && !incoming.getTicker().equals(existing.getTicker())) {
            existing.setTicker(incoming.getTicker());
            changed = true;
        }
        if (incoming.getType() != null && incoming.getType() != existing.getType()) {
            existing.setType(incoming.getType());
            changed = true;
        }
        if (incoming.getTradeDirection() != null && incoming.getTradeDirection() != existing.getTradeDirection()) {
            existing.setTradeDirection(incoming.getTradeDirection());
            changed = true;
        }
        if (incoming.getSize() != null && incoming.getSize().compareTo(existing.getSize()) != 0) {
            existing.setSize(incoming.getSize());
            changed = true;
        }
        if (incoming.getLimitPrice() != null && !incoming.getLimitPrice().equals(existing.getLimitPrice())) {
            existing.setLimitPrice(incoming.getLimitPrice());
            changed = true;
        }
        if (incoming.getStopPrice() != null && !incoming.getStopPrice().equals(existing.getStopPrice())) {
            existing.setStopPrice(incoming.getStopPrice());
            changed = true;
        }
        if (incoming.getDuration() != null && incoming.getDuration() != existing.getDuration()) {
            existing.setDuration(incoming.getDuration());
            changed = true;
        }
        if (incoming.getGoodAfterTime() != null && !incoming.getGoodAfterTime().equals(existing.getGoodAfterTime())) {
            existing.setGoodAfterTime(incoming.getGoodAfterTime());
            changed = true;
        }
        if (incoming.getGoodUntilTime() != null && !incoming.getGoodUntilTime().equals(existing.getGoodUntilTime())) {
            existing.setGoodUntilTime(incoming.getGoodUntilTime());
            changed = true;
        }

        if (incoming.getModifiers() != null && !incoming.getModifiers().isEmpty()
                && !incoming.getModifiers().equals(existing.getModifiers())) {
            existing.setModifiers(new ArrayList<>(incoming.getModifiers()));
            changed = true;
        }

        return changed;
    }

    protected BrokerRequestResult notConnectedResult() {
        return new BrokerRequestResult(false, false, "Broker is not connected",
                BrokerRequestResult.FailureType.UNKNOWN);
    }
}
