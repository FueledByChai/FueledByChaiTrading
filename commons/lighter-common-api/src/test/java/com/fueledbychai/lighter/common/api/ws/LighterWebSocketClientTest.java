package com.fueledbychai.lighter.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWebSocketClientTest {

    @Test
    void subscribeMessageIncludesAuthWhenProvided() throws Exception {
        TestableLighterWebSocketClient client = new TestableLighterWebSocketClient("wss://example.test/stream",
                "account_all_trades/255", "auth-token");
        JSONObject subscribe = new JSONObject(client.buildSubscribeMessageForTest());
        assertEquals("subscribe", subscribe.getString("type"));
        assertEquals("account_all_trades/255", subscribe.getString("channel"));
        assertEquals("auth-token", subscribe.getString("auth"));
    }

    @Test
    void subscribeMessageOmitsAuthWhenNotProvided() throws Exception {
        TestableLighterWebSocketClient client = new TestableLighterWebSocketClient("wss://example.test/stream",
                "trade/1", null);
        JSONObject subscribe = new JSONObject(client.buildSubscribeMessageForTest());
        assertEquals("subscribe", subscribe.getString("type"));
        assertEquals("trade/1", subscribe.getString("channel"));
        assertNull(subscribe.opt("auth"));
    }

    private static class TestableLighterWebSocketClient extends LighterWebSocketClient {

        TestableLighterWebSocketClient(String serverUri, String channel, String subscribeAuth) throws Exception {
            super(serverUri, channel, subscribeAuth, new NoopWebSocketProcessor());
        }

        String buildSubscribeMessageForTest() {
            return super.buildSubscribeMessage();
        }
    }

    private static class NoopWebSocketProcessor implements IWebSocketProcessor {

        @Override
        public void messageReceived(String message) {
        }

        @Override
        public void connectionClosed(int code, String reason, boolean remote) {
        }

        @Override
        public void connectionOpened() {
        }

        @Override
        public void connectionError(Exception error) {
        }

        @Override
        public void connectionEstablished() {
        }
    }
}
