package com.fueledbychai.lighter.common.api.ws.client;

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

    @Test
    void lighterDisablesBuiltInLostConnectionDetection() throws Exception {
        TestableLighterWebSocketClient client = new TestableLighterWebSocketClient("wss://example.test/stream",
                "trade/1", null);
        assertEquals(0, client.getConnectionLostTimeout());
    }

    @Test
    void lighterKeepaliveUsesNativePingFrames() throws Exception {
        TestableLighterWebSocketClient client = new TestableLighterWebSocketClient("wss://example.test/stream",
                "trade/1", null);
        client.sendKeepaliveFrameForTest();
        assertEquals(1, client.getPingCount());
    }

    private static class TestableLighterWebSocketClient extends LighterWebSocketClient {
        private int pingCount;

        TestableLighterWebSocketClient(String serverUri, String channel, String subscribeAuth) throws Exception {
            super(serverUri, channel, subscribeAuth, new NoopWebSocketProcessor());
        }

        String buildSubscribeMessageForTest() {
            return super.buildSubscribeMessage();
        }

        void sendKeepaliveFrameForTest() {
            super.sendKeepaliveFrame();
        }

        int getPingCount() {
            return pingCount;
        }

        @Override
        public void sendPing() {
            pingCount++;
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
