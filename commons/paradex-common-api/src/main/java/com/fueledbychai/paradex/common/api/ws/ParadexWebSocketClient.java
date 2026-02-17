package com.fueledbychai.paradex.common.api.ws;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.fueledbychai.websocket.BaseCryptoWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class ParadexWebSocketClient extends BaseCryptoWebSocketClient {

    protected String jwtToken = null;

    public ParadexWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        super(serverUri, channel, processor);
    }

    public ParadexWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor, String jwtToken)
            throws Exception {
        this(serverUri, channel, processor);
        this.jwtToken = jwtToken;
    }

    @Override
    protected String getProviderName() {
        return "Paradex";
    }

    @Override
    protected String buildAuthMessage() {
        if (jwtToken == null) {
            return null;
        }
        JsonObject authJson = new JsonObject();
        authJson.addProperty("jsonrpc", "2.0");
        authJson.addProperty("method", "auth");
        JsonObject params = new JsonObject();
        params.addProperty("bearer", jwtToken);
        authJson.add("params", params);
        authJson.addProperty("id", 0);
        return authJson.toString();
    }

    @Override
    protected String buildSubscribeMessage() {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        JsonObject subscribeJson = new JsonObject();
        subscribeJson.addProperty("jsonrpc", "2.0");
        subscribeJson.addProperty("method", "subscribe");
        JsonElement params = new JsonObject();
        params.getAsJsonObject().addProperty("channel", channel);
        subscribeJson.add("params", params);
        subscribeJson.addProperty("id", 1);
        return subscribeJson.toString();
    }

    @Override
    protected long getAuthSubscribeDelayMillis() {
        return jwtToken == null ? 0 : 500;
    }

}
