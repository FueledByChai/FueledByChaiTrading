package com.fueledbychai.deribit.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class DeribitWebSocketApiTest {

    private final List<TestableDeribitWebSocketApi> apisToCleanup = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (TestableDeribitWebSocketApi api : apisToCleanup) {
            api.disconnectAll();
        }
        apisToCleanup.clear();
    }

    @Test
    void subscribeRequestsAreBatched() throws Exception {
        TestableDeribitWebSocketApi api = newApi(3, 10L, 1);

        for (int i = 0; i < 7; i++) {
            api.subscribeTicker("btc-chan-" + i, update -> {
            });
        }

        api.drainNow();
        api.drainNow();
        api.drainNow();

        List<JsonObject> requests = api.getSentRequests();
        assertEquals(3, requests.size());

        Set<String> subscribedChannels = new HashSet<>();
        for (JsonObject request : requests) {
            assertEquals("public/subscribe", request.get("method").getAsString());
            JsonArray channels = request.getAsJsonObject("params").getAsJsonArray("channels");
            assertTrue(channels.size() <= 3);
            channels.forEach(channel -> subscribedChannels.add(channel.getAsString()));
        }

        assertEquals(7, subscribedChannels.size());
    }

    @Test
    void subscribeErrorRetriesAndSuccessActivatesChannel() throws Exception {
        TestableDeribitWebSocketApi api = newApi(10, 10L, 1);

        api.subscribeTicker("btc-retry-1", update -> {
        });
        api.drainNow();
        waitFor(() -> api.getSentRequests().size() >= 1, 500L);

        JsonObject firstRequest = api.getSentRequests().get(0);
        long firstId = firstRequest.get("id").getAsLong();
        String channel = firstRequest.getAsJsonObject("params").getAsJsonArray("channels").get(0).getAsString();

        api.processMessage(String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"error\":{\"code\":10028,\"message\":\"too_many_requests\"}}",
                firstId));
        api.drainNow();
        waitFor(() -> api.getSentRequests().size() >= 2, 500L);

        JsonObject retryRequest = api.getSentRequests().get(1);
        assertEquals(channel, retryRequest.getAsJsonObject("params").getAsJsonArray("channels").get(0).getAsString());
        long retryId = retryRequest.get("id").getAsLong();

        api.processMessage(String.format("{\"jsonrpc\":\"2.0\",\"id\":%d,\"result\":[\"%s\"]}", retryId, channel));
        waitFor(() -> api.isChannelActive(channel), 1_500L);
        assertTrue(api.isChannelActive(channel));
    }

    private TestableDeribitWebSocketApi newApi(int batchSize, long intervalMs, int maxRetries) {
        TestableDeribitWebSocketApi api = new TestableDeribitWebSocketApi(batchSize, intervalMs, maxRetries);
        apisToCleanup.add(api);
        return api;
    }

    private void waitFor(BooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not satisfied before timeout");
    }

    private static class TestableDeribitWebSocketApi extends DeribitWebSocketApi {

        private final int batchSize;
        private final long intervalMillis;
        private final int maxRetries;
        private final CopyOnWriteArrayList<JsonObject> sentRequests = new CopyOnWriteArrayList<>();

        TestableDeribitWebSocketApi(int batchSize, long intervalMillis, int maxRetries) {
            super("wss://test.deribit.com/ws/api/v2");
            this.batchSize = batchSize;
            this.intervalMillis = intervalMillis;
            this.maxRetries = maxRetries;
        }

        @Override
        protected void ensureConnected() {
            connected = true;
            manualDisconnect = false;
        }

        @Override
        protected boolean canSend() {
            return connected;
        }

        @Override
        protected int getSubscribeBatchSize() {
            return batchSize;
        }

        @Override
        protected long getSubscribeSendIntervalMillis() {
            return intervalMillis;
        }

        @Override
        protected int getMaxSubscribeRetries() {
            return maxRetries;
        }

        @Override
        protected void sendText(String payload) {
            sentRequests.add(JsonParser.parseString(payload).getAsJsonObject());
        }

        List<JsonObject> getSentRequests() {
            return new ArrayList<>(sentRequests);
        }

        void processMessage(String message) {
            handleMessage(message);
        }

        void drainNow() {
            drainSubscribeQueue();
        }

        boolean isChannelActive(String channel) {
            return activeChannels.contains(channel);
        }
    }
}
