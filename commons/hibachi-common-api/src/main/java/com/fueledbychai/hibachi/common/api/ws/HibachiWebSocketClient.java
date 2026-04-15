package com.fueledbychai.hibachi.common.api.ws;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.java_websocket.handshake.ServerHandshake;

import com.fueledbychai.websocket.AbstractWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

/**
 * WebSocket client for Hibachi market / account / trade streams.
 *
 * <p>Private streams (account + trade) require an {@code Authorization: <apiKey>} handshake
 * header (raw value, not "Bearer") and an {@code accountId} URL query parameter. Market
 * stream is anonymous.
 *
 * <p>If a non-blank {@code subscribeMessage} is supplied, it is sent immediately after
 * connection open.
 */
public class HibachiWebSocketClient extends AbstractWebSocketClient {

    protected final String subscribeMessage;

    public HibachiWebSocketClient(String serverUri,
                                  IWebSocketProcessor processor,
                                  String authApiKey,
                                  String hibachiClient,
                                  String subscribeMessage) throws Exception {
        super(buildUri(serverUri, hibachiClient).toString(), null, processor);
        this.subscribeMessage = subscribeMessage;
        Map<String, String> headers = new HashMap<>();
        if (authApiKey != null && !authApiKey.isBlank()) {
            headers.put("Authorization", authApiKey);
        }
        if (hibachiClient != null && !hibachiClient.isBlank()) {
            headers.put("Hibachi-Client", hibachiClient);
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            super.addHeader(e.getKey(), e.getValue());
        }
    }

    public static HibachiWebSocketClient createMarket(String serverUri,
                                                      IWebSocketProcessor processor,
                                                      String hibachiClient,
                                                      String subscribeMessage) throws Exception {
        return new HibachiWebSocketClient(serverUri, processor, null, hibachiClient, subscribeMessage);
    }

    public static HibachiWebSocketClient createPrivate(String serverUri,
                                                       String accountId,
                                                       IWebSocketProcessor processor,
                                                       String authApiKey,
                                                       String hibachiClient) throws Exception {
        String urlWithAccount = appendQueryParam(serverUri, "accountId", accountId);
        return new HibachiWebSocketClient(urlWithAccount, processor, authApiKey, hibachiClient, null);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        processor.connectionOpened();
        if (subscribeMessage != null && !subscribeMessage.isBlank()) {
            send(subscribeMessage);
        }
    }

    @Override
    public void onError(Exception ex) {
        super.onError(ex);
        if (processor != null) {
            processor.connectionError(ex);
        }
    }

    private static URI buildUri(String serverUri, String hibachiClient) {
        String url = appendQueryParam(serverUri, "hibachiClient", hibachiClient);
        try {
            return new URI(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Hibachi WebSocket URI: " + url, e);
        }
    }

    static String appendQueryParam(String url, String name, String value) {
        if (url == null || name == null || value == null || value.isBlank()) {
            return url;
        }
        char sep = url.contains("?") ? '&' : '?';
        return url + sep + name + "=" + value;
    }

}
