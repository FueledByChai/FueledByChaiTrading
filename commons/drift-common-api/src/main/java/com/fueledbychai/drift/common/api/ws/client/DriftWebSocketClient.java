package com.fueledbychai.drift.common.api.ws.client;

import org.java_websocket.handshake.ServerHandshake;

import com.fueledbychai.websocket.BaseCryptoWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class DriftWebSocketClient extends BaseCryptoWebSocketClient {

    private final String subscribeMessage;

    public DriftWebSocketClient(String serverUri, String subscribeMessage, IWebSocketProcessor processor)
            throws Exception {
        super(serverUri, null, processor);
        this.subscribeMessage = subscribeMessage;
    }

    @Override
    protected String getProviderName() {
        return "Drift";
    }

    @Override
    protected String buildSubscribeMessage() {
        return subscribeMessage;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        super.onOpen(handshakedata);
        processor.connectionEstablished();
        processor.connectionOpened();
    }

    @Override
    public void onError(Exception ex) {
        super.onError(ex);
        processor.connectionError(ex);
    }
}
