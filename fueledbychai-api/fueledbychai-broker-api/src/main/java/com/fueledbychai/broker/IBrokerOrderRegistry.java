package com.fueledbychai.broker;

import java.util.List;

import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.Ticker;

public interface IBrokerOrderRegistry {

    /**
     * Makes the specified open orders the current set of open orders for the given
     * ticker, moving any other orders to completed
     * 
     * @param ticker
     * @param openOrders
     */
    void replaceOpenOrders(Ticker ticker, List<OrderTicket> openOrders);

    /**
     * Makes the specified open orders the current set of open orders, moving any
     * other orders to completed
     * 
     * @param openOrders
     */
    void replaceOpenOrders(List<OrderTicket> openOrders);

    void addOpenOrder(OrderTicket order);

    void addCompletedOrder(OrderTicket order);

    List<OrderTicket> getOpenOrders();

    List<OrderTicket> getOpenOrdersByTicker(Ticker ticker);

    OrderTicket getOpenOrderById(String orderId);

    OrderTicket getOpenOrderByClientId(String clientOrderId);

    OrderTicket getCompletedOrderById(String orderId);

    OrderTicket getCompletedOrderByClientId(String clientOrderId);

}