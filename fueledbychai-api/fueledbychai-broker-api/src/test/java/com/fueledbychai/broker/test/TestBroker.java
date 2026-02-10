package com.fueledbychai.broker.test;

import java.util.Collections;
import java.util.List;

import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.Ticker;

public class TestBroker extends AbstractBasicBroker {

    @Override
    protected void onDisconnect() {
        // no-op
    }

    @Override
    public String getBrokerName() {
        return "TestBroker";
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        return new BrokerRequestResult();
    }

    @Override
    public String getNextOrderId() {
        return "1";
    }

    @Override
    public void connect() {
        // no-op
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public BrokerStatus getBrokerStatus() {
        return BrokerStatus.UNKNOWN;
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        return null;
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        return Collections.emptyList();
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        // no-op
    }

    @Override
    public List<Position> getAllPositions() {
        return Collections.emptyList();
    }
}
