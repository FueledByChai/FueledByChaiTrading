package com.fueledbychai.binancefutures.common.api.ws;

import org.java_websocket.handshake.ServerHandshake;

import com.fueledbychai.websocket.AbstractWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class BinanceFuturesWebSocketClient extends AbstractWebSocketClient {

    private static int idCounter = 1;

    public BinanceFuturesWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        super(serverUri, channel, processor);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        processor.connectionOpened();
        if (channel != null && !channel.isBlank()) {
            JsonObject subscribeJson = new JsonObject();
            subscribeJson.addProperty("method", "SUBSCRIBE");
            JsonArray paramsArray = new JsonArray();
            paramsArray.add(channel);
            subscribeJson.add("params", paramsArray);
            subscribeJson.addProperty("id", idCounter++);
            send(subscribeJson.toString());
        }
    }

    @Override
    public void onError(Exception ex) {
        super.onError(ex);
        if (processor != null) {
            processor.connectionError(ex);
        }
    }
}
