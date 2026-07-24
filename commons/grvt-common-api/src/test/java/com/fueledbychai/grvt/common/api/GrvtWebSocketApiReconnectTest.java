package com.fueledbychai.grvt.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.grvt.common.api.ws.GrvtWebSocketClient;

class GrvtWebSocketApiReconnectTest {

    private TestWebSocketApi api;

    @AfterEach
    void tearDown() {
        if (api != null) {
            api.disconnectAll();
        }
    }

    @Test
    void abnormalCloseRetriesTimedOutConnectAndReplaysSubscriptionExactlyOnce() throws Exception {
        api = new TestWebSocketApi(true, false, true);

        api.subscribeMarketData("book.d", "TRUMP_USDT_Perp@50", ignored -> { });
        FakeClient first = api.awaitClient(0);
        assertEquals(List.of("book.d|TRUMP_USDT_Perp@50"), first.subscriptions);

        first.simulateRemoteClose();

        FakeClient failedReconnect = api.awaitClient(1);
        FakeClient recovered = api.awaitClient(2);
        assertTrue(failedReconnect.subscriptions.isEmpty(),
                "a client that never opened must not receive subscriptions");
        assertEquals(List.of("book.d|TRUMP_USDT_Perp@50"), recovered.subscriptions,
                "the replacement socket must replay each desired subscription once");
        assertTrue(api.isMarketDataConnected());
    }

    @Test
    void forcedReconnectReplacesSilentSocketAndReplaysSubscriptionExactlyOnce() throws Exception {
        api = new TestWebSocketApi(true, true);

        api.subscribeMarketData("book.d", "SOL_USDT_Perp@50", ignored -> { });
        FakeClient first = api.awaitClient(0);
        assertEquals(List.of("book.d|SOL_USDT_Perp@50"), first.subscriptions);

        api.forceReconnectMarketData();

        FakeClient replacement = api.awaitClient(1);
        assertTrue(first.closed.get(), "the silent socket generation must be discarded");
        assertEquals(List.of("book.d|SOL_USDT_Perp@50"), replacement.subscriptions,
                "the replacement socket must replay each desired subscription once");
        assertTrue(api.isMarketDataConnected());
    }

    @Test
    void disconnectAllStartsCleanSessionAndStillReconnectsAfterRemoteClose() throws Exception {
        api = new TestWebSocketApi(true, true, true);

        api.subscribeMarketData("book.d", "ARB_USDT_Perp@50", ignored -> { });
        FakeClient first = api.awaitClient(0);

        api.disconnectAll();
        assertTrue(first.closed.get());

        api.subscribeMarketData("book.d", "ARB_USDT_Perp@50", ignored -> { });
        FakeClient restarted = api.awaitClient(1);
        assertEquals(List.of("book.d|ARB_USDT_Perp@50"), restarted.subscriptions,
                "the new session must not retain or duplicate the old callback");

        restarted.simulateRemoteClose();
        FakeClient recovered = api.awaitClient(2);
        assertEquals(List.of("book.d|ARB_USDT_Perp@50"), recovered.subscriptions);
        assertTrue(api.isMarketDataConnected());
    }

    @Test
    void disconnectMarketDataLeavesAuthenticatedTradeSessionConnected() throws Exception {
        api = new TestWebSocketApi(true, true, true);

        api.subscribeMarketData("book.d", "ASTER_USDT_Perp@50", ignored -> { });
        FakeClient market = api.awaitClient(0);
        api.subscribeTradeData("order", "sub-account", ignored -> { });
        FakeClient trade = api.awaitClient(1);

        api.disconnectMarketData();

        assertTrue(market.closed.get());
        assertTrue(trade.isOpen(), "market-data stop must not close authenticated trading");
        assertTrue(api.isTradeDataConnected());

        api.subscribeMarketData("book.d", "ASTER_USDT_Perp@50", ignored -> { });
        FakeClient restartedMarket = api.awaitClient(2);
        assertEquals(List.of("book.d|ASTER_USDT_Perp@50"), restartedMarket.subscriptions);
        assertTrue(api.isTradeDataConnected());
    }

    private static final class TestWebSocketApi extends GrvtWebSocketApi {
        private final List<Boolean> connectResults;
        private final List<FakeClient> clients = new ArrayList<>();

        private TestWebSocketApi(Boolean... connectResults) {
            super(GrvtEnvironment.forName("testnet"), restApiStub());
            this.connectResults = new ArrayList<>(List.of(connectResults));
        }

        private static IGrvtRestApi restApiStub() {
            return (IGrvtRestApi) Proxy.newProxyInstance(
                    IGrvtRestApi.class.getClassLoader(),
                    new Class<?>[] { IGrvtRestApi.class },
                    (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        }

        @Override
        synchronized GrvtWebSocketClient createClient(String url, Map<String, String> headers) {
            boolean connectResult = connectResults.isEmpty() || connectResults.remove(0);
            FakeClient client = new FakeClient(connectResult);
            clients.add(client);
            notifyAll();
            return client;
        }

        @Override
        long reconnectDelaySeconds() {
            return 0L;
        }

        synchronized FakeClient awaitClient(int index) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 2_000L;
            while (clients.size() <= index && System.currentTimeMillis() < deadline) {
                wait(25L);
            }
            assertTrue(clients.size() > index, "timed out waiting for GRVT client " + index);
            return clients.get(index);
        }
    }

    private static final class FakeClient extends GrvtWebSocketClient {
        private final boolean connectResult;
        private final AtomicBoolean open = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final List<String> subscriptions = new ArrayList<>();

        private FakeClient(boolean connectResult) {
            super(URI.create("ws://localhost/fake"), Map.of());
            this.connectResult = connectResult;
        }

        @Override
        public boolean connectBlocking(long timeout, TimeUnit timeUnit) {
            open.set(connectResult);
            return connectResult;
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public void close() {
            closed.set(true);
            open.set(false);
        }

        @Override
        public void subscribe(String stream, String selector,
                java.util.function.Consumer<JsonNode> callback) {
            subscriptions.add(stream + "|" + selector);
        }

        private void simulateRemoteClose() {
            open.set(false);
            onClose(1006, "abnormal close", true);
        }
    }
}
