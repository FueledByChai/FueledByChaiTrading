package com.fueledbychai.broker.hibachi;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.hibachi.common.api.HibachiConfiguration;
import com.fueledbychai.hibachi.common.api.ws.HibachiJsonProcessor;
import com.fueledbychai.hibachi.common.api.ws.HibachiWebSocketClient;
import com.fueledbychai.hibachi.common.api.ws.account.HibachiAccountStreamMessages;
import com.fueledbychai.hibachi.common.api.ws.trade.HibachiTradeEnvelope;

/**
 * Account WebSocket client for Hibachi.
 *
 * <p>On connect: sends {@code stream.start}, captures the {@code listenKey} from the
 * response, then schedules {@code stream.ping} every
 * {@link HibachiConfiguration#getAccountWsPingSeconds()} (default 14s).
 *
 * <p>Auto-reconnects with exponential backoff if the WS closes unexpectedly.
 */
public class HibachiAccountStreamClient {

    private static final Logger logger = LoggerFactory.getLogger(HibachiAccountStreamClient.class);

    protected final HibachiConfiguration config;
    protected final long accountId;
    protected final String apiKey;
    protected volatile HibachiWebSocketClient client;
    protected volatile HibachiJsonProcessor processor;
    protected volatile ScheduledExecutorService pingScheduler;
    protected volatile ScheduledFuture<?> pingTask;
    protected volatile ScheduledExecutorService reconnectScheduler;
    protected volatile ScheduledFuture<?> reconnectTask;
    protected volatile long reconnectBackoffMs;
    protected volatile boolean shutdown;
    protected final AtomicLong listenKeyHolder = new AtomicLong();
    protected volatile String listenKey;
    protected volatile HibachiAccountEventListener eventListener;
    protected volatile Consumer<Boolean> connectionStateListener;

    public void setEventListener(HibachiAccountEventListener listener) {
        this.eventListener = listener;
    }

    /** Notified with {@code true} when the account WS opens, {@code false} when it closes. */
    public void setConnectionStateListener(Consumer<Boolean> listener) {
        this.connectionStateListener = listener;
    }

    public HibachiAccountStreamClient(HibachiConfiguration config, long accountId, String apiKey) {
        this.config = config;
        this.accountId = accountId;
        this.apiKey = apiKey;
        this.reconnectBackoffMs = Math.max(100L, config.getWsReconnectInitialBackoffMs());
    }

    public synchronized void connect() {
        if (client != null && client.isOpen()) {
            return;
        }
        shutdown = false;
        try {
            doConnect();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open Hibachi account WS", e);
        }
    }

    public synchronized void disconnect() {
        shutdown = true;
        cancelReconnect();
        stopPing();
        cleanupClient();
        listenKey = null;
        notifyState(false);
    }

    public boolean isConnected() {
        HibachiWebSocketClient c = client;
        return c != null && c.isOpen();
    }

    public String getListenKey() {
        return listenKey;
    }

    protected synchronized void doConnect() throws Exception {
        cleanupClient();
        processor = new HibachiJsonProcessor(this::onClosed);
        processor.addEventListener(this::onMessage);
        client = HibachiWebSocketClient.createPrivate(
                config.getAccountWsUrl(), String.valueOf(accountId), processor, apiKey, config.getClient());
        if (!client.connectBlocking(15, TimeUnit.SECONDS)) {
            cleanupClient();
            throw new IllegalStateException("Hibachi account WS handshake timed out");
        }
        sendStreamStart();
        startPing();
        cancelReconnect();
        reconnectBackoffMs = Math.max(100L, config.getWsReconnectInitialBackoffMs());
        notifyState(true);
    }

    protected synchronized void cleanupClient() {
        HibachiWebSocketClient c = client;
        client = null;
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
        if (processor != null) {
            try { processor.shutdown(); } catch (Exception ignored) {}
            processor = null;
        }
    }

    protected void sendStreamStart() {
        long id = HibachiTradeEnvelope.nextCorrelationId();
        client.send(HibachiAccountStreamMessages.buildStreamStart(id, accountId));
    }

    protected void startPing() {
        stopPing();
        long intervalSeconds = Math.max(1L, config.getAccountWsPingSeconds());
        pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hibachi-account-ping");
            t.setDaemon(true);
            return t;
        });
        pingTask = pingScheduler.scheduleAtFixedRate(() -> {
            try {
                if (client != null && client.isOpen() && listenKey != null) {
                    long id = HibachiTradeEnvelope.nextCorrelationId();
                    client.send(HibachiAccountStreamMessages.buildStreamPing(id, accountId, listenKey));
                }
            } catch (Exception e) {
                logger.warn("Hibachi account stream ping failed", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    protected void stopPing() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        if (pingScheduler != null) {
            pingScheduler.shutdownNow();
            pingScheduler = null;
        }
    }

    protected void scheduleReconnect() {
        if (shutdown) {
            return;
        }
        synchronized (this) {
            if (reconnectTask != null && !reconnectTask.isDone()) {
                return;
            }
            if (reconnectScheduler == null) {
                reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "hibachi-account-reconnect");
                    t.setDaemon(true);
                    return t;
                });
            }
            long delay = reconnectBackoffMs;
            long max = Math.max(delay, config.getWsReconnectMaxBackoffMs());
            reconnectBackoffMs = Math.min(max, Math.max(delay * 2L, delay + 250L));
            logger.info("Scheduling Hibachi account WS reconnect in {}ms", delay);
            reconnectTask = reconnectScheduler.schedule(this::tryReconnect, delay, TimeUnit.MILLISECONDS);
        }
    }

    protected void tryReconnect() {
        if (shutdown) {
            return;
        }
        try {
            synchronized (this) {
                if (isConnected()) {
                    return;
                }
                doConnect();
                logger.info("Hibachi account WS reconnected");
            }
        } catch (Exception e) {
            logger.warn("Hibachi account WS reconnect failed; rescheduling", e);
            scheduleReconnect();
        }
    }

    protected synchronized void cancelReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            reconnectScheduler = null;
        }
    }

    protected void onMessage(JsonNode message) {
        if (message == null) {
            return;
        }
        logger.info("Hibachi account WS raw <- {}", message);
        HibachiAccountEventListener l = eventListener;

        JsonNode result = message.path("result");
        if (result.has("listenKey")) {
            listenKey = result.path("listenKey").asText(null);
            listenKeyHolder.set(System.currentTimeMillis());
            logger.info("Hibachi account stream started; listenKey={}", listenKey);
        }
        if (l != null && result.has("accountSnapshot")) {
            safeDispatch(() -> l.onAccountSnapshot(result.path("accountSnapshot")));
        }

        if (l == null) {
            return;
        }
        // Hibachi push frames use either {"event":"...","data":{...}} (e.g. order_request_rejected)
        // or {"topic":"...","params":{...}}. Handle the event family first since rejections only
        // arrive on this channel.
        String event = message.path("event").asText(null);
        if (event != null) {
            String e = event.toLowerCase();
            JsonNode data = message.path("data");
            if (e.contains("reject")) {
                String orderId = data.path("orderId").asText(null);
                String reason = describeRejection(data.path("error"));
                safeDispatch(() -> l.onOrderRejected(orderId, reason, message));
                return;
            }
            if (e.contains("fill") || e.contains("trade")) {
                safeDispatch(() -> l.onFill(message));
                return;
            }
            if (e.contains("position")) {
                safeDispatch(() -> l.onPositionUpdate(message));
                return;
            }
            if (e.contains("balance") || e.contains("equity") || e.contains("account")) {
                safeDispatch(() -> l.onBalanceUpdate(message));
                return;
            }
            if (e.contains("order")) {
                safeDispatch(() -> l.onOrderUpdate(message));
                return;
            }
            final String eventFinal = event;
            safeDispatch(() -> l.onUnknownFrame(eventFinal, message));
            logger.info("Hibachi account WS unknown event={} frame={}", event, message);
            return;
        }
        String topic = message.path("topic").asText(null);
        if (topic == null && message.path("params").has("topic")) {
            topic = message.path("params").path("topic").asText(null);
        }
        if (topic == null) {
            return;
        }
        String t = topic.toLowerCase();
        if (t.contains("fill") || t.contains("trade")) {
            safeDispatch(() -> l.onFill(message));
        } else if (t.contains("order")) {
            safeDispatch(() -> l.onOrderUpdate(message));
        } else if (t.contains("position")) {
            safeDispatch(() -> l.onPositionUpdate(message));
        } else if (t.contains("balance") || t.contains("equity") || t.contains("account")) {
            safeDispatch(() -> l.onBalanceUpdate(message));
        } else {
            final String topicFinal = topic;
            safeDispatch(() -> l.onUnknownFrame(topicFinal, message));
            logger.info("Hibachi account WS unknown topic={} frame={}", topic, message);
        }
    }

    private void safeDispatch(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            logger.warn("Hibachi account event listener threw", e);
        }
    }

    /**
     * Hibachi rejection errors look like {@code {"TooSmallNotionalValue":{"notional":...}}}
     * or sometimes a plain string. Pull the discriminator key (or the string) so callers can
     * surface a human-readable reason without re-parsing.
     */
    private static String describeRejection(JsonNode error) {
        if (error == null || error.isMissingNode() || error.isNull()) {
            return null;
        }
        if (error.isTextual()) {
            return error.asText();
        }
        if (error.isObject() && error.fieldNames().hasNext()) {
            String key = error.fieldNames().next();
            JsonNode payload = error.path(key);
            if (payload.isMissingNode() || payload.isNull() || payload.size() == 0) {
                return key;
            }
            return key + ": " + payload.toString();
        }
        return error.toString();
    }

    protected void onClosed() {
        stopPing();
        listenKey = null;
        notifyState(false);
        if (!shutdown) {
            scheduleReconnect();
        }
    }

    protected void notifyState(boolean connected) {
        Consumer<Boolean> listener = connectionStateListener;
        if (listener == null) {
            return;
        }
        try {
            listener.accept(connected);
        } catch (Exception e) {
            logger.warn("Hibachi account WS connection-state listener failed", e);
        }
    }
}
