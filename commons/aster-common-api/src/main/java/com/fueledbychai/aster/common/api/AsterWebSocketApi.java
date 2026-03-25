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
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketEventListener;

/**
 * Reusable websocket session manager for Aster spot and futures market-data
 * streams, plus the existing futures user-data stream.
 */
public class AsterWebSocketApi implements IAsterWebSocketApi {

    private static final Logger logger = LoggerFactory.getLogger(AsterWebSocketApi.class);
    private static final long RECONNECT_DELAY_MILLIS = 1_000L;

    protected final String futuresWebSocketUrl;
    protected final String spotWebSocketUrl;
    protected volatile boolean connected = false;
    protected volatile boolean orderEntryConnected = false;
    protected volatile boolean shuttingDown = false;
    protected final Map<String, AsterWebSocketClient> clients = new ConcurrentHashMap<>();
    protected final Map<String, AsterJsonProcessor> processors = new ConcurrentHashMap<>();
    protected final Map<String, String> streamChannels = new ConcurrentHashMap<>();
    protected final Map<String, String> streamUrls = new ConcurrentHashMap<>();
    protected volatile String userDataStreamKey;
    protected final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "aster-ws-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    public AsterWebSocketApi(String webSocketUrl) {
        this(webSocketUrl, deriveSpotWebSocketUrl(webSocketUrl));
    }

    public AsterWebSocketApi(String futuresWebSocketUrl, String spotWebSocketUrl) {
        if (futuresWebSocketUrl == null || futuresWebSocketUrl.isBlank()) {
            throw new IllegalArgumentException("futuresWebSocketUrl is required");
        }
        if (spotWebSocketUrl == null || spotWebSocketUrl.isBlank()) {
            throw new IllegalArgumentException("spotWebSocketUrl is required");
        }
        this.futuresWebSocketUrl = normalizeWebSocketUrl(futuresWebSocketUrl);
        this.spotWebSocketUrl = normalizeWebSocketUrl(spotWebSocketUrl);
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
        requirePerpetualTicker(ticker);
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
        // Disconnect previous user data stream if any (listenKey changed)
        disconnectUserData();

        String streamUrl = AsterWebSocketClientBuilder.userDataUrl(futuresWebSocketUrl, listenKey);
        String streamKey = streamKey(streamUrl, "");
        userDataStreamKey = streamKey;
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
    public void disconnectUserData() {
        String key = userDataStreamKey;
        if (key == null) {
            return;
        }
        userDataStreamKey = null;
        AsterWebSocketClient client = clients.remove(key);
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.debug("Ignoring user data websocket close failure", e);
            }
        }
        AsterJsonProcessor processor = processors.remove(key);
        if (processor != null) {
            processor.shutdown();
        }
        streamChannels.remove(key);
        streamUrls.remove(key);
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

        String streamUrl = resolveMarketDataWebSocketUrl(ticker);
        String streamKey = streamKey(streamUrl, channel);
        streamChannels.put(streamKey, channel);
        streamUrls.put(streamKey, streamUrl);

        AsterJsonProcessor processor = processors.computeIfAbsent(streamKey, this::newProcessor);
        processor.addEventListener(listener);

        AsterWebSocketClient existing = clients.get(streamKey);
        if (existing != null) {
            return existing;
        }

        AsterWebSocketClient client = newClient(streamUrl, channel, processor);
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
            AsterWebSocketClient oldClient = clients.get(streamKey);
            if (oldClient != null) {
                try { oldClient.close(); } catch (Exception e) { /* already closing */ }
            }
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

    protected String resolveMarketDataWebSocketUrl(Ticker ticker) {
        if (ticker != null && ticker.getInstrumentType() == InstrumentType.CRYPTO_SPOT) {
            return spotWebSocketUrl;
        }
        return futuresWebSocketUrl;
    }

    protected void requirePerpetualTicker(Ticker ticker) {
        if (ticker != null && ticker.getInstrumentType() == InstrumentType.CRYPTO_SPOT) {
            throw new IllegalArgumentException("Mark price stream is only available for Aster perpetual futures");
        }
    }

    protected static String deriveSpotWebSocketUrl(String futuresWebSocketUrl) {
        if (futuresWebSocketUrl == null || futuresWebSocketUrl.isBlank()) {
            throw new IllegalArgumentException("futuresWebSocketUrl is required");
        }

        String normalized = futuresWebSocketUrl.trim();
        String replaced = normalized.replace("://fstream.", "://sstream.");
        if (!replaced.equals(normalized)) {
            return replaced;
        }
        return normalized;
    }
}
