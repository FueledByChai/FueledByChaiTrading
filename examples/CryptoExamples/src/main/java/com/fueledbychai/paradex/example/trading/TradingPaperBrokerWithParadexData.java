package com.fueledbychai.paradex.example.trading;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.broker.paper.PaperBroker;
import com.fueledbychai.broker.paper.PaperBrokerCommission;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.paradex.ParadexQuoteEngine;
import com.fueledbychai.paradex.common.ParadexTickerRegistry;

public class TradingPaperBrokerWithParadexData {

    protected Logger logger = LoggerFactory.getLogger(TradingPaperBrokerWithParadexData.class);

    public void runExample() throws Exception {
        Ticker ticker = ParadexTickerRegistry.getInstance().lookupByBrokerSymbol("BTC-USD-PERP");

        ParadexQuoteEngine quoteEngine = new ParadexQuoteEngine();
        quoteEngine.startEngine();

        PaperBroker broker = new PaperBroker(quoteEngine, ticker, PaperBrokerCommission.PARADEX_COMMISSION, 10000.0);
        quoteEngine.subscribeLevel1(ticker, broker);
        broker.connect();

        String orderId = broker.getNextOrderId();

        OrderTicket order = new OrderTicket(orderId, ticker, BigDecimal.valueOf(1.0), TradeDirection.BUY);
        order.setType(Type.MARKET);

        broker.placeOrder(order);

        broker.addOrderEventListener((event) -> {
            logger.info("Order Event: {}", event);
        });

        Thread.sleep(2000);
        broker.getAllPositions().forEach(position -> {
            logger.info("Position: {}", position);
        });

    }

    public static void main(String[] args) throws Exception {
        TradingPaperBrokerWithParadexData example = new TradingPaperBrokerWithParadexData();
        example.runExample();
    }
}
