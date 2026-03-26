package com.fueledbychai.aster.common.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.aster.common.api.ws.AsterJsonProcessor;
import com.fueledbychai.aster.common.api.ws.AsterMultiplexingProcessor;
import com.fueledbychai.aster.common.api.ws.AsterWebSocketClient;
import com.fueledbychai.aster.common.api.ws.AsterWebSocketClientBuilder;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketEventListener;
import com.fueledbychai.websocket.IWebSocketProcessor;

/**
 * Reusable websocket session manager for Aster spot and futures market-data
 * streams, plus the existing futures user-data stream.
 *
 * Supports two modes:
 * <ul>
 * <li><b>Shared mode</b> (default): multiple channels share a single WebSocket
 * connection, up to 100 subscriptions per socket. Additional sockets are
 * opened as needed.</li>
 * <li><b>Dedicated mode</b>: one WebSocket connection per channel for lowest
 * latency. Enable via {@code aster.ws.dedicated.mode=true}.</li>
 * </ul>
 */
public class AsterWebSocketApi implements IAsterWebSocketApi {

    private static final Logger logger = LoggerFactory.getLogger(AsterWebSocketApi.class);
    private static final long RECONNECT_DELAY_MILLIS = 1_000L;
    private static final int MAX_SUBSCRIPTIONS_PER_SHARED_CLIENT = 100;

    protected final String futuresWebSocketUrl;
    protected final String spotWebSocketUrl;
    protected final boolean dedicatedMode;
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

    // Shared-mode pooling (per URL)
    protected final List<SharedClientSlot> sharedClientSlots = new ArrayList<>();
    protected final Map<String, SharedClientSlot> channelToSharedSlot = new ConcurrentHashMap<>();

    public AsterWebSocketApi(String webSocketUrl) {
        this(webSocketUrl, deriveSpotWebSocketUrl(webSocketUrl),
                AsterConfiguration.getInstance().isDedicatedWebSocketMode());
    }

    public AsterWebSocketApi(String futuresWebSocketUrl, String spotWebSocketUrl) {
        this(futuresWebSocketUrl, spotWebSocketUrl, false);
    }

    public AsterWebSocketApi(String futuresWebSocketUrl, String spotWebSocketUrl, boolean dedicatedMode) {
        if (futuresWebSocketUrl == null || futuresWebSocketUrl.isBlank()) {
            throw new IllegalArgumentException("futuresWebSocketUrl is required");
        }
        if (spotWebSocketUrl == null || spotWebSocketUrl.isBlank()) {
            throw new IllegalArgumentException("spotWebSocketUrl is required");
        }
        this.futuresWebSocketUrl = normalizeWebSocketUrl(futuresWebSocketUrl);
        this.spotWebSocketUrl = normalizeWebSocketUrl(spotWebSocketUrl);
        this.dedicatedMode = dedicatedMode;
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
        for (SharedClientSlot slot : sharedClientSlots) {
            if (slot.client != null) {
                try {
                    slot.client.close();
                } catch (Exception e) {
                    logger.debug("Ignoring shared websocket close failure", e);
                }
            }
            slot.channels.clear();
        }
        sharedClientSlots.clear();
        channelToSharedSlot.clear();
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

        if (dedicatedMode) {
            AsterWebSocketClient client = newClient(streamUrl, channel, processor);
            clients.put(streamKey, client);
            client.connect();
            return client;
        }

        // Shared mode: pool channels onto shared sockets
        SharedClientSlot slot = acquireSharedSlot(streamUrl, channel, processor);
        clients.put(streamKey, slot.client);
        return slot.client;
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

    protected AsterWebSocketClient newClient(String streamUrl, String channel, IWebSocketProcessor processor) {
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

    // ---- Shared-mode WebSocket pooling ----

    protected synchronized SharedClientSlot acquireSharedSlot(String streamUrl, String channel,
            AsterJsonProcessor processor) {
        SharedClientSlot existing = channelToSharedSlot.get(channel);
        if (existing != null) {
            return existing;
        }

        // Find a slot for this URL with capacity
        for (SharedClientSlot slot : sharedClientSlots) {
            if (slot.streamUrl.equals(streamUrl) && slot.channels.size() < MAX_SUBSCRIPTIONS_PER_SHARED_CLIENT) {
                addChannelToSlot(slot, channel, processor);
                return slot;
            }
        }

        // No capacity — create a new shared client for this URL
        SharedClientSlot slot = createSharedClientSlot(streamUrl);
        sharedClientSlots.add(slot);
        addChannelToSlot(slot, channel, processor);
        logger.info("Opening shared Aster WebSocket #{} for {} (channel: {})", sharedClientSlots.size(), streamUrl,
                channel);
        slot.client.connect();
        return slot;
    }

    protected void addChannelToSlot(SharedClientSlot slot, String channel, AsterJsonProcessor processor) {
        slot.channels.add(channel);
        slot.multiplexor.addProcessor(channel, processor);
        channelToSharedSlot.put(channel, slot);

        if (slot.client != null && slot.client.isOpen()) {
            slot.client.sendCommand("SUBSCRIBE", channel);
        }
    }

    protected SharedClientSlot createSharedClientSlot(String streamUrl) {
        SharedClientSlot slot = new SharedClientSlot();
        slot.streamUrl = streamUrl;
        slot.multiplexor = new AsterMultiplexingProcessor(() -> handleSharedClientClosed(slot));
        slot.multiplexor.setOnConnectedCallback(() -> onSharedClientConnected(slot));
        slot.client = newClient(streamUrl, null, slot.multiplexor);
        return slot;
    }

    protected void onSharedClientConnected(SharedClientSlot slot) {
        slot.reconnectAttempts.set(0);
        if (!slot.channels.isEmpty()) {
            slot.client.sendCommand("SUBSCRIBE", slot.channels);
        }
    }

    protected void handleSharedClientClosed(SharedClientSlot slot) {
        logger.warn("Shared Aster WebSocket closed with {} channels", slot.channels.size());
        if (shuttingDown || slot.channels.isEmpty()) {
            return;
        }
        scheduleSharedClientReconnect(slot);
    }

    protected void scheduleSharedClientReconnect(SharedClientSlot slot) {
        int attempt = slot.reconnectAttempts.incrementAndGet();
        reconnectExecutor.schedule(() -> reconnectSharedClient(slot), RECONNECT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        logger.warn("Scheduled shared Aster client reconnect (attempt {}) for {} channels", attempt,
                slot.channels.size());
    }

    protected synchronized void reconnectSharedClient(SharedClientSlot slot) {
        if (shuttingDown || slot.channels.isEmpty()) {
            return;
        }
        try {
            AsterWebSocketClient oldClient = slot.client;
            if (oldClient != null) {
                try {
                    oldClient.close();
                } catch (Exception ignored) {
                }
            }
            slot.client = newClient(slot.streamUrl, null, slot.multiplexor);
            for (String ch : slot.channels) {
                String streamKey = streamKey(slot.streamUrl, ch);
                clients.put(streamKey, slot.client);
            }
            slot.client.connect();
        } catch (Exception e) {
            logger.error("Shared Aster client reconnect failed", e);
            scheduleSharedClientReconnect(slot);
        }
    }

    protected static class SharedClientSlot {
        volatile AsterWebSocketClient client;
        AsterMultiplexingProcessor multiplexor;
        String streamUrl;
        final Set<String> channels = ConcurrentHashMap.newKeySet();
        final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    }
}
