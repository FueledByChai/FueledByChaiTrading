package com.fueledbychai.marketdata.paradex;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.common.api.ws.ParadexWebSocketClient;

public class OrderBookRegistry {
    protected static final Logger logger = LoggerFactory.getLogger(OrderBookRegistry.class);
    protected static OrderBookRegistry instance = null;
    protected Map<Ticker, ParadexOrderBook> orderBooks = new HashMap<>();
    protected String wsUrl = "wss://ws.api.prod.paradex.trade/v1";

    public static OrderBookRegistry getInstance() {
        if (instance == null) {
            instance = new OrderBookRegistry();
        }
        return instance;
    }

    public IParadexOrderBook getOrderBook(Ticker ticker) {
        ParadexOrderBook orderBook = orderBooks.get(ticker);
        if (orderBook == null) {
            orderBook = new ParadexOrderBook(ticker);
            orderBooks.put(ticker, orderBook);
            startMarketBookWSClient(ticker, orderBook);
        }
        return orderBook;
    }

    public void startMarketBookWSClient(Ticker ticker, IParadexOrderBook orderBook) {
        try {
            logger.info("Starting order book WebSocket client");
            // ParadexWebSocketClient orderBookWSClient = new ParadexWebSocketClient(wsUrl,
            // "order_book." + ticker.getSymbol() + ".deltas", new
            // MarketBookWebSocketProcessor(orderBook, () -> {
            // logger.info("Order book WebSocket closed, trying to restart...");
            // startMarketBookWSClient(ticker, orderBook);
            // }));
            // Channel suffix is overridable so a data collector can opt into the true delta
            // stream ("deltas") while trading keeps the throttled top-15 snapshot channel.
            String channelSuffix = System.getProperty("paradex.orderbook.channel.suffix", "interactive@15@200ms");
            ParadexWebSocketClient orderBookWSClient = new ParadexWebSocketClient(wsUrl,
                    "order_book." + ticker.getSymbol() + "." + channelSuffix,
                    new MarketBookWebSocketProcessor(orderBook, () -> {
                        logger.info("Order book WebSocket closed, trying to restart...");
                        startMarketBookWSClient(ticker, orderBook);
                    }));
            orderBookWSClient.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

}
