package com.fueledbychai.aster.common.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.aster.common.api.ws.AsterJsonProcessor;
import com.fueledbychai.aster.common.api.ws.AsterWebSocketClient;
import com.fueledbychai.aster.common.api.ws.AsterWebSocketClientBuilder;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketEventListener;

/**
 * Reusable websocket session manager for Aster futures market-data streams.
 */
public class AsterWebSocketApi implements IAsterWebSocketApi {

    private static final Logger logger = LoggerFactory.getLogger(AsterWebSocketApi.class);
    private static final long RECONNECT_DELAY_MILLIS = 1_000L;

    protected final String webSocketUrl;
    protected volatile boolean connected = false;
    protected volatile boolean orderEntryConnected = false;
    protected volatile boolean shuttingDown = false;
    protected final Map<String, AsterWebSocketClient> clients = new ConcurrentHashMap<>();
    protected final Map<String, AsterJsonProcessor> processors = new ConcurrentHashMap<>();
    protected final Map<String, String> streamChannels = new ConcurrentHashMap<>();
    protected final Map<String, String> streamUrls = new ConcurrentHashMap<>();
    protected final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "aster-ws-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    public AsterWebSocketApi(String webSocketUrl) {
        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            throw new IllegalArgumentException("webSocketUrl is required");
        }
        this.webSocketUrl = normalizeWebSocketUrl(webSocketUrl);
    }

    @Override
    public void connect() {
        connected = true;
        shuttingDown = false;
    }

    @Override
    public void connectOrderEntryWebSocket() {
        orderEntryConnected = true;
    }

    @Override
    public AsterWebSocketClient subscribeBookTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
        return subscribe(ticker, AsterWebSocketClientBuilder.bookTickerChannel(ticker), listener);
    }

    @Override
    public AsterWebSocketClient subscribeSymbolTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
        return subscribe(ticker, AsterWebSocketClientBuilder.symbolTickerChannel(ticker), listener);
    }

    @Override
    public AsterWebSocketClient subscribePartialDepth(Ticker ticker, int depth,
            IWebSocketEventListener<JsonNode> listener) {
        return subscribe(ticker, AsterWebSocketClientBuilder.partialDepthChannel(ticker, depth), listener);
    }

    @Override
    public AsterWebSocketClient subscribeAggTrades(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
        return subscribe(ticker, AsterWebSocketClientBuilder.aggTradeChannel(ticker), listener);
    }

    @Override
    public AsterWebSocketClient subscribeMarkPrice(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
        return subscribe(ticker, AsterWebSocketClientBuilder.markPriceChannel(ticker), listener);
    }

    @Override
    public AsterWebSocketClient subscribeUserData(String listenKey, IWebSocketEventListener<JsonNode> listener) {
        if (listenKey == null || listenKey.isBlank()) {
            throw new IllegalArgumentException("listenKey is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }

        shuttingDown = false;
        connected = true;
        String streamUrl = AsterWebSocketClientBuilder.userDataUrl(webSocketUrl, listenKey);
        String streamKey = streamKey(streamUrl, "");
        streamChannels.put(streamKey, "");
        streamUrls.put(streamKey, streamUrl);

        AsterJsonProcessor processor = processors.computeIfAbsent(streamKey, this::newProcessor);
        processor.addEventListener(listener);

        AsterWebSocketClient existing = clients.get(streamKey);
        if (existing != null) {
            return existing;
        }

        AsterWebSocketClient client = newClient(streamUrl, null, processor);
        clients.put(streamKey, client);
        client.connect();
        return client;
    }

    @Override
    public void disconnectAll() {
        shuttingDown = true;
        connected = false;
        orderEntryConnected = false;
        for (AsterWebSocketClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                logger.debug("Ignoring websocket close failure", e);
            }
        }
        clients.clear();
        for (AsterJsonProcessor processor : processors.values()) {
            processor.shutdown();
        }
        processors.clear();
        streamChannels.clear();
        streamUrls.clear();
    }

    protected synchronized AsterWebSocketClient subscribe(Ticker ticker, String channel,
            IWebSocketEventListener<JsonNode> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }

        shuttingDown = false;
        connected = true;

        String streamKey = streamKey(webSocketUrl, channel);
        streamChannels.put(streamKey, channel);
        streamUrls.put(streamKey, webSocketUrl);

        AsterJsonProcessor processor = processors.computeIfAbsent(streamKey, this::newProcessor);
        processor.addEventListener(listener);

        AsterWebSocketClient existing = clients.get(streamKey);
        if (existing != null) {
            return existing;
        }

        AsterWebSocketClient client = newClient(webSocketUrl, channel, processor);
        clients.put(streamKey, client);
        client.connect();
        return client;
    }

    protected AsterJsonProcessor newProcessor(String streamKey) {
        return new AsterJsonProcessor(() -> scheduleReconnect(streamKey));
    }

    protected void scheduleReconnect(String streamKey) {
        if (shuttingDown) {
            return;
        }
        reconnectExecutor.schedule(() -> reconnect(streamKey), RECONNECT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    protected synchronized void reconnect(String streamKey) {
        if (shuttingDown) {
            return;
        }
        AsterJsonProcessor processor = processors.get(streamKey);
        if (processor == null) {
            return;
        }
        String channel = streamChannels.get(streamKey);
        String streamUrl = streamUrls.get(streamKey);
        if (streamUrl == null) {
            return;
        }
        try {
            AsterWebSocketClient client = newClient(streamUrl, channel == null || channel.isBlank() ? null : channel,
                    processor);
            clients.put(streamKey, client);
            client.connect();
        } catch (RuntimeException e) {
            logger.warn("Failed to reconnect Aster websocket channel {}", channel, e);
        }
    }

    protected AsterWebSocketClient newClient(String streamUrl, String channel, AsterJsonProcessor processor) {
        try {
            return new AsterWebSocketClient(streamUrl, channel, processor);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Aster websocket client", e);
        }
    }

    protected String streamKey(String streamUrl, String channel) {
        return streamUrl + "|" + (channel == null ? "" : channel);
    }

    protected String normalizeWebSocketUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
