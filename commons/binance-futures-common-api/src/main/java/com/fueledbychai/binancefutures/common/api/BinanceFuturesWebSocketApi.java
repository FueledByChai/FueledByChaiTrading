package com.fueledbychai.binancefutures.common.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.binancefutures.common.api.ws.BinanceFuturesJsonProcessor;
import com.fueledbychai.binancefutures.common.api.ws.BinanceFuturesWebSocketClient;
import com.fueledbychai.binancefutures.common.api.ws.BinanceFuturesWebSocketClientBuilder;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketEventListener;

/**
 * Reusable websocket session manager for Binance futures market-data streams.
 */
public class BinanceFuturesWebSocketApi implements IBinanceFuturesWebSocketApi {

    private static final Logger logger = LoggerFactory.getLogger(BinanceFuturesWebSocketApi.class);
    private static final long RECONNECT_DELAY_MILLIS = 1000L;

    protected final String webSocketUrl;
    protected volatile boolean connected = false;
    protected volatile boolean orderEntryConnected = false;
    protected volatile boolean shuttingDown = false;
    protected final Map<String, BinanceFuturesWebSocketClient> clients = new ConcurrentHashMap<>();
    protected final Map<String, BinanceFuturesJsonProcessor> processors = new ConcurrentHashMap<>();
    protected final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "binance-futures-ws-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    public BinanceFuturesWebSocketApi(String webSocketUrl) {
        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            throw new IllegalArgumentException("webSocketUrl is required");
        }
        this.webSocketUrl = webSocketUrl;
    }

    @Override
    public void connect() {
        connected = true;
    }

    @Override
    public void connectOrderEntryWebSocket() {
        orderEntryConnected = true;
    }

    @Override
    public BinanceFuturesWebSocketClient subscribeBookTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
        return subscribe(BinanceFuturesWebSocketClientBuilder.bookTickerChannel(ticker), listener);
    }

    @Override
    public BinanceFuturesWebSocketClient subscribeSymbolTicker(Ticker ticker,
            IWebSocketEventListener<JsonNode> listener) {
        return subscribe(BinanceFuturesWebSocketClientBuilder.symbolTickerChannel(ticker), listener);
    }

    @Override
    public BinanceFuturesWebSocketClient subscribePartialDepth(Ticker ticker, int depth,
            IWebSocketEventListener<JsonNode> listener) {
        return subscribe(BinanceFuturesWebSocketClientBuilder.partialDepthChannel(ticker, depth), listener);
    }

    @Override
    public BinanceFuturesWebSocketClient subscribeAggTrades(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
        return subscribe(BinanceFuturesWebSocketClientBuilder.aggTradeChannel(ticker), listener);
    }

    @Override
    public BinanceFuturesWebSocketClient subscribeMarkPrice(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
        return subscribe(BinanceFuturesWebSocketClientBuilder.markPriceChannel(ticker), listener);
    }

    @Override
    public void disconnectAll() {
        shuttingDown = true;
        connected = false;
        orderEntryConnected = false;
        for (BinanceFuturesWebSocketClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                logger.debug("Ignoring websocket close failure", e);
            }
        }
        clients.clear();
        for (BinanceFuturesJsonProcessor processor : processors.values()) {
            processor.shutdown();
        }
        processors.clear();
    }

    protected synchronized BinanceFuturesWebSocketClient subscribe(String channel,
            IWebSocketEventListener<JsonNode> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        shuttingDown = false;
        connected = true;

        BinanceFuturesJsonProcessor processor = processors.computeIfAbsent(channel, this::newProcessor);
        processor.addEventListener(listener);

        BinanceFuturesWebSocketClient existing = clients.get(channel);
        if (existing != null) {
            return existing;
        }

        BinanceFuturesWebSocketClient client = newClient(channel, processor);
        clients.put(channel, client);
        client.connect();
        return client;
    }

    protected BinanceFuturesJsonProcessor newProcessor(String channel) {
        return new BinanceFuturesJsonProcessor(() -> scheduleReconnect(channel));
    }

    protected void scheduleReconnect(String channel) {
        if (shuttingDown) {
            return;
        }
        reconnectExecutor.schedule(() -> reconnect(channel), RECONNECT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    protected synchronized void reconnect(String channel) {
        if (shuttingDown) {
            return;
        }
        BinanceFuturesJsonProcessor processor = processors.get(channel);
        if (processor == null) {
            return;
        }
        try {
            BinanceFuturesWebSocketClient client = newClient(channel, processor);
            clients.put(channel, client);
            client.connect();
        } catch (RuntimeException e) {
            logger.warn("Failed to reconnect Binance futures websocket channel {}", channel, e);
        }
    }

    protected BinanceFuturesWebSocketClient newClient(String channel, BinanceFuturesJsonProcessor processor) {
        try {
            return new BinanceFuturesWebSocketClient(webSocketUrl, channel, processor);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Binance futures websocket client", e);
        }
    }
}
