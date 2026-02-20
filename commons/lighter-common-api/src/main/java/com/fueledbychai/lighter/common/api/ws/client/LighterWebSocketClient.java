package com.fueledbychai.lighter.common.api.ws.client;

import org.json.JSONObject;
import org.java_websocket.handshake.ServerHandshake;

import com.fueledbychai.websocket.BaseCryptoWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWebSocketClient extends BaseCryptoWebSocketClient {

    private final String subscribeAuth;
    private final boolean autoSubscribeOnOpen;
    private final Runnable onConnectedListener;

    public LighterWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        this(serverUri, channel, null, processor, true, null);
    }

    public LighterWebSocketClient(String serverUri, String channel, String subscribeAuth, IWebSocketProcessor processor)
            throws Exception {
        this(serverUri, channel, subscribeAuth, processor, true, null);
    }

    public LighterWebSocketClient(String serverUri, String channel, String subscribeAuth, IWebSocketProcessor processor,
            boolean autoSubscribeOnOpen, Runnable onConnectedListener) throws Exception {
        super(serverUri, channel, processor);
        this.subscribeAuth = subscribeAuth;
        this.autoSubscribeOnOpen = autoSubscribeOnOpen;
        this.onConnectedListener = onConnectedListener;
    }

    @Override
    protected String getProviderName() {
        return "Lighter";
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        super.onOpen(handshakedata);
        onConnected();
    }

    @Override
    protected String buildSubscribeMessage() {
        if (!autoSubscribeOnOpen) {
            return null;
        }
        return buildSubscribeMessage(channel, subscribeAuth);
    }

    public static String buildSubscribeMessage(String channel, String subscribeAuth) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        JSONObject subscribeJson = new JSONObject();
        subscribeJson.put("type", "subscribe");
        subscribeJson.put("channel", channel);
        if (subscribeAuth != null && !subscribeAuth.isBlank()) {
            subscribeJson.put("auth", subscribeAuth);
        }
        return subscribeJson.toString();
    }

    protected void onConnected() {
        if (onConnectedListener == null) {
            return;
        }
        try {
            onConnectedListener.run();
        } catch (Exception e) {
            logger.warn("Error in Lighter websocket onConnected callback", e);
        }
    }

    public void postMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        logger.info("WS: {} Sending message: {}", super.getURI().getHost(), message);
        send(message);
    }

    @Override
    public void onError(Exception ex) {
        super.onError(ex);
        processor.connectionError(ex);
    }
}
