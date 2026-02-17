package com.fueledbychai.lighter.common.api.ws;

import org.json.JSONObject;

import com.fueledbychai.websocket.BaseCryptoWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWebSocketClient extends BaseCryptoWebSocketClient {

    private final String subscribeAuth;

    public LighterWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        this(serverUri, channel, null, processor);
    }

    public LighterWebSocketClient(String serverUri, String channel, String subscribeAuth, IWebSocketProcessor processor)
            throws Exception {
        super(serverUri, channel, processor);
        this.subscribeAuth = subscribeAuth;
    }

    @Override
    protected String getProviderName() {
        return "Lighter";
    }

    @Override
    protected String buildSubscribeMessage() {
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
