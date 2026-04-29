package com.fueledbychai.broker.hibachi;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    protected final AtomicLong listenKeyHolder = new AtomicLong();
    protected volatile String listenKey;

    public HibachiAccountStreamClient(HibachiConfiguration config, long accountId, String apiKey) {
        this.config = config;
        this.accountId = accountId;
        this.apiKey = apiKey;
    }

    public synchronized void connect() {
        if (client != null && client.isOpen()) {
            return;
        }
        try {
            processor = new HibachiJsonProcessor(this::onClosed);
            processor.addEventListener(this::onMessage);
            client = HibachiWebSocketClient.createPrivate(
                    config.getAccountWsUrl(), String.valueOf(accountId), processor, apiKey, config.getClient());
            if (!client.connectBlocking(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Hibachi account WS handshake timed out");
            }
            sendStreamStart();
            startPing();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open Hibachi account WS", e);
        }
    }

    public synchronized void disconnect() {
        stopPing();
        HibachiWebSocketClient c = client;
        client = null;
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
        if (processor != null) {
            processor.shutdown();
            processor = null;
        }
        listenKey = null;
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public String getListenKey() {
        return listenKey;
    }

    protected void sendStreamStart() {
        long id = HibachiTradeEnvelope.nextCorrelationId();
        client.send(HibachiAccountStreamMessages.buildStreamStart(id, accountId));
    }

    protected void startPing() {
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

    protected void onMessage(JsonNode message) {
        if (message == null) {
            return;
        }
        // Capture listenKey from stream.start response.
        JsonNode result = message.path("result");
        if (result.has("listenKey")) {
            listenKey = result.path("listenKey").asText(null);
            listenKeyHolder.set(System.currentTimeMillis());
            logger.info("Hibachi account stream started; listenKey={}", listenKey);
        }
    }

    protected void onClosed() {
        stopPing();
    }
}
