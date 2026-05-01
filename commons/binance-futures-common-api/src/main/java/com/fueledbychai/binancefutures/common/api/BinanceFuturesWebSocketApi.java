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
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.websocket.IWebSocketEventListener;

/**
 * Reusable websocket session manager for Binance futures and options market-data streams.
 */
public class BinanceFuturesWebSocketApi implements IBinanceFuturesWebSocketApi {

    private static final Logger logger = LoggerFactory.getLogger(BinanceFuturesWebSocketApi.class);
    private static final long RECONNECT_DELAY_MILLIS = 1000L;

    protected final String futuresWebSocketUrl;
    protected final String optionsWebSocketUrl;
    protected final String webSocketUrl;
    // Binance announced a WS URL split (legacy /ws decommissioned 2026-04-23)
    // routing channels into traffic categories: bookTicker + depth land on
    // /public/ws; ticker, markPrice, aggTrade, kline, miniTicker, etc. land
    // on /market/ws. Connecting to the legacy /ws URL silently drops
    // anything outside the public set — SUBSCRIBE is acked but no frames
    // flow. We derive both per-category URLs from whatever base the caller
    // configured so existing BINANCE_FUTURES_MAINNET_WS_URL overrides keep
    // working without a config schema change.
    protected final String futuresPublicWebSocketUrl;
    protected final String futuresMarketWebSocketUrl;
    protected volatile boolean connected = false;
    protected volatile boolean orderEntryConnected = false;
    protected volatile boolean shuttingDown = false;
    protected final Map<String, BinanceFuturesWebSocketClient> clients = new ConcurrentHashMap<>();
    protected final Map<String, BinanceFuturesJsonProcessor> processors = new ConcurrentHashMap<>();
    protected final Map<String, String> streamChannels = new ConcurrentHashMap<>();
    protected final Map<String, String> streamUrls = new ConcurrentHashMap<>();
    protected final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "binance-futures-ws-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    public BinanceFuturesWebSocketApi(String webSocketUrl) {
        this(webSocketUrl, webSocketUrl);
    }

    public BinanceFuturesWebSocketApi(String futuresWebSocketUrl, String optionsWebSocketUrl) {
        if (futuresWebSocketUrl == null || futuresWebSocketUrl.isBlank()) {
            throw new IllegalArgumentException("futuresWebSocketUrl is required");
        }
        if (optionsWebSocketUrl == null || optionsWebSocketUrl.isBlank()) {
            throw new IllegalArgumentException("optionsWebSocketUrl is required");
        }
        this.futuresWebSocketUrl = futuresWebSocketUrl;
        this.optionsWebSocketUrl = optionsWebSocketUrl;
        this.webSocketUrl = futuresWebSocketUrl;
        String futuresBase = stripLegacyPath(futuresWebSocketUrl);
        this.futuresPublicWebSocketUrl = futuresBase + "/public/ws";
        this.futuresMarketWebSocketUrl = futuresBase + "/market/ws";
    }

    /**
     * Strip a trailing /ws, /public/ws, /market/ws, or /stream from a
     * configured Binance Futures WS URL so we can append the per-category
     * suffix. Tolerant of all the path forms the legacy + new docs use.
     */
    private static String stripLegacyPath(String url) {
        return url.replaceFirst("/(public/ws|market/ws|stream|ws)$", "");
    }

    /**
     * Maps a Binance Futures stream channel name to the new traffic
     * category Binance introduced after the 2026-04-23 URL split.
     * Public-category channels (bookTicker, depth, diff-depth) push only
     * to /public/ws; everything else (ticker, markPrice, aggTrade, kline,
     * miniTicker, forceOrder, etc.) pushes only to /market/ws.
     */
    static boolean isPublicChannel(String channel) {
        if (channel == null) {
            return false;
        }
        return channel.contains("@bookTicker") || channel.equals("!bookTicker")
                || channel.contains("@depth");
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
        return subscribe(ticker, BinanceFuturesWebSocketClientBuilder.bookTickerChannel(ticker), listener);
    }

    @Override
    public BinanceFuturesWebSocketClient subscribeSymbolTicker(Ticker ticker,
            IWebSocketEventListener<JsonNode> listener) {
        return subscribe(ticker, BinanceFuturesWebSocketClientBuilder.symbolTickerChannel(ticker), listener);
    }

    @Override
    public BinanceFuturesWebSocketClient subscribePartialDepth(Ticker ticker, int depth,
            IWebSocketEventListener<JsonNode> listener) {
        return subscribe(ticker, BinanceFuturesWebSocketClientBuilder.partialDepthChannel(ticker, depth), listener);
    }

    @Override
    public BinanceFuturesWebSocketClient subscribeAggTrades(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
        return subscribe(ticker, BinanceFuturesWebSocketClientBuilder.aggTradeChannel(ticker), listener);
    }

    @Override
    public BinanceFuturesWebSocketClient subscribeMarkPrice(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
        return subscribe(ticker, BinanceFuturesWebSocketClientBuilder.markPriceChannel(ticker), listener);
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
        streamChannels.clear();
        streamUrls.clear();
    }

    protected synchronized BinanceFuturesWebSocketClient subscribe(Ticker ticker, String channel,
            IWebSocketEventListener<JsonNode> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        shuttingDown = false;
        connected = true;

        String streamKey = streamKey(ticker, channel);
        String url = resolveWebSocketUrl(ticker, channel);
        streamChannels.put(streamKey, channel);
        streamUrls.put(streamKey, url);

        BinanceFuturesJsonProcessor processor = processors.computeIfAbsent(streamKey, this::newProcessor);
        processor.addEventListener(listener);

        BinanceFuturesWebSocketClient existing = clients.get(streamKey);
        if (existing != null) {
            return existing;
        }

        BinanceFuturesWebSocketClient client = newClient(url, channel, processor);
        clients.put(streamKey, client);
        client.connect();
        return client;
    }

    protected BinanceFuturesJsonProcessor newProcessor(String streamKey) {
        return new BinanceFuturesJsonProcessor(() -> scheduleReconnect(streamKey));
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
        BinanceFuturesJsonProcessor processor = processors.get(streamKey);
        if (processor == null) {
            return;
        }
        String channel = streamChannels.get(streamKey);
        String streamUrl = streamUrls.get(streamKey);
        if (channel == null || streamUrl == null) {
            return;
        }
        try {
            BinanceFuturesWebSocketClient oldClient = clients.get(streamKey);
            if (oldClient != null) {
                try { oldClient.close(); } catch (Exception e) { /* already closing */ }
            }
            BinanceFuturesWebSocketClient client = newClient(streamUrl, channel, processor);
            clients.put(streamKey, client);
            client.connect();
        } catch (RuntimeException e) {
            logger.warn("Failed to reconnect Binance futures websocket channel {}", channel, e);
        }
    }

    /**
     * Resolves the right WS endpoint for (ticker, channel). Options keep
     * the dedicated options URL (already on /public/ws). For futures, the
     * URL is selected by the channel's traffic category — see
     * {@link #isPublicChannel(String)} for the public/market split that
     * Binance enforces server-side after the 2026-04-23 cutover.
     */
    protected String resolveWebSocketUrl(Ticker ticker, String channel) {
        if (ticker != null) {
            InstrumentType instrumentType = ticker.getInstrumentType();
            if (instrumentType == InstrumentType.OPTION || instrumentType == InstrumentType.PERPETUAL_OPTION) {
                return optionsWebSocketUrl;
            }
        }
        return isPublicChannel(channel) ? futuresPublicWebSocketUrl : futuresMarketWebSocketUrl;
    }

    protected String streamKey(Ticker ticker, String channel) {
        return resolveWebSocketUrl(ticker, channel) + "|" + channel;
    }

    protected BinanceFuturesWebSocketClient newClient(String streamUrl, String channel,
            BinanceFuturesJsonProcessor processor) {
        try {
            return new BinanceFuturesWebSocketClient(streamUrl, channel, processor);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Binance futures websocket client", e);
        }
    }
}
