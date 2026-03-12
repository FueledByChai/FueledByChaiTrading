package com.fueledbychai.okx.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fueledbychai.okx.common.api.ws.model.OkxFundingRateUpdate;
import com.fueledbychai.okx.common.api.ws.model.OkxOrderBookUpdate;
import com.fueledbychai.okx.common.api.ws.model.OkxTickerUpdate;
import com.fueledbychai.okx.common.api.ws.model.OkxTrade;

class OkxWebSocketApiTest {

    private final List<TestableOkxWebSocketApi> apisToCleanup = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (TestableOkxWebSocketApi api : apisToCleanup) {
            api.disconnectAll();
        }
        apisToCleanup.clear();
    }

    @Test
    void subscribeRequestsAreBatched() {
        TestableOkxWebSocketApi api = newApi(2, 10L, 1);

        for (int i = 0; i < 5; i++) {
            api.subscribeTicker("btc-usdt-" + i, update -> {
            });
        }

        api.drainNow();
        api.drainNow();
        api.drainNow();

        List<JsonObject> requests = api.getSentRequests();
        assertEquals(3, requests.size());

        Set<String> subscriptions = new HashSet<>();
        for (JsonObject request : requests) {
            assertEquals("subscribe", request.get("op").getAsString());
            JsonArray args = request.getAsJsonArray("args");
            assertTrue(args.size() <= 2);
            args.forEach(arg -> {
                JsonObject argObject = arg.getAsJsonObject();
                subscriptions.add(argObject.get("channel").getAsString() + ":" + argObject.get("instId").getAsString());
            });
        }

        assertEquals(5, subscriptions.size());
    }

    @Test
    void parsesAndDispatchesTickerFundingBookAndTrades() {
        TestableOkxWebSocketApi api = newApi(5, 10L, 1);

        AtomicReference<OkxTickerUpdate> tickerUpdate = new AtomicReference<>();
        AtomicReference<OkxFundingRateUpdate> fundingRateUpdate = new AtomicReference<>();
        AtomicReference<OkxOrderBookUpdate> bookUpdate = new AtomicReference<>();
        AtomicReference<List<OkxTrade>> trades = new AtomicReference<>();

        api.subscribeTicker("BTC-USDT", tickerUpdate::set);
        api.subscribeFundingRate("BTC-USDT-SWAP", fundingRateUpdate::set);
        api.subscribeOrderBook("BTC-USDT", bookUpdate::set);
        api.subscribeTrades("BTC-USDT", (instrumentId, updateTrades) -> trades.set(updateTrades));

        api.processMessage(
                """
                        {"arg":{"channel":"tickers","instId":"BTC-USDT"},"data":[{"instId":"BTC-USDT","bidPx":"100.1","bidSz":"5","askPx":"100.2","askSz":"3","last":"100.15","ts":"1710000000000"}]}
                        """);

        api.processMessage(
                """
                        {"arg":{"channel":"books5","instId":"BTC-USDT"},"data":[{"bids":[["100.1","2","0","1"]],"asks":[["100.2","4","0","1"]],"ts":"1710000000100"}]}
                        """);

        api.processMessage(
                """
                        {"arg":{"channel":"funding-rate","instId":"BTC-USDT-SWAP"},"data":[{"instId":"BTC-USDT-SWAP","instType":"SWAP","fundingRate":"0.0008","nextFundingRate":"0.0010","fundingTime":"1710003600000","nextFundingTime":"1710032400000"}]}
                        """);

        api.processMessage(
                """
                        {"arg":{"channel":"trades","instId":"BTC-USDT"},"data":[{"instId":"BTC-USDT","tradeId":"1","px":"100.12","sz":"0.5","side":"buy","ts":"1710000000200"}]}
                        """);

        OkxTickerUpdate ticker = tickerUpdate.get();
        OkxFundingRateUpdate funding = fundingRateUpdate.get();
        OkxOrderBookUpdate book = bookUpdate.get();
        List<OkxTrade> tradeList = trades.get();

        assertNotNull(ticker);
        assertNotNull(funding);
        assertNotNull(book);
        assertNotNull(tradeList);

        assertEquals("BTC-USDT", ticker.getInstrumentId());
        assertEquals("100.1", ticker.getBestBidPrice().toPlainString());
        assertEquals("100.2", ticker.getBestAskPrice().toPlainString());

        assertEquals("BTC-USDT-SWAP", funding.getInstrumentId());
        assertEquals("0.0008", funding.getFundingRate().toPlainString());
        assertEquals("0.0010", funding.getNextFundingRate().toPlainString());
        assertEquals(1710003600000L, funding.getTimestamp());
        assertEquals(1710032400000L, funding.getNextFundingTime());

        assertEquals("BTC-USDT", book.getInstrumentId());
        assertFalse(book.getBids().isEmpty());
        assertFalse(book.getAsks().isEmpty());
        assertEquals("100.1", book.getBids().get(0).getPrice().toPlainString());
        assertEquals("100.2", book.getAsks().get(0).getPrice().toPlainString());

        assertEquals(1, tradeList.size());
        assertEquals("100.12", tradeList.get(0).getPrice().toPlainString());
        assertEquals("buy", tradeList.get(0).getSide());
    }

    private TestableOkxWebSocketApi newApi(int batchSize, long intervalMs, int maxRetries) {
        TestableOkxWebSocketApi api = new TestableOkxWebSocketApi(batchSize, intervalMs, maxRetries);
        apisToCleanup.add(api);
        return api;
    }

    private static class TestableOkxWebSocketApi extends OkxWebSocketApi {

        private final int batchSize;
        private final long intervalMillis;
        private final int maxRetries;
        private final CopyOnWriteArrayList<JsonObject> sentRequests = new CopyOnWriteArrayList<>();

        TestableOkxWebSocketApi(int batchSize, long intervalMillis, int maxRetries) {
            super("wss://ws.okx.test/ws/v5/public");
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
        protected void ensureSubscribeDrainScheduled() {
            // Disable background scheduling in tests so drain order is deterministic.
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
    }
}
