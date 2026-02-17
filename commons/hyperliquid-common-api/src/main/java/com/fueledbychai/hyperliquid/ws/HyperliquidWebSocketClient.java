package com.fueledbychai.hyperliquid.ws;

import java.util.Map;
import com.google.gson.JsonObject;
import com.fueledbychai.websocket.BaseCryptoWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class HyperliquidWebSocketClient extends BaseCryptoWebSocketClient {

    protected Map<String, String> params = null;

    public HyperliquidWebSocketClient(String serverUri, IWebSocketProcessor processor) throws Exception {
        this(serverUri, "", processor);
    }

    public HyperliquidWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor)
            throws Exception {
        super(serverUri, channel, processor);
    }

    public HyperliquidWebSocketClient(String serverUri, String channel, Map<String, String> params,
            IWebSocketProcessor processor) throws Exception {
        super(serverUri, channel, processor);
        this.params = params;
    }

    public HyperliquidWebSocketClient(String serverUri, String channel, Map<String, String> params,
            IWebSocketProcessor processor, String jwtToken) throws Exception {
        this(serverUri, channel, params, processor);
    }

    @Override
    protected String getProviderName() {
        return "Hyperliquid";
    }

    @Override
    protected String buildSubscribeMessage() {
        if (channel == null || channel.isEmpty()) {
            return null;
        }
        JsonObject subscribeJson = new JsonObject();
        subscribeJson.addProperty("jsonrpc", "2.0");
        subscribeJson.addProperty("method", "subscribe");
        JsonObject subscription = new JsonObject();
        subscription.addProperty("type", channel);
        if (params != null) {
            for (String key : params.keySet()) {
                subscription.addProperty(key, params.get(key));
            }
        }

        subscribeJson.add("subscription", subscription);

        return subscribeJson.toString();
    }

    public void postMessage(String message) {
        logger.info("WS: " + super.getURI().getHost() + " Sending POST message: " + message);
        send(message);
    }

    @Override
    protected long getPingIntervalSeconds() {
        return 30;
    }

    @Override
    protected String buildPingMessage() {
        return "{\"method\":\"ping\"}";
    }

}
