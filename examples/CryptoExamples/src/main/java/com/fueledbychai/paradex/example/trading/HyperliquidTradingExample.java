package com.fueledbychai.paradex.example.trading;

import java.math.BigDecimal;

import com.fueledbychai.broker.BrokerAccountInfoListener;
import com.fueledbychai.broker.hyperliquid.HyperliquidBroker;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.hyperliquid.ws.HyperliquidTickerRegistry;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.hyperliquid.HyperliquidQuoteEngine;

public class HyperliquidTradingExample {
    protected static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HyperliquidTradingExample.class);

    public void executeTrade() throws Exception {
        String tickerString = "BTC";
        String size = "0.01";
        String price = "120000";
        Ticker ticker = HyperliquidTickerRegistry.getInstance()
                .lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, tickerString);

        QuoteEngine engine = QuoteEngine.getInstance(HyperliquidQuoteEngine.class);
        engine.startEngine();

        engine.subscribeLevel1(ticker, (level1Quote) -> {
            // System.out.println("Level 1 Quote Updated : " + level1Quote);
        });

        HyperliquidBroker broker = new HyperliquidBroker();
        broker.connect();

        broker.addOrderEventListener((event) -> {
            logger.info("Order Event Received : {}", event);
        });

        broker.addFillEventListener(fill -> {
            if (fill.isSnapshot()) {
                logger.info("SNAPSHOT Fill Received : {}", fill);
            } else {
                logger.info("Fill Received : {}", fill);
            }
        });

        broker.addBrokerAccountInfoListener(new BrokerAccountInfoListener() {

            @Override
            public void accountEquityUpdated(double equity) {
                logger.info("Account equity updated: {}", equity);
            }

            @Override
            public void availableFundsUpdated(double availableFunds) {
                logger.info("Available funds updated: {}", availableFunds);
            }

        });

        Thread.sleep(5000);

        OrderTicket order = new OrderTicket();

        // double size = 0.001;
        // double price = 110000;
        TradeDirection direction = TradeDirection.BUY;
        order.setTicker(ticker).setSize(new BigDecimal(size)).setDirection(direction).setType(Type.LIMIT)
                .setLimitPrice(new BigDecimal(price)).addModifier(OrderTicket.Modifier.POST_ONLY);
        // .setTicker(ticker).setSize(new
        // BigDecimal(size)).setDirection(TradeDirection.BUY).setType(Type.MARKET);

        BigDecimal priceToUse = new BigDecimal(price);
        int orders = 1;
        for (int i = 0; i < orders; i++) {
            logger.info("placing order");
            try {
                broker.placeOrder(getMarketOrder(ticker, priceToUse));
                priceToUse = priceToUse.subtract(new BigDecimal("1000"));
            } catch (Exception e) {
                logger.error("Error placing order", e);
            }

            Thread.sleep(2000);
        }

    }

    protected OrderTicket getPostOrder(Ticker ticker, BigDecimal price) {
        OrderTicket order = new OrderTicket();
        order.setTicker(ticker).setSize(new BigDecimal("0.001")).setDirection(TradeDirection.BUY).setType(Type.LIMIT)
                .setLimitPrice(price).addModifier(OrderTicket.Modifier.POST_ONLY);
        return order;
    }

    protected OrderTicket getMarketOrder(Ticker ticker, BigDecimal price) {
        OrderTicket order = new OrderTicket();
        order.setTicker(ticker).setSize(new BigDecimal("0.001")).setDirection(TradeDirection.BUY).setType(Type.MARKET);
        return order;
    }

    public static void main(String[] args) throws Exception {
        // config file
//        System.setProperty("hyperliquid.config.file",
//                "/path/to/your/hyperliquid-trading-example.properties");
        HyperliquidTradingExample example = new HyperliquidTradingExample();
        example.executeTrade();
    }
}
