package com.fueledbychai.marketdata.extended;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.extended.common.api.ExtendedConfiguration;
import com.fueledbychai.extended.common.api.ws.ExtendedStreamUrls;
import com.fueledbychai.extended.common.api.ws.ExtendedWebSocketClient;

/**
 * Lazily creates and caches one {@link ExtendedOrderBook} per ticker, starting
 * the underlying order-book WebSocket feed the first time a book is requested.
 */
public class OrderBookRegistry {
    protected static final Logger logger = LoggerFactory.getLogger(OrderBookRegistry.class);
    protected static OrderBookRegistry instance = null;
    protected Map<Ticker, ExtendedOrderBook> orderBooks = new HashMap<>();

    public static synchronized OrderBookRegistry getInstance() {
        if (instance == null) {
            instance = new OrderBookRegistry();
        }
        return instance;
    }

    public synchronized IExtendedOrderBook getOrderBook(Ticker ticker) {
        ExtendedOrderBook orderBook = orderBooks.get(ticker);
        if (orderBook == null) {
            orderBook = new ExtendedOrderBook(ticker);
            orderBooks.put(ticker, orderBook);
            startMarketBookWSClient(ticker, orderBook);
        }
        return orderBook;
    }

    public void startMarketBookWSClient(Ticker ticker, IExtendedOrderBook orderBook) {
        try {
            logger.info("Starting Extended order book WebSocket client for {}", ticker.getSymbol());
            String baseWsUrl = ExtendedConfiguration.getInstance().getWebSocketUrl();
            String streamUrl = new ExtendedStreamUrls(baseWsUrl).orderBook(ticker.getSymbol(), 1);
            ExtendedWebSocketClient orderBookWSClient = new ExtendedWebSocketClient(streamUrl,
                    "orderbooks." + ticker.getSymbol(),
                    new ExtendedOrderBookProcessor(orderBook, () -> {
                        logger.info("Extended order book WebSocket closed, trying to restart...");
                        startMarketBookWSClient(ticker, orderBook);
                    }), null);
            orderBookWSClient.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
