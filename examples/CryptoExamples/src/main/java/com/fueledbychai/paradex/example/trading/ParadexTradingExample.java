package com.fueledbychai.paradex.example.trading;

import java.math.BigDecimal;

import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Modifier;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.broker.paradex.ParadexBroker;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.paradex.ParadexQuoteEngine;
import com.fueledbychai.paradex.common.ParadexTickerRegistry;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.paradex.common.api.ParadexApiFactory;

public class ParadexTradingExample {
    protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ParadexTradingExample.class);

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
        ParadexTradingExample example = new ParadexTradingExample();
        example.executeTrade();
        // example.onBoard();
    }
}
