package com.fueledbychai.binance.ws;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.java_websocket.handshake.ServerHandshake;

import com.fueledbychai.websocket.AbstractWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class BinanceWebSocketClient extends AbstractWebSocketClient {

    protected Map<String, String> params = null;

    private ScheduledExecutorService pingScheduler;
    private ScheduledFuture<?> pingTask;
    private static final long PING_INTERVAL_SECONDS = 30;
    private static int idCounter = 1;

    public BinanceWebSocketClient(String serverUri, IWebSocketProcessor processor) throws Exception {
        this(serverUri, "", processor);
    }

    public BinanceWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        super(serverUri, channel, processor);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("Connected to Binance WebSocket");
        startPingTask();

        if (channel != null && !channel.isEmpty()) {
            subscribeToChannel();
        }

    }

    protected void subscribeToChannel() {
        JsonObject subscribeJson = new JsonObject();
        subscribeJson.addProperty("method", "SUBSCRIBE");
        JsonArray paramsArray = new JsonArray();
        paramsArray.add(channel);
        subscribeJson.add("params", paramsArray);
        subscribeJson.addProperty("id", idCounter++);

        logger.info("Subscribing to channel: " + subscribeJson.toString());

        send(subscribeJson.toString());
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

    // Start a scheduled task to send a lightweight ping message every 30 seconds.
    // We use a text heartbeat here (JSON) to be compatible with servers that
    // don't rely on WebSocket-level ping frames. If you'd prefer a ping frame
    // instead, we can switch to using the library's sendPing API.

    protected void startPingTask() {
        pingScheduler = Executors.newSingleThreadScheduledExecutor(r ->

        {
            Thread t = new Thread(r, "binance-ping-thread");
            t.setDaemon(true);
            return t;
        });
        pingTask = pingScheduler.scheduleAtFixedRate(() -> {

            try {
                if (isOpen()) {
                    // Send a small JSON heartbeat. Adjust payload if the server expects a different
                    // format.
                    logger.info(channel + ": Sending ping");
                    send("{\"method\":\"ping\"}");
                } else {
                    // Connection closed, cancel the scheduled task to avoid unnecessary work.
                    if (pingTask != null && !pingTask.isCancelled()) {
                        pingTask.cancel(false);
                    }
                }
            } catch (Exception e) {
                logger.error("Error sending ping", e);
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopPingScheduler() {
        try {
            if (pingTask != null && !pingTask.isCancelled()) {
                pingTask.cancel(false);
            }
            if (pingScheduler != null && !pingScheduler.isShutdown()) {
                pingScheduler.shutdownNow();
            }
        } catch (Exception e) {
            logger.warn("Error shutting down ping scheduler", e);
        } finally {
            pingTask = null;
            pingScheduler = null;
        }
    }

}