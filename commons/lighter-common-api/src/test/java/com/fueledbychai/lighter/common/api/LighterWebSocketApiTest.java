package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.ws.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.LighterWebSocketClient;
import com.fueledbychai.websocket.IWebSocketProcessor;

public class LighterWebSocketApiTest {

    @Test
    void subscribeReusesConnectionForSameMarket() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterMarketStatsListener listener = update -> {
        };

        LighterWebSocketClient client1 = api.subscribeMarketStats(53, listener);
        LighterWebSocketClient client2 = api.subscribeMarketStats(53, listener);

        assertNotNull(client1);
        assertNotNull(client2);
        assertEquals(client1, client2);
        assertEquals(1, api.createdClients.size());
        assertEquals(1, api.connectCountByChannel.get("market_stats/53").intValue());
    }

    @Test
    void subscribeAllUsesDedicatedChannel() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterMarketStatsListener listener = update -> {
        };

        api.subscribeMarketStats(53, listener);
        api.subscribeAllMarketStats(listener);

        assertEquals(2, api.createdClients.size());
        assertEquals(1, api.connectCountByChannel.get("market_stats/53").intValue());
        assertEquals(1, api.connectCountByChannel.get("market_stats/all").intValue());
    }

    @Test
    void disconnectAllClosesAllConnections() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterMarketStatsListener listener = update -> {
        };

        api.subscribeMarketStats(53, listener);
        api.subscribeAllMarketStats(listener);
        api.disconnectAll();

        assertEquals(1, api.closeCountByChannel.get("market_stats/53").intValue());
        assertEquals(1, api.closeCountByChannel.get("market_stats/all").intValue());
    }

    @Test
    void rejectNegativeMarketId() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterMarketStatsListener listener = update -> {
        };
        assertThrows(IllegalArgumentException.class, () -> api.subscribeMarketStats(-1, listener));
    }

    private static class TestableLighterWebSocketApi extends LighterWebSocketApi {
        private final Map<String, TestClient> createdClients = new ConcurrentHashMap<>();
        private final Map<String, Integer> connectCountByChannel = new ConcurrentHashMap<>();
        private final Map<String, Integer> closeCountByChannel = new ConcurrentHashMap<>();

        TestableLighterWebSocketApi(String url) {
            super(url);
        }

        @Override
        protected LighterWebSocketClient createClient(String channel, IWebSocketProcessor processor) {
            try {
                TestClient client = new TestClient("wss://example.test/stream", channel, processor,
                        connectCountByChannel, closeCountByChannel);
                createdClients.put(channel, client);
                return client;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static class TestClient extends LighterWebSocketClient {
        private final String channelKey;
        private final Map<String, Integer> connectCountByChannel;
        private final Map<String, Integer> closeCountByChannel;

        TestClient(String serverUri, String channel, IWebSocketProcessor processor,
                Map<String, Integer> connectCountByChannel, Map<String, Integer> closeCountByChannel) throws Exception {
            super(serverUri, channel, processor);
            this.channelKey = channel;
            this.connectCountByChannel = connectCountByChannel;
            this.closeCountByChannel = closeCountByChannel;
        }

        @Override
        public void connect() {
            connectCountByChannel.merge(channelKey, 1, Integer::sum);
        }

        @Override
        public void close() {
            closeCountByChannel.merge(channelKey, 1, Integer::sum);
        }
    }
}

