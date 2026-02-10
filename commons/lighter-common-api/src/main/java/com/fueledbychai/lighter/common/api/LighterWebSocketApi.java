package com.fueledbychai.lighter.common.api;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.lighter.common.api.ws.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.ILighterOrderBookListener;
import com.fueledbychai.lighter.common.api.ws.LighterMarketStatsWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.LighterOrderBookWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.LighterWSClientBuilder;
import com.fueledbychai.lighter.common.api.ws.LighterWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWebSocketApi implements ILighterWebSocketApi {

    private static final Logger logger = LoggerFactory.getLogger(LighterWebSocketApi.class);
    private static final long DEFAULT_RECONNECT_DELAY_MILLIS = 2_000L;

    protected enum ChannelType {
        MARKET_STATS, ORDER_BOOK
    }

    private final String webSocketUrl;
    private final Map<String, LighterWebSocketClient> channelClients = new ConcurrentHashMap<>();
    private final Map<String, LighterMarketStatsWebSocketProcessor> marketStatsProcessors = new ConcurrentHashMap<>();
    private final Map<String, LighterOrderBookWebSocketProcessor> orderBookProcessors = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> reconnectTasks = new ConcurrentHashMap<>();
    private final Set<String> manualCloseChannels = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "lighter-ws-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    public LighterWebSocketApi() {
        this(LighterConfiguration.getInstance().getWebSocketUrl());
    }

    public LighterWebSocketApi(String webSocketUrl) {
        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            throw new IllegalArgumentException("webSocketUrl is required");
        }
        this.webSocketUrl = webSocketUrl;
    }

    @Override
    public LighterWebSocketClient subscribeMarketStats(int marketId, ILighterMarketStatsListener listener) {
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }
        String channel = LighterWSClientBuilder.getMarketStatsChannel(marketId);
        return subscribeMarketStats(channel, listener);
    }

    @Override
    public LighterWebSocketClient subscribeAllMarketStats(ILighterMarketStatsListener listener) {
        return subscribeMarketStats(LighterWSClientBuilder.WS_TYPE_MARKET_STATS_ALL, listener);
    }

    @Override
    public LighterWebSocketClient subscribeOrderBook(int marketId, ILighterOrderBookListener listener) {
        if (marketId < 0) {
            throw new IllegalArgumentException("marketId must be >= 0");
        }
        String channel = LighterWSClientBuilder.getOrderBookChannel(marketId);
        return subscribeOrderBook(channel, listener);
    }

    protected synchronized LighterWebSocketClient subscribeMarketStats(String channel,
            ILighterMarketStatsListener listener) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        manualCloseChannels.remove(channel);
        cancelReconnectTask(channel);

        LighterMarketStatsWebSocketProcessor processor = marketStatsProcessors.get(channel);
        if (processor == null) {
            processor = createMarketStatsProcessor(channel);
            LighterWebSocketClient client = createClient(channel, processor);
            marketStatsProcessors.put(channel, processor);
            channelClients.put(channel, client);
            logger.info("Connecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            client.connect();
        }

        processor.addEventListener(listener);
        return channelClients.get(channel);
    }

    protected synchronized LighterWebSocketClient subscribeOrderBook(String channel, ILighterOrderBookListener listener) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        manualCloseChannels.remove(channel);
        cancelReconnectTask(channel);

        LighterOrderBookWebSocketProcessor processor = orderBookProcessors.get(channel);
        if (processor == null) {
            processor = createOrderBookProcessor(channel);
            LighterWebSocketClient client = createClient(channel, processor);
            orderBookProcessors.put(channel, processor);
            channelClients.put(channel, client);
            logger.info("Connecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            client.connect();
        }

        processor.addEventListener(listener);
        return channelClients.get(channel);
    }

    @Override
    public synchronized void disconnectAll() {
        manualCloseChannels.addAll(channelClients.keySet());
        cancelAllReconnectTasks();

        for (LighterWebSocketClient client : channelClients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("Error closing websocket client", e);
            }
        }
        for (LighterMarketStatsWebSocketProcessor processor : marketStatsProcessors.values()) {
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down websocket processor", e);
            }
        }
        for (LighterOrderBookWebSocketProcessor processor : orderBookProcessors.values()) {
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down websocket processor", e);
            }
        }
        channelClients.clear();
        marketStatsProcessors.clear();
        orderBookProcessors.clear();
        manualCloseChannels.clear();
    }

    protected LighterMarketStatsWebSocketProcessor createMarketStatsProcessor(String channel) {
        return new LighterMarketStatsWebSocketProcessor(() -> {
            handleChannelClosed(channel, ChannelType.MARKET_STATS);
        });
    }

    protected LighterOrderBookWebSocketProcessor createOrderBookProcessor(String channel) {
        return new LighterOrderBookWebSocketProcessor(() -> {
            handleChannelClosed(channel, ChannelType.ORDER_BOOK);
        });
    }

    protected LighterWebSocketClient createClient(String channel, IWebSocketProcessor processor) {
        try {
            return LighterWSClientBuilder.buildClient(webSocketUrl, channel, processor);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create Lighter websocket client for channel " + channel, e);
        }
    }

    protected long getReconnectDelayMillis() {
        return DEFAULT_RECONNECT_DELAY_MILLIS;
    }

    protected void handleChannelClosed(String channel, ChannelType type) {
        logger.info("Lighter websocket closed for channel: {}", channel);
        channelClients.remove(channel);
        if (manualCloseChannels.remove(channel)) {
            return;
        }
        scheduleReconnect(channel, type);
    }

    protected void scheduleReconnect(String channel, ChannelType type) {
        if (!hasProcessor(channel, type)) {
            return;
        }
        ScheduledFuture<?> existingTask = reconnectTasks.get(channel);
        if (existingTask != null && !existingTask.isDone()) {
            return;
        }

        long delay = getReconnectDelayMillis();
        ScheduledFuture<?> task = reconnectScheduler.schedule(() -> reconnectChannel(channel, type), delay,
                TimeUnit.MILLISECONDS);
        reconnectTasks.put(channel, task);
        logger.info("Scheduled reconnect for channel {} in {} ms", channel, delay);
    }

    protected synchronized void reconnectChannel(String channel, ChannelType type) {
        reconnectTasks.remove(channel);
        if (manualCloseChannels.contains(channel)) {
            return;
        }
        if (channelClients.containsKey(channel)) {
            return;
        }

        IWebSocketProcessor processor = getProcessor(channel, type);
        if (processor == null) {
            return;
        }

        try {
            LighterWebSocketClient client = createClient(channel, processor);
            channelClients.put(channel, client);
            logger.info("Reconnecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            client.connect();
        } catch (Exception e) {
            logger.warn("Reconnect attempt failed for channel {}", channel, e);
            scheduleReconnect(channel, type);
        }
    }

    protected IWebSocketProcessor getProcessor(String channel, ChannelType type) {
        return switch (type) {
            case MARKET_STATS -> marketStatsProcessors.get(channel);
            case ORDER_BOOK -> orderBookProcessors.get(channel);
        };
    }

    protected boolean hasProcessor(String channel, ChannelType type) {
        return getProcessor(channel, type) != null;
    }

    protected void cancelReconnectTask(String channel) {
        ScheduledFuture<?> task = reconnectTasks.remove(channel);
        if (task != null) {
            task.cancel(false);
        }
    }

    protected void cancelAllReconnectTasks() {
        for (ScheduledFuture<?> task : reconnectTasks.values()) {
            if (task != null) {
                task.cancel(false);
            }
        }
        reconnectTasks.clear();
    }
}
