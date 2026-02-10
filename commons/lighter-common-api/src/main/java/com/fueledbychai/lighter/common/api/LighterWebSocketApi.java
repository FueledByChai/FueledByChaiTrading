package com.fueledbychai.lighter.common.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.lighter.common.api.ws.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.LighterMarketStatsWebSocketProcessor;
import com.fueledbychai.lighter.common.api.ws.LighterWSClientBuilder;
import com.fueledbychai.lighter.common.api.ws.LighterWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWebSocketApi implements ILighterWebSocketApi {

    private static final Logger logger = LoggerFactory.getLogger(LighterWebSocketApi.class);

    private final String webSocketUrl;
    private final Map<String, LighterWebSocketClient> channelClients = new ConcurrentHashMap<>();
    private final Map<String, LighterMarketStatsWebSocketProcessor> channelProcessors = new ConcurrentHashMap<>();

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
        return subscribe(channel, listener);
    }

    @Override
    public LighterWebSocketClient subscribeAllMarketStats(ILighterMarketStatsListener listener) {
        return subscribe(LighterWSClientBuilder.WS_TYPE_MARKET_STATS_ALL, listener);
    }

    protected synchronized LighterWebSocketClient subscribe(String channel, ILighterMarketStatsListener listener) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }

        LighterMarketStatsWebSocketProcessor processor = channelProcessors.get(channel);
        if (processor == null) {
            processor = createProcessor(channel);
            LighterWebSocketClient client = createClient(channel, processor);
            channelProcessors.put(channel, processor);
            channelClients.put(channel, client);
            logger.info("Connecting to Lighter websocket channel: {} using {}", channel, webSocketUrl);
            client.connect();
        }

        processor.addEventListener(listener);
        return channelClients.get(channel);
    }

    @Override
    public synchronized void disconnectAll() {
        for (LighterWebSocketClient client : channelClients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("Error closing websocket client", e);
            }
        }
        for (LighterMarketStatsWebSocketProcessor processor : channelProcessors.values()) {
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down websocket processor", e);
            }
        }
        channelClients.clear();
        channelProcessors.clear();
    }

    protected LighterMarketStatsWebSocketProcessor createProcessor(String channel) {
        return new LighterMarketStatsWebSocketProcessor(() -> {
            logger.info("Lighter websocket closed for channel: {}", channel);
            channelClients.remove(channel);
            channelProcessors.remove(channel);
        });
    }

    protected LighterWebSocketClient createClient(String channel, IWebSocketProcessor processor) {
        try {
            return LighterWSClientBuilder.buildClient(webSocketUrl, channel, processor);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create Lighter websocket client for channel " + channel, e);
        }
    }
}
