package com.fueledbychai.paradex.example.trading;

import java.math.BigDecimal;
import java.util.List;

import com.fueledbychai.broker.IBroker;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Modifier;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.broker.paradex.ParadexBroker;
import com.fueledbychai.broker.paradex.ResilientParadexBroker;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.paradex.ParadexQuoteEngine;
import com.fueledbychai.paradex.common.ParadexTickerRegistry;
import com.fueledbychai.time.Span;

public class ParadexLatencyTest {
    protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ParadexLatencyTest.class);

    public void cancelMultipleTimes() throws Exception {
        Ticker btcTicker = ParadexTickerRegistry.getInstance().lookupByBrokerSymbol("BTC-USD-PERP");

        QuoteEngine engine = QuoteEngine.getInstance(ParadexQuoteEngine.class);
        engine.startEngine();

        engine.subscribeLevel1(btcTicker, (level1Quote) -> {
            // System.out.println("Level 1 Quote Updated : " + level1Quote);
        });

        // ParadexBroker broker = new ParadexBroker();
        IBroker broker = new ResilientParadexBroker();
        broker.connect();

        broker.addOrderEventListener((event) -> {
            log.info("Order Event Received : {}", event);
        });

        broker.addFillEventListener(fill -> {
            log.info("Order Fill Received : {}", fill);
        });

        Thread.sleep(5000);

        List<Position> allPositions = broker.getAllPositions();
        Position activePosition = null;
        for (Position position : allPositions) {
            if (position.getTicker().equals(btcTicker) && position.getStatus() == Position.Status.OPEN) {
                log.info("Current BTC Position: {}", position);
                activePosition = position;
            }
        }

        if (activePosition != null) {
            log.info("Closing existing position of size: {}", activePosition.getSize());
            OrderTicket closeOrder = new OrderTicket();
            TradeDirection direction = TradeDirection.SELL;
            if (activePosition.getSide() == com.fueledbychai.data.Side.SHORT) {
                direction = TradeDirection.BUY;
            }
            closeOrder.setTicker(btcTicker).setSize(activePosition.getSize().abs()).setDirection(direction)
                    .setType(Type.MARKET).setClientOrderId(broker.getNextOrderId());
            broker.placeOrder(closeOrder);
            Thread.sleep(5000);
        }

        while (true) {
            OrderTicket order = new OrderTicket();

            String orderId = broker.getNextOrderId();

            order.setTicker(btcTicker).setSize(BigDecimal.valueOf(0.01)).setDirection(TradeDirection.BUY)
                    .setType(Type.LIMIT).setLimitPrice(BigDecimal.valueOf(106000)).setClientOrderId(orderId);

            try (var s = Span.start("PD_CREATING_ORDER", order.getClientOrderId())) {
                broker.placeOrder(order);
            }
            Thread.sleep(3000);

            try (var s = Span.start("PD_CANCELING_ORDER", order.getClientOrderId())) {
                broker.cancelOrderByClientOrderId(orderId);
            }

            Thread.sleep(5000);
        }

    }

    public void executeTrade() throws Exception {
        Ticker btcTicker = ParadexTickerRegistry.getInstance().lookupByBrokerSymbol("BTC-USD-PERP");

        QuoteEngine engine = QuoteEngine.getInstance(ParadexQuoteEngine.class);
        engine.startEngine();

        engine.subscribeLevel1(btcTicker, (level1Quote) -> {
            // System.out.println("Level 1 Quote Updated : " + level1Quote);
        });

        ParadexBroker broker = new ParadexBroker();
        broker.connect();

        broker.addOrderEventListener((event) -> {
            log.info("Order Event Received : {}", event);
        });

        broker.addFillEventListener(fill -> {
            log.info("Order Fill Received : {}", fill);
        });

        Thread.sleep(2000);

        OrderTicket order = new OrderTicket();

        order.setTicker(btcTicker).setSize(BigDecimal.valueOf(0.01)).setDirection(TradeDirection.BUY)
                .setType(Type.LIMIT).setLimitPrice(BigDecimal.valueOf(110000)).addModifier(Modifier.POST_ONLY)
                .setClientOrderId("123");

        broker.placeOrder(order);
        Thread.sleep(1000);

        OrderTicket order2 = new OrderTicket();

        order2.setTicker(btcTicker).setSize(BigDecimal.valueOf(0.01)).setDirection(TradeDirection.BUY)
                .setType(Type.LIMIT).setLimitPrice(BigDecimal.valueOf(110000)).addModifier(Modifier.POST_ONLY)
                .setClientOrderId("123");

        broker.placeOrder(order2);

    }

    public static void main(String[] args) throws Exception {
        // config file
        // System.setProperty("paradex.config.file",
        // "/path/to/your/paradex-trading-example.properties");
        ParadexLatencyTest example = new ParadexLatencyTest();
        // example.executeTrade();
        example.cancelMultipleTimes();
        // example.onBoard();
    }
}
