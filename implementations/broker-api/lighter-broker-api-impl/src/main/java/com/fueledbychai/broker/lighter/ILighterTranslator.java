package com.fueledbychai.broker.lighter;

import java.util.List;

import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.lighter.common.api.account.LighterPosition;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;
import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;

public interface ILighterTranslator {

    LighterCreateOrderRequest translateCreateOrder(OrderTicket order, long accountIndex, int apiKeyIndex, long nonce);

    LighterModifyOrderRequest translateModifyOrder(OrderTicket order, long accountIndex, int apiKeyIndex, long nonce);

    LighterCancelOrderRequest translateCancelOrder(OrderTicket order, long accountIndex, int apiKeyIndex, long nonce);

    OrderTicket translateOrder(LighterOrder order);

    List<OrderTicket> translateOrders(List<LighterOrder> orders);

    OrderStatus translateOrderStatus(LighterOrder order);

    Fill translateFill(LighterTrade trade, long accountIndex);

    List<Position> translatePositions(List<LighterPosition> positions);
}
