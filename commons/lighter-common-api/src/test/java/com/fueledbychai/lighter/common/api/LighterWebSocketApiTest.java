package com.fueledbychai.lighter.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import com.fueledbychai.lighter.common.api.ws.ILighterMarketStatsListener;
import com.fueledbychai.lighter.common.api.ws.ILighterOrderBookListener;
import com.fueledbychai.lighter.common.api.ws.ILighterTradeListener;
import com.fueledbychai.lighter.common.api.order.LighterCancelOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterModifyOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterOrderType;
import com.fueledbychai.lighter.common.api.order.LighterTimeInForce;
import com.fueledbychai.lighter.common.api.signer.ILighterTransactionSigner;
import com.fueledbychai.lighter.common.api.signer.LighterSignedTransaction;
import com.fueledbychai.lighter.common.api.ws.LighterSendTxResponse;
import com.fueledbychai.lighter.common.api.ws.LighterWebSocketClient;
import com.fueledbychai.lighter.common.api.ws.LighterSendTxWebSocketProcessor;
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

    @Test
    void sendSignedTransactionPostsJsonApiMessageAndReturnsAck() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream?readonly=true");
        JSONObject txInfo = new JSONObject();
        txInfo.put("market_index", 2);
        txInfo.put("nonce", 1700);

        LighterSendTxResponse response = api.sendSignedTransaction(10, txInfo);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(200, response.getCode());
        assertEquals("ok", response.getMessage());
        assertEquals(1, api.postedTxMessages.size());

        JSONObject posted = new JSONObject(api.postedTxMessages.get(0));
        assertEquals("jsonapi/sendtx", posted.getString("type"));
        assertEquals(10, posted.getJSONObject("data").getInt("tx_type"));
        assertEquals(2, posted.getJSONObject("data").getJSONObject("tx_info").getInt("market_index"));
    }

    @Test
    void sendSignedTransactionRejectsInvalidInput() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream");
        assertThrows(IllegalArgumentException.class, () -> api.sendSignedTransaction(0, new JSONObject()));
        assertThrows(IllegalArgumentException.class, () -> api.sendSignedTransaction(10, null));
    }

    @Test
    void sendSignedTransactionHandlesAckWithoutId() {
        LighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream") {
            @Override
            protected LighterWebSocketClient createSendTxClient(IWebSocketProcessor processor) {
                try {
                    return new LighterWebSocketClient("wss://example.test/stream", null, processor) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public void postMessage(String message) {
                            processor.messageReceived("{\"code\":200,\"msg\":\"ok\"}");
                        }
                    };
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            protected void waitForTxClientConnection(LighterWebSocketClient client, long timeoutMillis) {
                // no-op for test doubles
            }
        };

        JSONObject txInfo = new JSONObject();
        txInfo.put("market_index", 2);
        txInfo.put("nonce", 1700);

        LighterSendTxResponse response = api.sendSignedTransaction(10, txInfo);
        assertNotNull(response);
        assertTrue(response.isSuccess());
    }

    @Test
    void submitOrderSignsAndSendsTransaction() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream",
                createFixedSigner());
        LighterCreateOrderRequest request = buildValidOrderRequest();

        LighterSendTxResponse response = api.submitOrder(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(1, api.postedTxMessages.size());

        JSONObject posted = new JSONObject(api.postedTxMessages.get(0));
        assertEquals("jsonapi/sendtx", posted.getString("type"));
        assertEquals(14, posted.getJSONObject("data").getInt("tx_type"));
        assertEquals(2, posted.getJSONObject("data").getJSONObject("tx_info").getInt("market_index"));
    }

    @Test
    void cancelOrderSignsAndSendsTransaction() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream",
                createFixedSigner());
        LighterCancelOrderRequest request = buildValidCancelRequest();

        LighterSendTxResponse response = api.cancelOrder(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(1, api.postedTxMessages.size());

        JSONObject posted = new JSONObject(api.postedTxMessages.get(0));
        assertEquals("jsonapi/sendtx", posted.getString("type"));
        assertEquals(11, posted.getJSONObject("data").getInt("tx_type"));
        assertEquals(2, posted.getJSONObject("data").getJSONObject("tx_info").getInt("market_index"));
        assertEquals(77L, posted.getJSONObject("data").getJSONObject("tx_info").getLong("order_index"));
    }

    @Test
    void modifyOrderSignsAndSendsTransaction() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream",
                createFixedSigner());
        LighterModifyOrderRequest request = buildValidModifyRequest();

        LighterSendTxResponse response = api.modifyOrder(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(1, api.postedTxMessages.size());

        JSONObject posted = new JSONObject(api.postedTxMessages.get(0));
        assertEquals("jsonapi/sendtx", posted.getString("type"));
        assertEquals(13, posted.getJSONObject("data").getInt("tx_type"));
        assertEquals(2, posted.getJSONObject("data").getJSONObject("tx_info").getInt("market_index"));
        assertEquals(77L, posted.getJSONObject("data").getJSONObject("tx_info").getLong("order_index"));
        assertEquals(1500L, posted.getJSONObject("data").getJSONObject("tx_info").getLong("base_amount"));
    }

    @Test
    void signOrderRequiresConfiguredSigner() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream", null);
        assertThrows(IllegalStateException.class, () -> api.signOrder(buildValidOrderRequest()));
    }

    @Test
    void signCancelOrderRequiresConfiguredSigner() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream", null);
        assertThrows(IllegalStateException.class, () -> api.signCancelOrder(buildValidCancelRequest()));
    }

    @Test
    void signModifyOrderRequiresConfiguredSigner() {
        TestableLighterWebSocketApi api = new TestableLighterWebSocketApi("wss://example.test/stream", null);
        assertThrows(IllegalStateException.class, () -> api.signModifyOrder(buildValidModifyRequest()));
    }

    private LighterCreateOrderRequest buildValidOrderRequest() {
        LighterCreateOrderRequest request = new LighterCreateOrderRequest();
        request.setMarketIndex(2);
        request.setClientOrderIndex(1L);
        request.setBaseAmount(1000L);
        request.setPrice(90000);
        request.setAsk(false);
        request.setOrderType(LighterOrderType.MARKET);
        request.setTimeInForce(LighterTimeInForce.IOC);
        return request;
    }

    private LighterCancelOrderRequest buildValidCancelRequest() {
        LighterCancelOrderRequest request = new LighterCancelOrderRequest();
        request.setMarketIndex(2);
        request.setOrderIndex(77L);
        return request;
    }

    private LighterModifyOrderRequest buildValidModifyRequest() {
        LighterModifyOrderRequest request = new LighterModifyOrderRequest();
        request.setMarketIndex(2);
        request.setOrderIndex(77L);
        request.setBaseAmount(1500L);
        request.setPrice(92000);
        request.setAsk(false);
        request.setOrderType(LighterOrderType.LIMIT);
        request.setTimeInForce(LighterTimeInForce.GTT);
        return request;
    }

    private ILighterTransactionSigner createFixedSigner() {
        return new ILighterTransactionSigner() {
            @Override
            public LighterSignedTransaction signCreateOrder(LighterCreateOrderRequest orderRequest) {
                JSONObject txInfo = new JSONObject();
                txInfo.put("market_index", orderRequest.getMarketIndex());
                txInfo.put("base_amount", orderRequest.getBaseAmount());
                return new LighterSignedTransaction(14, txInfo, "0xhash", "0xmessage");
            }

            @Override
            public LighterSignedTransaction signCancelOrder(LighterCancelOrderRequest cancelRequest) {
                JSONObject txInfo = new JSONObject();
                txInfo.put("market_index", cancelRequest.getMarketIndex());
                txInfo.put("order_index", cancelRequest.getOrderIndex());
                return new LighterSignedTransaction(11, txInfo, "0xhash", "0xmessage");
            }

            @Override
            public LighterSignedTransaction signModifyOrder(LighterModifyOrderRequest modifyRequest) {
                JSONObject txInfo = new JSONObject();
                txInfo.put("market_index", modifyRequest.getMarketIndex());
                txInfo.put("order_index", modifyRequest.getOrderIndex());
                txInfo.put("base_amount", modifyRequest.getBaseAmount());
                txInfo.put("price", modifyRequest.getPrice());
                return new LighterSignedTransaction(13, txInfo, "0xhash", "0xmessage");
            }
        };
    }

    private static class TestableLighterWebSocketApi extends LighterWebSocketApi {
        private final Map<String, TestClient> createdClients = new ConcurrentHashMap<>();
        private final Map<String, Integer> connectCountByChannel = new ConcurrentHashMap<>();
        private final Map<String, Integer> closeCountByChannel = new ConcurrentHashMap<>();
        private final List<String> postedTxMessages = new CopyOnWriteArrayList<>();
        private final ILighterTransactionSigner providedSigner;

        TestableLighterWebSocketApi(String url) {
            this(url, null);
        }

        TestableLighterWebSocketApi(String url, ILighterTransactionSigner signer) {
            super(url, signer);
            this.providedSigner = signer;
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

        @Override
        protected LighterWebSocketClient createSendTxClient(IWebSocketProcessor processor) {
            try {
                return new TestTxClient("wss://example.test/stream", processor, postedTxMessages, connectCountByChannel,
                        closeCountByChannel);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected LighterSendTxWebSocketProcessor createSendTxProcessor() {
            return new LighterSendTxWebSocketProcessor(this::handleTxClientClosed);
        }

        @Override
        protected void waitForTxClientConnection(LighterWebSocketClient client, long timeoutMillis) {
            // no-op for test doubles
        }

        @Override
        protected synchronized ILighterTransactionSigner getOrCreateOrderSigner() {
            return providedSigner;
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

    private static class TestTxClient extends LighterWebSocketClient {
        private final List<String> postedTxMessages;
        private final Map<String, Integer> connectCountByChannel;
        private final Map<String, Integer> closeCountByChannel;

        TestTxClient(String serverUri, IWebSocketProcessor processor, List<String> postedTxMessages,
                Map<String, Integer> connectCountByChannel, Map<String, Integer> closeCountByChannel) throws Exception {
            super(serverUri, null, processor);
            this.postedTxMessages = postedTxMessages;
            this.connectCountByChannel = connectCountByChannel;
            this.closeCountByChannel = closeCountByChannel;
        }

        @Override
        public void connect() {
            connectCountByChannel.merge("tx", 1, Integer::sum);
        }

        @Override
        public void close() {
            closeCountByChannel.merge("tx", 1, Integer::sum);
            processor.connectionClosed(1000, "manual close", false);
        }

        @Override
        public void postMessage(String message) {
            postedTxMessages.add(message);
            JSONObject request = new JSONObject(message);
            String id = request.opt("id").toString();
            processor.messageReceived("{\"code\":200,\"msg\":\"ok\",\"id\":\"" + id + "\"}");
        }
    }
}
