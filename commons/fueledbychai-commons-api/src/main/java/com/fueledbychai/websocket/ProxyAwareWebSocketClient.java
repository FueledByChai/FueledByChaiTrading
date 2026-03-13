package com.fueledbychai.websocket;

import java.net.Proxy;
import java.net.URI;

import org.java_websocket.client.WebSocketClient;

public abstract class ProxyAwareWebSocketClient extends WebSocketClient {

    protected ProxyAwareWebSocketClient(URI serverUri) {
        super(serverUri);
        Proxy proxy = ProxyConfig.getInstance().getProxy();
        if (proxy != null && proxy != Proxy.NO_PROXY) {
            setProxy(proxy);
        }
    }
}
