package com.fueledbychai.drift.common.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.json.JSONObject;

import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.drift.common.api.ws.DriftGatewayEventListener;
import com.fueledbychai.drift.common.api.ws.DriftOrderBookListener;
import com.fueledbychai.drift.common.api.ws.client.DriftWebSocketClient;
import com.fueledbychai.drift.common.api.ws.processor.DriftGatewayEventProcessor;
import com.fueledbychai.drift.common.api.ws.processor.DriftOrderBookProcessor;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;

public class DriftWebSocketApi implements IDriftWebSocketApi {

    protected final String dlobWebSocketUrl;
    protected final String gatewayWebSocketUrl;
    protected final Map<String, ManagedSubscription<?>> subscriptions = new ConcurrentHashMap<>();
    protected final ScheduledExecutorService reconnectExecutor = Executors
            .newSingleThreadScheduledExecutor(r -> new Thread(r, "drift-ws-reconnect"));

    public DriftWebSocketApi(String dlobWebSocketUrl, String gatewayWebSocketUrl) {
        if (dlobWebSocketUrl == null || dlobWebSocketUrl.isBlank()) {
            throw new IllegalArgumentException("dlobWebSocketUrl is required");
        }
        this.dlobWebSocketUrl = dlobWebSocketUrl;
        this.gatewayWebSocketUrl = gatewayWebSocketUrl;
    }

    @Override
    public DriftWebSocketClient subscribeOrderBook(String marketName, DriftMarketType marketType,
            DriftOrderBookListener listener) {
        if (marketName == null || marketName.isBlank()) {
            throw new IllegalArgumentException("marketName is required");
        }
        if (marketType == null) {
            throw new IllegalArgumentException("marketType is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }

        String key = "orderbook:" + marketType.getApiValue() + ":" + marketName;
        ManagedSubscription<DriftOrderBookProcessor> subscription = getOrCreateSubscription(key,
                () -> new DriftOrderBookProcessor(() -> scheduleReconnect(key)), processor -> {
                    String subscribeMessage = buildOrderBookSubscribeMessage(marketName, marketType);
                    return new DriftWebSocketClient(dlobWebSocketUrl, subscribeMessage, processor);
                });
        subscription.processor.addEventListener(listener);
        subscription.connectIfNeeded();
        return subscription.client;
    }

    @Override
    public DriftWebSocketClient subscribeGatewayEvents(int subAccountId, DriftGatewayEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        if (gatewayWebSocketUrl == null || gatewayWebSocketUrl.isBlank()) {
            throw new IllegalStateException("Drift gateway websocket URL is not configured");
        }

        String key = "gateway:" + subAccountId;
        ManagedSubscription<DriftGatewayEventProcessor> subscription = getOrCreateSubscription(key,
                () -> new DriftGatewayEventProcessor(() -> scheduleReconnect(key)), processor -> {
                    String subscribeMessage = buildGatewaySubscribeMessage(subAccountId);
                    return new DriftWebSocketClient(gatewayWebSocketUrl, subscribeMessage, processor);
                });
        subscription.processor.addEventListener(listener);
        subscription.connectIfNeeded();
        return subscription.client;
    }

    @SuppressWarnings("unchecked")
    protected <T extends AbstractWebSocketProcessor<?>> ManagedSubscription<T> getOrCreateSubscription(String key,
            Supplier<T> processorFactory, ClientFactory<T> clientFactory) {
        return (ManagedSubscription<T>) subscriptions.computeIfAbsent(key,
                ignored -> new ManagedSubscription<>(key, processorFactory.get(), clientFactory));
    }

    protected void scheduleReconnect(String key) {
        ManagedSubscription<?> subscription = subscriptions.get(key);
        if (subscription == null || subscription.closedByUser) {
            return;
        }
        reconnectExecutor.schedule(subscription::reconnect, subscription.nextReconnectDelayMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void disconnectAll() {
        for (ManagedSubscription<?> subscription : subscriptions.values()) {
            subscription.close();
        }
        subscriptions.clear();
        reconnectExecutor.shutdownNow();
    }

    protected String buildOrderBookSubscribeMessage(String marketName, DriftMarketType marketType) {
        JSONObject json = new JSONObject();
        json.put("type", "subscribe");
        json.put("channel", "orderbook");
        json.put("market", marketName);
        json.put("marketType", marketType.getApiValue());
        return json.toString();
    }

    protected String buildGatewaySubscribeMessage(int subAccountId) {
        JSONObject json = new JSONObject();
        json.put("method", "subscribe");
        json.put("subAccountId", subAccountId);
        return json.toString();
    }

    @FunctionalInterface
    protected interface ClientFactory<T extends AbstractWebSocketProcessor<?>> {
        DriftWebSocketClient create(T processor) throws Exception;
    }

    protected final class ManagedSubscription<T extends AbstractWebSocketProcessor<?>> {
        private final String key;
        private final T processor;
        private final ClientFactory<T> clientFactory;
        private volatile DriftWebSocketClient client;
        private volatile boolean closedByUser;
        private volatile int reconnectAttempts;

        private ManagedSubscription(String key, T processor, ClientFactory<T> clientFactory) {
            this.key = key;
            this.processor = processor;
            this.clientFactory = clientFactory;
        }

        private synchronized void connectIfNeeded() {
            if (client != null && client.isOpen()) {
                return;
            }
            connect();
        }

        private synchronized void reconnect() {
            if (closedByUser) {
                return;
            }
            connect();
        }

        private synchronized void connect() {
            try {
                DriftWebSocketClient newClient = clientFactory.create(processor);
                client = newClient;
                reconnectAttempts++;
                newClient.connect();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to connect Drift websocket subscription " + key, e);
            }
        }

        private long nextReconnectDelayMillis() {
            int attempt = Math.max(1, reconnectAttempts);
            return Math.min(10_000L, 500L * attempt);
        }

        private synchronized void close() {
            closedByUser = true;
            if (client != null) {
                client.close();
            }
            processor.shutdown();
        }
    }
}
