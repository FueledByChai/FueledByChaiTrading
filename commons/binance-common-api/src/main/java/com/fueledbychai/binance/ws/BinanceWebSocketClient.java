package com.fueledbychai.binance.ws;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

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
    }

    @Override
    public void onError(Exception ex) {
        super.onError(ex);
    }

}