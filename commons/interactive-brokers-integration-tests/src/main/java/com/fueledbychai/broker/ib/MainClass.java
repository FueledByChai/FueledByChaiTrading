/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.broker.ib;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderEventListener;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.ib.IBConnectionUtil;
import com.fueledbychai.ib.IBSocket;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.ib.IBQuoteEngine;

/**
 *
 *  
 */
public class MainClass implements OrderEventListener, Level1QuoteListener {

    @Override
    public void quoteRecieved(ILevel1Quote quote) {
        System.out.println("Quote: " + quote);
    }

    public void start() throws Exception {

        IBConnectionUtil util = new IBConnectionUtil("localhost", 7999, 1);
        IBSocket socket;
        InteractiveBrokersBroker broker;
        List<OrderEvent> eventList = new ArrayList<>();
        Ticker qqqTicker = new Ticker("QQQ").setInstrumentType(InstrumentType.STOCK);
        socket = util.getIBSocket();
        broker = new InteractiveBrokersBroker(socket);
        broker.addOrderEventListener(this);
        broker.connect();
        IBQuoteEngine ibQuoteEngine = new IBQuoteEngine(socket);
        ibQuoteEngine.startEngine();
        ibQuoteEngine.subscribeLevel1(qqqTicker, this);

        String orderId = broker.getNextOrderId();
        System.out.println("Order id: " + orderId);
        OrderTicket order = new OrderTicket(orderId, qqqTicker, BigDecimal.valueOf(100), TradeDirection.BUY);
        broker.placeOrder(order);

        Thread thread = new Thread(() -> {
            sleep(10000);
            String orderId2 = broker.getNextOrderId();
            System.out.println("Order id: " + orderId);
            OrderTicket order2 = new OrderTicket(orderId2, qqqTicker, BigDecimal.valueOf(200), TradeDirection.BUY);
            broker.placeOrder(order2);

            new Thread(() -> {
                sleep(20000);
                System.exit(0);

            }).start();

        });
        thread.start();

    }

    @Override
    public void orderEvent(OrderEvent event) {
        System.out.println("Event: " + event);
    }

    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ex) {
        }
    }

    public static void main(String[] args) throws Exception {
        new MainClass().start();
    }

}
