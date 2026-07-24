package com.fueledbychai.broker.extended;

import java.util.List;

import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.extended.common.api.order.ExtendedOrder;
import com.fueledbychai.extended.common.api.order.ExtendedOrderStatus;

public interface IExtendedTranslator {

    ExtendedOrder translateOrder(OrderTicket order);

    OrderTicket translateOrder(ExtendedOrder order);

    List<OrderTicket> translateOrders(List<ExtendedOrder> orders);

    OrderStatus.Status translateStatusCode(ExtendedOrderStatus status);
}
