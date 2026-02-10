package com.fueledbychai.lighter.common.api.ws;

import org.json.JSONObject;

import com.fueledbychai.websocket.BaseCryptoWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWebSocketClient extends BaseCryptoWebSocketClient {

    public LighterWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        super(serverUri, channel, processor);
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
        return subscribeJson.toString();
    }
}

