package com.fueledbychai.qfex.common.api.ws;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.websocket.IWebSocketProcessor;

/**
 * A QFEX socket that keeps itself connected: exponential-backoff reconnects
 * after any close, plus a watchdog that recycles a socket that has gone
 * silent (a half-open TCP connection otherwise looks healthy forever).
 */
public abstract class QfexStream implements IWebSocketProcessor {

    protected static final Logger logger = LoggerFactory.getLogger(QfexStream.class);
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);

    private static final long INITIAL_BACKOFF_MS = 500;
    private static final long MAX_BACKOFF_MS = 30_000;

    protected final String name;
    protected final ScheduledExecutorService scheduler;
    private final Duration staleAfter;

    private volatile QfexWebSocketClient client;
    private volatile boolean running;
    private volatile boolean open;
    private volatile long lastMessageAt;
    private int reconnectAttempts;
    private ScheduledFuture<?> watchdog;

    protected QfexStream(String name, Duration staleAfter) {
        this.name = name;
        this.staleAfter = staleAfter;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qfex-" + name);
            t.setDaemon(true);
            return t;
        });
    }

    /** The URL for the next connection attempt. */
    protected abstract String connectUrl();

    /** The socket opened; send auth/subscriptions here. */
    protected abstract void onOpened();

    /** The socket closed (a reconnect will follow while running). */
    protected abstract void onClosed();

    protected abstract void onMessage(JsonNode message);

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        connectNow();
        watchdog = scheduler.scheduleWithFixedDelay(this::checkStale, 15, 15, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        running = false;
        if (watchdog != null) {
            watchdog.cancel(false);
        }
        QfexWebSocketClient c = client;
        client = null;
        if (c != null) {
            c.close();
        }
        open = false;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isOpen() {
        return open;
    }

    /** Sends if the socket is open; returns false otherwise. */
    public boolean send(JsonNode message) {
        QfexWebSocketClient c = client;
        if (c == null || !open) {
            return false;
        }
        try {
            c.send(MAPPER.writeValueAsString(message));
            return true;
        } catch (Exception e) {
            logger.warn("QFEX [{}] send failed: {}", name, e.getMessage());
            return false;
        }
    }

    private synchronized void connectNow() {
        if (!running) {
            return;
        }
        try {
            QfexWebSocketClient c = new QfexWebSocketClient(connectUrl(), name, this);
            client = c;
            lastMessageAt = System.currentTimeMillis();
            c.connect();
        } catch (Exception e) {
            logger.warn("QFEX [{}] connect failed: {}", name, e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        long delay = Math.min(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS << Math.min(reconnectAttempts, 6));
        reconnectAttempts++;
        logger.info("QFEX [{}] reconnecting in {} ms (attempt {})", name, delay, reconnectAttempts);
        scheduler.schedule(this::connectNow, delay, TimeUnit.MILLISECONDS);
    }

    private void checkStale() {
        QfexWebSocketClient c = client;
        if (running && open && c != null
                && System.currentTimeMillis() - lastMessageAt > staleAfter.toMillis()) {
            logger.warn("QFEX [{}] no messages for {}s; recycling socket", name, staleAfter.toSeconds());
            c.close();
        }
    }

    // ---- IWebSocketProcessor (called on the socket's thread) ----

    @Override
    public void connectionOpened() {
        open = true;
        reconnectAttempts = 0;
        lastMessageAt = System.currentTimeMillis();
        try {
            onOpened();
        } catch (RuntimeException e) {
            logger.error("QFEX [{}] onOpened failed", name, e);
        }
    }

    @Override
    public void connectionEstablished() {
    }

    @Override
    public void connectionClosed(int code, String reason, boolean remote) {
        open = false;
        logger.info("QFEX [{}] closed code={} remote={} reason={}", name, code, remote, reason);
        try {
            onClosed();
        } catch (RuntimeException e) {
            logger.error("QFEX [{}] onClosed failed", name, e);
        }
        scheduleReconnect();
    }

    @Override
    public void connectionError(Exception error) {
        // onClose follows an error; reconnection is handled there.
    }

    @Override
    public void messageReceived(String message) {
        lastMessageAt = System.currentTimeMillis();
        JsonNode node;
        try {
            node = MAPPER.readTree(message);
        } catch (Exception e) {
            logger.warn("QFEX [{}] unparseable message: {}", name, message);
            return;
        }
        try {
            onMessage(node);
        } catch (RuntimeException e) {
            logger.error("QFEX [{}] message handler failed for {}", name, message, e);
        }
    }
}
