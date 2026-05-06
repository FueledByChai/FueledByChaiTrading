package com.fueledbychai.websocket;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.fueledbychai.diagnostics.SecretRedactor;
import com.fueledbychai.diagnostics.WireTap;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWebSocketClient extends WebSocketClient {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractWebSocketClient.class);
    protected IWebSocketProcessor processor;
    protected List<String> messages = new ArrayList<>();
    protected String channel;
    private final String serverUriString;

    public AbstractWebSocketClient(String serverUri, String channel, IWebSocketProcessor processor) throws Exception {
        super(new URI(serverUri));
        setProxy(ProxyConfig.getInstance().getProxy());
        this.processor = processor;
        this.channel = channel;
        this.serverUriString = serverUri;
    }

    @Override
    public void onMessage(String message) {
        if (WireTap.isEnabled()) {
            WireTap.publishWs(new WireTap.WsEvent(
                    System.currentTimeMillis(),
                    WireTap.Direction.IN,
                    exchangeFromHost(),
                    channel,
                    serverUriString,
                    SecretRedactor.redactBody(message)));
        }
        processor.messageReceived(message);
    }

    @Override
    public void send(String text) {
        if (WireTap.isEnabled()) {
            WireTap.publishWs(new WireTap.WsEvent(
                    System.currentTimeMillis(),
                    WireTap.Direction.OUT,
                    exchangeFromHost(),
                    channel,
                    serverUriString,
                    SecretRedactor.redactBody(text)));
        }
        super.send(text);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        processor.connectionClosed(code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        logger.error(ex.getMessage(), ex);
    }

    private String exchangeFromHost() {
        try {
            String host = getURI() != null && getURI().getHost() != null ? getURI().getHost().toLowerCase() : "";
            if (host.contains("hyperliquid")) return "HYPERLIQUID";
            if (host.contains("paradex")) return "PARADEX";
            if (host.contains("lighter")) return "LIGHTER";
            if (host.contains("hibachi")) return "HIBACHI";
            if (host.contains("aster")) return "ASTER";
            if (host.contains("bybit")) return "BYBIT";
            if (host.contains("okx") || host.contains("okex")) return "OKX";
            if (host.contains("drift")) return "DRIFT";
            if (host.contains("deribit")) return "DERIBIT";
            if (host.contains("binance")) return "BINANCE";
            if (host.contains("dydx")) return "DYDX";
            return host.isEmpty() ? "unknown" : host;
        } catch (Exception e) {
            return "unknown";
        }
    }

}
