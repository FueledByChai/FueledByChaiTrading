package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.fueledbychai.lighter.common.api.ws.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.ILighterOrderBookListener;
import com.fueledbychai.lighter.common.api.ws.ILighterTradeListener;
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
    void subscribeOrderBookReusesConnectionForSameMarket() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterOrderBookListener listener = update -> {
        };

        LighterWebSocketClient client1 = api.subscribeOrderBook(53, listener);
        LighterWebSocketClient client2 = api.subscribeOrderBook(53, listener);

        assertNotNull(client1);
        assertNotNull(client2);
        assertEquals(client1, client2);
        assertEquals(1, api.createdClients.size());
        assertEquals(1, api.connectCountByChannel.get("order_book/53").intValue());
    }

    @Test
    void subscribeTradesReusesConnectionForSameMarket() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterTradeListener listener = update -> {
        };

        LighterWebSocketClient client1 = api.subscribeTrades(53, listener);
        LighterWebSocketClient client2 = api.subscribeTrades(53, listener);

        assertNotNull(client1);
        assertNotNull(client2);
        assertEquals(client1, client2);
        assertEquals(1, api.createdClients.size());
        assertEquals(1, api.connectCountByChannel.get("trade/53").intValue());
    }

    @Test
    void disconnectAllClosesAllConnections() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterMarketStatsListener listener = update -> {
        };
        ILighterOrderBookListener orderBookListener = update -> {
        };
        ILighterTradeListener tradeListener = update -> {
        };

        api.subscribeMarketStats(53, listener);
        api.subscribeAllMarketStats(listener);
        api.subscribeOrderBook(53, orderBookListener);
        api.subscribeTrades(53, tradeListener);
        api.disconnectAll();

        assertEquals(1, api.closeCountByChannel.get("market_stats/53").intValue());
        assertEquals(1, api.closeCountByChannel.get("market_stats/all").intValue());
        assertEquals(1, api.closeCountByChannel.get("order_book/53").intValue());
        assertEquals(1, api.closeCountByChannel.get("trade/53").intValue());
    }

    @Test
    void rejectNegativeMarketId() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterMarketStatsListener listener = update -> {
        };
        assertThrows(IllegalArgumentException.class, () -> api.subscribeMarketStats(-1, listener));
    }

    @Test
    void rejectNegativeOrderBookMarketId() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterOrderBookListener listener = update -> {
        };
        assertThrows(IllegalArgumentException.class, () -> api.subscribeOrderBook(-1, listener));
    }

    @Test
    void rejectNegativeTradeMarketId() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterTradeListener listener = update -> {
        };
        assertThrows(IllegalArgumentException.class, () -> api.subscribeTrades(-1, listener));
    }

    @Test
    void autoReconnectsAfterRemoteClose() throws Exception {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterMarketStatsListener listener = update -> {
        };

        api.subscribeMarketStats(53, listener);
        TestClient firstClient = api.createdClients.get("market_stats/53");
        assertNotNull(firstClient);

        firstClient.simulateRemoteClose();

        long deadlineMillis = System.currentTimeMillis() + 1_000L;
        int connectCount = 0;
        while (System.currentTimeMillis() < deadlineMillis) {
            Integer count = api.connectCountByChannel.get("market_stats/53");
            connectCount = count == null ? 0 : count;
            if (connectCount >= 2) {
                break;
            }
            Thread.sleep(20L);
        }

        assertTrue(connectCount >= 2, "Expected reconnect to be attempted");
        api.disconnectAll();
    }

    @Test
    void autoReconnectsAfterError() throws Exception {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        ILighterMarketStatsListener listener = update -> {
        };

        api.subscribeMarketStats(53, listener);
        TestClient firstClient = api.createdClients.get("market_stats/53");
        assertNotNull(firstClient);

        firstClient.simulateError(new RuntimeException("test websocket error"));

        long deadlineMillis = System.currentTimeMillis() + 1_000L;
        int connectCount = 0;
        while (System.currentTimeMillis() < deadlineMillis) {
            Integer count = api.connectCountByChannel.get("market_stats/53");
            connectCount = count == null ? 0 : count;
            if (connectCount >= 2) {
                break;
            }
            Thread.sleep(20L);
        }

        assertTrue(connectCount >= 2, "Expected reconnect to be attempted after error");
        api.disconnectAll();
    }

    private static class TestableLighterWebSocketApi extends LighterWebSocketApi {
        private final Map<String, TestClient> createdClients = new ConcurrentHashMap<>();
        private final Map<String, Integer> connectCountByChannel = new ConcurrentHashMap<>();
        private final Map<String, Integer> closeCountByChannel = new ConcurrentHashMap<>();

        TestableLighterWebSocketApi(String url) {
            super(url);
        }

        @Override
        protected long getReconnectDelayMillis() {
            return 10L;
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
            processor.connectionClosed(1000, "manual close", false);
        }

        void simulateRemoteClose() {
            processor.connectionClosed(1006, "remote close", true);
        }

        void simulateError(Exception error) {
            onError(error);
        }
    }
}
