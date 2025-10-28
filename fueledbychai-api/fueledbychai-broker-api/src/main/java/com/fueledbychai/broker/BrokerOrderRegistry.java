package com.fueledbychai.broker;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Ticker;

public class BrokerOrderRegistry implements IBrokerOrderRegistry {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    protected List<Position> positionsList = new ArrayList<>();
    protected Map<String, OrderTicket> openOrderMapByOrderId = new HashMap<>();
    protected Map<String, OrderTicket> openOrderMapByClientId = new HashMap<>();
    protected Map<String, OrderTicket> completedOrderMapByOrderId = new HashMap<>();
    protected Map<String, OrderTicket> completedOrderMapByClientId = new HashMap<>();

    protected Map<Ticker, Position> positions = new HashMap<>();
    protected Map<Ticker, Map<String, OrderTicket>> openOrdersByTickerByCloid = new HashMap<>();
    protected Map<Ticker, Map<String, OrderTicket>> completedOrdersByTickerByCloid = new HashMap<>();
    protected Map<Ticker, List<Fill>> fillsByTicker = new HashMap<>();
    protected Map<Ticker, Position> positionByTicker = new HashMap<>();

    /*
     * Replaces any open orders
     */
    public void replaceOpenOrders(Ticker ticker, List<OrderTicket> newOpenOrders) {
        lock.writeLock().lock();
        try {
            // Move existing open orders for the ticker to completed
            Map<String, OrderTicket> existingOpenOrders = openOrdersByTickerByCloid.get(ticker);
            if (existingOpenOrders != null) {
                // only move to completed if the order isn't in the OpenOrders list
                // match it buy ClientOrderId, since order properties may have changed
                for (OrderTicket existingOpenOrder : existingOpenOrders.values()) {
                    boolean stillOpen = false;
                    for (OrderTicket newOpenOrder : newOpenOrders) {
                        if (existingOpenOrder.getClientOrderId() != null
                                && existingOpenOrder.getClientOrderId().equals(newOpenOrder.getClientOrderId())) {
                            stillOpen = true;
                            break;
                        }
                    }
                    if (!stillOpen) {
                        addCompletedOrder(existingOpenOrder);
                    }
                }

                // if an order is in the newOpenOrders list, but not in the existingOpenOrders,
                // add it to open orders, search
                // by ClientOrderId since order properties may have changed
                for (OrderTicket newOpenOrder : newOpenOrders) {
                    boolean alreadyOpen = false;
                    for (OrderTicket existingOpenOrder : existingOpenOrders.values()) {
                        if (newOpenOrder.getClientOrderId() != null
                                && newOpenOrder.getClientOrderId().equals(existingOpenOrder.getClientOrderId())) {
                            alreadyOpen = true;
                            break;
                        }
                    }
                    if (!alreadyOpen) {
                        addOpenOrder(newOpenOrder);
                    }
                }
            } else {
                // No existing open orders, just add all new open orders
                for (OrderTicket newOpenOrder : newOpenOrders) {
                    addOpenOrder(newOpenOrder);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replaceOpenOrders(List<OrderTicket> newOpenOrders) {
        // group the orders by ticker and then call the other method
        Map<Ticker, List<OrderTicket>> ordersByTicker = new HashMap<>();
        for (OrderTicket order : newOpenOrders) {
            List<OrderTicket> ordersForTicker = ordersByTicker.get(order.getTicker());
            if (ordersForTicker == null) {
                ordersForTicker = new ArrayList<>();
                ordersByTicker.put(order.getTicker(), ordersForTicker);
            }
            ordersForTicker.add(order);
        }

        for (Map.Entry<Ticker, List<OrderTicket>> entry : ordersByTicker.entrySet()) {
            replaceOpenOrders(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void addOpenOrder(OrderTicket order) {
        lock.writeLock().lock();
        try {
            addOpenOrderToMap(order, order.getOrderId(), openOrderMapByOrderId);
            addOpenOrderToMap(order, order.getClientOrderId(), openOrderMapByClientId);
            Map<String, OrderTicket> openOrderMapbyTicker = openOrdersByTickerByCloid.get(order.getTicker());
            if (openOrderMapbyTicker == null) {
                openOrderMapbyTicker = new HashMap<>();
                openOrdersByTickerByCloid.put(order.getTicker(), openOrderMapbyTicker);
            }
            addOpenOrderToMap(order, order.getClientOrderId(), openOrderMapbyTicker);
        } finally {
            lock.writeLock().unlock();
        }
    }

    protected void addOpenOrderToMap(OrderTicket order, String idToUse, Map<String, OrderTicket> openOrderMap) {
        openOrderMap.put(idToUse, order);
    }

    @Override
    public void addCompletedOrder(OrderTicket order) {
        lock.writeLock().lock();
        try {
            addCompletedOrderToMap(order, order.getOrderId(), openOrderMapByOrderId, completedOrderMapByOrderId);
            addCompletedOrderToMap(order, order.getClientOrderId(), openOrderMapByClientId,
                    completedOrderMapByClientId);
            Map<String, OrderTicket> openOrderMap = openOrdersByTickerByCloid.get(order.getTicker());
            Map<String, OrderTicket> completedOrderMap = completedOrdersByTickerByCloid.get(order.getTicker());
            if (completedOrderMap == null) {
                completedOrderMap = new HashMap<>();
                completedOrdersByTickerByCloid.put(order.getTicker(), completedOrderMap);
            }
            addCompletedOrderToMap(order, order.getClientOrderId(), openOrderMap, completedOrderMap);
        } finally {
            lock.writeLock().unlock();
        }
    }

    protected void addCompletedOrderToMap(OrderTicket order, String idToUse, Map<String, OrderTicket> openOrderMap,
            Map<String, OrderTicket> completedOrderMap) {
        if (idToUse == null || idToUse.isEmpty())
            return;

        openOrderMap.remove(idToUse);
        if (order.getFilledSize() != null && order.getFilledSize().compareTo(BigDecimal.ZERO) > 0) {
            completedOrderMap.put(idToUse, order);
        }
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(openOrderMapByOrderId.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<OrderTicket> getOpenOrdersByTicker(Ticker ticker) {
        lock.readLock().lock();
        try {
            Map<String, OrderTicket> openOrderMap = openOrdersByTickerByCloid.get(ticker);
            if (openOrderMap != null) {
                return new ArrayList<>(openOrderMap.values());
            }
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public OrderTicket getOpenOrderById(String orderId) {
        lock.readLock().lock();
        try {
            return openOrderMapByOrderId.get(orderId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public OrderTicket getOpenOrderByClientId(String clientOrderId) {
        lock.readLock().lock();
        try {
            return openOrderMapByClientId.get(clientOrderId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public OrderTicket getCompletedOrderById(String orderId) {
        lock.readLock().lock();
        try {
            return completedOrderMapByOrderId.get(orderId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public OrderTicket getCompletedOrderByClientId(String clientOrderId) {
        lock.readLock().lock();
        try {
            return completedOrderMapByClientId.get(clientOrderId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public OrderTicket getOrderById(String orderId) {
        lock.readLock().lock();
        try {
            OrderTicket order = openOrderMapByOrderId.get(orderId);
            if (order == null) {
                order = completedOrderMapByOrderId.get(orderId);
            }
            return order;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public OrderTicket getOrderByClientId(String clientOrderId) {
        lock.readLock().lock();
        try {
            OrderTicket order = openOrderMapByClientId.get(clientOrderId);
            if (order == null) {
                order = completedOrderMapByClientId.get(clientOrderId);
            }
            return order;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<OrderTicket> getOpenBuyOrdersByTicker(Ticker ticker) {
        lock.readLock().lock();
        try {
            List<OrderTicket> result = new ArrayList<>();
            // Direct access to avoid nested locking since we already have the read lock
            Map<String, OrderTicket> openOrderMap = openOrdersByTickerByCloid.get(ticker);
            if (openOrderMap != null) {
                openOrderMap.values().forEach(order -> {
                    if (order.getTradeDirection() == TradeDirection.BUY) {
                        result.add(order);
                    }
                });
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<OrderTicket> getOpenSellOrdersByTicker(Ticker ticker) {
        lock.readLock().lock();
        try {
            List<OrderTicket> result = new ArrayList<>();
            // Direct access to avoid nested locking since we already have the read lock
            Map<String, OrderTicket> openOrderMap = openOrdersByTickerByCloid.get(ticker);
            if (openOrderMap != null) {
                openOrderMap.values().forEach(order -> {
                    if (order.getTradeDirection() == TradeDirection.SELL) {
                        result.add(order);
                    }
                });
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
}
