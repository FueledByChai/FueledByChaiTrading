package com.fueledbychai.qfex.common.api.ws;

import org.java_websocket.handshake.ServerHandshake;

import com.fueledbychai.websocket.AbstractWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

/** One physical QFEX socket. Reconnection is handled by {@link QfexStream}, which builds a new client each time. */
public class QfexWebSocketClient extends AbstractWebSocketClient {

    public QfexWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        super(serverUri, channel, processor);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("QFEX WS [{}] open status={}", channel, handshakedata.getHttpStatus());
        processor.connectionOpened();
    }

    @Override
    public void onError(Exception ex) {
        logger.warn("QFEX WS [{}] error: {}", channel, ex.getMessage());
        processor.connectionError(ex);
    }
}
