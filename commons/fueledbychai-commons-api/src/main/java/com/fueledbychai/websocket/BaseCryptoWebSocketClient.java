package com.fueledbychai.websocket;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.java_websocket.handshake.ServerHandshake;

public abstract class BaseCryptoWebSocketClient extends AbstractWebSocketClient {

    private ScheduledExecutorService pingScheduler;
    private ScheduledFuture<?> pingTask;

    protected BaseCryptoWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        super(serverUri, channel, processor);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("Connected to {} WebSocket", getProviderName());

        String authMessage = buildAuthMessage();
        if (authMessage != null && !authMessage.isBlank()) {
            logger.info("Authenticating with {} WebSocket", getProviderName());
            send(authMessage);
            long delayMillis = getAuthSubscribeDelayMillis();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        String subscribeMessage = buildSubscribeMessage();
        if (subscribeMessage != null && !subscribeMessage.isBlank()) {
            logger.info("Subscribing to channel: {}", subscribeMessage);
            send(subscribeMessage);
        }

        startPingTask();
    }

    protected String getProviderName() {
        return "Crypto";
    }

    protected String buildAuthMessage() {
        return null;
    }

    protected String buildSubscribeMessage() {
        return null;
    }

    protected String buildPingMessage() {
        return null;
    }

    protected long getPingIntervalSeconds() {
        return 0;
    }

    protected long getAuthSubscribeDelayMillis() {
        return 0;
    }

    protected void startPingTask() {
        long pingIntervalSeconds = getPingIntervalSeconds();
        String pingMessage = buildPingMessage();
        if (pingIntervalSeconds <= 0 || pingMessage == null || pingMessage.isBlank()) {
            return;
        }

        pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, getProviderName().toLowerCase() + "-ping-thread");
            t.setDaemon(true);
            return t;
        });

        pingTask = pingScheduler.scheduleAtFixedRate(() -> {
            try {
                if (isOpen()) {
                    logger.info("Sending ping for {}", getProviderName());
                    send(pingMessage);
                } else if (pingTask != null && !pingTask.isCancelled()) {
                    pingTask.cancel(false);
                }
            } catch (Exception e) {
                logger.error("Error sending ping", e);
            }
        }, pingIntervalSeconds, pingIntervalSeconds, TimeUnit.SECONDS);
    }

    protected void stopPingScheduler() {
        if (pingTask != null && !pingTask.isCancelled()) {
            pingTask.cancel(false);
        }
        if (pingScheduler != null && !pingScheduler.isShutdown()) {
            pingScheduler.shutdownNow();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        super.onClose(code, reason, remote);
        stopPingScheduler();
    }

    @Override
    public void onError(Exception ex) {
        super.onError(ex);
        stopPingScheduler();
    }
}
