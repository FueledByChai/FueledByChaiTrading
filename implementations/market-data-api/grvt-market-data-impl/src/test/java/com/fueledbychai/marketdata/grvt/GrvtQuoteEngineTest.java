package com.fueledbychai.marketdata.grvt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.grvt.common.api.IGrvtRestApi;
import com.fueledbychai.grvt.common.api.IGrvtWebSocketApi;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;

class GrvtQuoteEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void connectionHealthUsesActualMarketSocketAndForcedReconnectIsSelective() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        GrvtQuoteEngine engine = new GrvtQuoteEngine(new StubRestApi(), webSocketApi,
                new NoOpTickerRegistry());

        engine.startEngine();
        assertTrue(engine.isConnected());

        webSocketApi.marketDataConnected = false;
        assertFalse(engine.isConnected(), "started must not masquerade as socket-connected");

        engine.forceReconnectMarketData();
        assertEquals(1, webSocketApi.forcedMarketReconnects.get());
    }

    @Test
    void subscribesToFiftyMillisecondDeltaBookOnce() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), webSocketApi,
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker();

        engine.subscribeMarketDepth(ticker, quote -> {
        });
        engine.subscribeMarketDepth(ticker, quote -> {
        });

        assertEquals(1, webSocketApi.marketDataSubscriptions.get());
        assertEquals("book.d", webSocketApi.lastStream);
        assertEquals("STRK_USDT_Perp@50", webSocketApi.lastSelector);
        assertNotNull(webSocketApi.lastListener);
    }

    @Test
    void stopStartCreatesCleanMarketSessionWithoutStoppingTradeSocket() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), webSocketApi,
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker();

        engine.startEngine();
        engine.subscribeLevel1(ticker, quote -> { });
        engine.subscribeLevel1(ticker, quote -> { });
        assertEquals(2, engine.level1ListenerCount(ticker));
        assertEquals(2, webSocketApi.marketDataSubscriptions.get());

        engine.stopEngine();
        assertEquals(0, engine.level1ListenerCount(ticker));
        assertEquals(1, webSocketApi.marketDisconnects.get());
        assertEquals(0, webSocketApi.allDisconnects.get());

        engine.startEngine();
        engine.subscribeLevel1(ticker, quote -> { });
        assertEquals(1, engine.level1ListenerCount(ticker));
        assertEquals(4, webSocketApi.marketDataSubscriptions.get(),
                "the restarted session should install one fresh mini and ticker stream");
    }

    @Test
    void parsesMarketDataAsHumanReadableDecimals() throws Exception {
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), new StubWebSocketApi(),
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker();

        engine.onMiniTicker(ticker, json("""
                {"feed":{"event_time":"1710000000123000000","best_bid_price":"0.02952",
                  "best_bid_size":"83183.4","best_ask_price":"0.02955","best_ask_size":"3089.2"}}
                """));
        engine.onTrade(ticker, json("""
                {"feed":[{"event_time":"1710000000123000000","price":"0.02953",
                  "size":"263976.2","is_taker_buyer":true}]}
                """));

        assertNotNull(engine.lastLevel1Quote);
        assertEquals(new BigDecimal("0.02952"), engine.lastLevel1Quote.getValue(QuoteType.BID));
        assertEquals(new BigDecimal("83183.4"), engine.lastLevel1Quote.getValue(QuoteType.BID_SIZE));
        assertEquals(new BigDecimal("0.02955"), engine.lastLevel1Quote.getValue(QuoteType.ASK));
        assertEquals(new BigDecimal("3089.2"), engine.lastLevel1Quote.getValue(QuoteType.ASK_SIZE));
        assertNotNull(engine.lastOrderFlow);
        assertEquals(new BigDecimal("0.02953"), engine.lastOrderFlow.getPrice());
        assertEquals(new BigDecimal("263976.2"), engine.lastOrderFlow.getSize());
    }

    @Test
    void publishesFundingAndTwentyFourHourBaseAndNotionalVolume() throws Exception {
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), new StubWebSocketApi(),
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker().setFundingRateInterval(4);

        engine.onTicker(ticker, json("""
                {"feed":{"event_time":"1710000000123000000","last_price":"0.02961",
                  "funding_rate":"0.005","buy_volume_24h_b":"162448675.5",
                  "sell_volume_24h_b":"162954014.6","buy_volume_24h_q":"4722483.346911",
                  "sell_volume_24h_q":"4736099.776745"}}
                """));

        assertNotNull(engine.lastLevel1Quote);
        assertEquals(0, engine.lastLevel1Quote.getValue(QuoteType.FUNDING_RATE_HOURLY_BPS)
                .compareTo(new BigDecimal("0.125")));
        assertEquals(0, engine.lastLevel1Quote.getValue(QuoteType.FUNDING_RATE_APR)
                .compareTo(new BigDecimal("10.95")));
        assertEquals(new BigDecimal("325402690.1"), engine.lastLevel1Quote.getValue(QuoteType.VOLUME));
        assertEquals(new BigDecimal("9458583.123656"),
                engine.lastLevel1Quote.getValue(QuoteType.VOLUME_NOTIONAL));
    }

    @Test
    void buildsInitialSnapshotAndAppliesAbsoluteSizeDeltasAndDeletes() throws Exception {
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), new StubWebSocketApi(),
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker();

        engine.onOrderBook(ticker, bookFrame(100, 99, 1_710_000_000_000_000_000L,
                "[{\"price\":\"0.02952\",\"size\":\"83183.4\"},{\"price\":\"0.02951\",\"size\":\"500\"}]",
                "[{\"price\":\"0.02955\",\"size\":\"3089.2\"}]"));

        assertBook(engine.lastLevel2Quote, "0.02952", 83183.4d, "0.02955", 3089.2d);

        engine.onOrderBook(ticker, bookFrame(101, 100, 1_710_000_000_050_000_000L,
                "[{\"price\":\"0.02952\",\"size\":\"0\"},{\"price\":\"0.02951\",\"size\":\"750\"}]",
                "[{\"price\":\"0.02955\",\"size\":\"4000\"}]"));

        assertBook(engine.lastLevel2Quote, "0.02951", 750d, "0.02955", 4000d);
        assertEquals(2, engine.level2Count.get());
    }

    @Test
    void acceptsLiveGatewaySequenceAfterZeroSequenceSnapshotWithoutResync() throws Exception {
        StubRestApi restApi = new StubRestApi();
        CapturingQuoteEngine engine = new CapturingQuoteEngine(restApi, new StubWebSocketApi(),
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker();

        engine.onOrderBook(ticker, bookFrame(0, -1, 1_710_000_000_000_000_000L,
                "[{\"price\":\"0.02952\",\"size\":\"100\"}]",
                "[{\"price\":\"0.02955\",\"size\":\"200\"}]"));
        engine.onOrderBook(ticker, bookFrame(252959, 252958, 1_710_000_000_050_000_000L,
                "[{\"price\":\"0.02953\",\"size\":\"150\"}]", "[]"));

        assertEquals(0, restApi.orderBookRequests.get());
        assertBook(engine.lastLevel2Quote, "0.02953", 150d, "0.02955", 200d);
        assertEquals(2, engine.level2Count.get());
    }

    @Test
    void zeroSequenceSnapshotReplacesExistingStateAfterReconnect() throws Exception {
        StubRestApi restApi = new StubRestApi();
        CapturingQuoteEngine engine = new CapturingQuoteEngine(restApi, new StubWebSocketApi(),
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker();

        engine.onOrderBook(ticker, bookFrame(252959, 252958, 1_710_000_000_000_000_000L,
                "[{\"price\":\"0.02952\",\"size\":\"100\"}]",
                "[{\"price\":\"0.02955\",\"size\":\"200\"}]"));
        engine.onOrderBook(ticker, bookFrame(0, -1, 1_710_000_001_000_000_000L,
                "[{\"price\":\"0.02940\",\"size\":\"1000\"}]",
                "[{\"price\":\"0.02960\",\"size\":\"2000\"}]"));
        engine.onOrderBook(ticker, bookFrame(300001, 300000, 1_710_000_001_050_000_000L,
                "[{\"price\":\"0.02941\",\"size\":\"1100\"}]", "[]"));

        assertEquals(0, restApi.orderBookRequests.get());
        assertBook(engine.lastLevel2Quote, "0.02941", 1100d, "0.02960", 2000d);
        assertEquals(3, engine.level2Count.get());
    }

    @Test
    void ignoresStaleDelta() throws Exception {
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), new StubWebSocketApi(),
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker();

        engine.onOrderBook(ticker, bookFrame(100, 99, 1_710_000_000_000_000_000L,
                "[{\"price\":\"0.02952\",\"size\":\"100\"}]",
                "[{\"price\":\"0.02955\",\"size\":\"200\"}]"));
        engine.onOrderBook(ticker, bookFrame(100, 99, 1_710_000_000_050_000_000L,
                "[{\"price\":\"0.02954\",\"size\":\"999\"}]", "[]"));

        assertBook(engine.lastLevel2Quote, "0.02952", 100d, "0.02955", 200d);
        assertEquals(1, engine.level2Count.get());
    }

    @Test
    void sequenceGapResynchronizesFromRestThenAppliesNewerDelta() throws Exception {
        StubRestApi restApi = new StubRestApi();
        restApi.orderBookSnapshot = json("""
                {"event_time":"1710000000100000000",
                 "bids":[{"price":"0.02940","size":"1000"}],
                 "asks":[{"price":"0.02960","size":"2000"}]}
                """);
        CapturingQuoteEngine engine = new CapturingQuoteEngine(restApi, new StubWebSocketApi(),
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker();

        engine.onOrderBook(ticker, bookFrame(100, 99, 1_710_000_000_000_000_000L,
                "[{\"price\":\"0.02952\",\"size\":\"100\"}]",
                "[{\"price\":\"0.02955\",\"size\":\"200\"}]"));
        engine.onOrderBook(ticker, bookFrame(103, 102, 1_710_000_000_150_000_000L,
                "[{\"price\":\"0.02945\",\"size\":\"1500\"}]", "[]"));

        assertEquals(1, restApi.orderBookRequests.get());
        assertEquals(500, restApi.lastDepth);
        assertBook(engine.lastLevel2Quote, "0.02945", 1500d, "0.02960", 2000d);
        assertEquals(2, engine.level2Count.get());
    }

    @Test
    void sequenceGapDoesNotReplayDeltaAlreadyCoveredByNewerRestSnapshot() throws Exception {
        StubRestApi restApi = new StubRestApi();
        restApi.orderBookSnapshot = json("""
                {"event_time":"1710000000200000000",
                 "bids":[{"price":"0.02948","size":"1000"}],
                 "asks":[{"price":"0.02960","size":"2000"}]}
                """);
        CapturingQuoteEngine engine = new CapturingQuoteEngine(restApi, new StubWebSocketApi(),
                new NoOpTickerRegistry());
        Ticker ticker = strkTicker();

        engine.onOrderBook(ticker, bookFrame(100, 99, 1_710_000_000_000_000_000L,
                "[{\"price\":\"0.02952\",\"size\":\"100\"}]",
                "[{\"price\":\"0.02955\",\"size\":\"200\"}]"));
        engine.onOrderBook(ticker, bookFrame(103, 102, 1_710_000_000_150_000_000L,
                "[{\"price\":\"0.02950\",\"size\":\"999\"}]", "[]"));

        assertBook(engine.lastLevel2Quote, "0.02948", 1000d, "0.02960", 2000d);
    }

    private static void assertBook(ILevel2Quote quote, String bid, double bidSize, String ask, double askSize) {
        assertNotNull(quote);
        assertEquals(0, quote.getOrderBook().getBestBid().getPrice().compareTo(new BigDecimal(bid)));
        assertEquals(bidSize, quote.getOrderBook().getBestBid().getSize(), 1.0e-9);
        assertEquals(0, quote.getOrderBook().getBestAsk().getPrice().compareTo(new BigDecimal(ask)));
        assertEquals(askSize, quote.getOrderBook().getBestAsk().getSize(), 1.0e-9);
    }

    private static JsonNode bookFrame(long sequence, long previousSequence, long eventTimeNanos,
            String bids, String asks) throws Exception {
        return json("""
                {"stream":"v1.book.d","selector":"STRK_USDT_Perp",
                 "sequence_number":"%d","prev_sequence_number":"%d",
                 "feed":{"event_time":"%d","instrument":"STRK_USDT_Perp",
                         "bids":%s,"asks":%s}}
                """.formatted(sequence, previousSequence, eventTimeNanos, bids, asks));
    }

    private static Ticker strkTicker() {
        return new Ticker("STRK_USDT_Perp")
                .setExchange(Exchange.GRVT)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setMinimumTickSize(new BigDecimal("0.00001"))
                .setOrderSizeIncrement(new BigDecimal("0.1"));
    }

    private static JsonNode json(String value) throws Exception {
        return OBJECT_MAPPER.readTree(value);
    }

    private static final class CapturingQuoteEngine extends GrvtQuoteEngine {
        private ILevel1Quote lastLevel1Quote;
        private ILevel2Quote lastLevel2Quote;
        private OrderFlow lastOrderFlow;
        private final AtomicInteger level2Count = new AtomicInteger();

        private CapturingQuoteEngine(IGrvtRestApi restApi, IGrvtWebSocketApi webSocketApi,
                ITickerRegistry tickerRegistry) {
            super(restApi, webSocketApi, tickerRegistry);
        }

        @Override
        public void fireLevel1Quote(ILevel1Quote quote) {
            lastLevel1Quote = quote;
        }

        @Override
        public void fireMarketDepthQuote(ILevel2Quote quote) {
            lastLevel2Quote = quote;
            level2Count.incrementAndGet();
        }

        @Override
        public void fireOrderFlow(OrderFlow orderFlow) {
            lastOrderFlow = orderFlow;
        }

        private int level1ListenerCount(Ticker ticker) {
            return level1ListenerMap.getOrDefault(ticker, java.util.List.of()).size();
        }
    }

    private static final class StubWebSocketApi implements IGrvtWebSocketApi {
        private final AtomicInteger marketDataSubscriptions = new AtomicInteger();
        private final AtomicInteger forcedMarketReconnects = new AtomicInteger();
        private final AtomicInteger marketDisconnects = new AtomicInteger();
        private final AtomicInteger allDisconnects = new AtomicInteger();
        private boolean marketDataConnected = true;
        private String lastStream;
        private String lastSelector;
        private Consumer<JsonNode> lastListener;

        @Override
        public void connectMarketData() {
        }

        @Override
        public void connectTradeData() {
        }

        @Override
        public boolean isMarketDataConnected() {
            return marketDataConnected;
        }

        @Override
        public void forceReconnectMarketData() {
            forcedMarketReconnects.incrementAndGet();
            marketDataConnected = true;
        }

        @Override
        public boolean isTradeDataConnected() {
            return true;
        }

        @Override
        public void subscribeMarketData(String stream, String selector, Consumer<JsonNode> listener) {
            marketDataSubscriptions.incrementAndGet();
            lastStream = stream;
            lastSelector = selector;
            lastListener = listener;
        }

        @Override
        public void subscribeTradeData(String stream, String selector, Consumer<JsonNode> listener) {
        }

        @Override
        public CompletableFuture<JsonNode> rpcCreateOrder(GrvtOrder order) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<JsonNode> rpcCancelOrder(String orderId, String clientOrderId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<JsonNode> rpcCancelAllOrders() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void disconnectMarketData() {
            marketDisconnects.incrementAndGet();
            marketDataConnected = false;
        }

        @Override
        public void disconnectAll() {
            allDisconnects.incrementAndGet();
        }
    }

    private static final class StubRestApi implements IGrvtRestApi {
        private JsonNode orderBookSnapshot;
        private final AtomicInteger orderBookRequests = new AtomicInteger();
        private int lastDepth;

        @Override
        public JsonNode getOrderBook(String instrument, int depth) {
            orderBookRequests.incrementAndGet();
            lastDepth = depth;
            return orderBookSnapshot;
        }

        @Override
        public void login() {
        }

        @Override
        public void refreshCookieIfNeeded() {
        }

        @Override
        public String getSessionCookie() {
            return null;
        }

        @Override
        public String getAccountId() {
            return null;
        }

        @Override
        public boolean isPublicApiOnly() {
            return true;
        }

        @Override
        public InstrumentDescriptor[] getAllInstrumentsForTypes(InstrumentType[] instrumentTypes) {
            return new InstrumentDescriptor[0];
        }

        @Override
        public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
            return null;
        }

        @Override
        public JsonNode getInstrument(String instrument) {
            return null;
        }

        @Override
        public JsonNode getTicker(String instrument) {
            return null;
        }

        @Override
        public JsonNode getMiniTicker(String instrument) {
            return null;
        }

        @Override
        public JsonNode getRecentTrades(String instrument, int limit) {
            return null;
        }

        @Override
        public JsonNode getFundingRateHistory(String instrument, long startTimeNanos, long endTimeNanos, int limit) {
            return null;
        }

        @Override
        public JsonNode getCandlestick(String instrument, String interval, String type, long startTimeNanos,
                long endTimeNanos, int limit) {
            return null;
        }

        @Override
        public JsonNode createOrder(GrvtOrder order) {
            return null;
        }

        @Override
        public JsonNode cancelOrder(String orderId, String clientOrderId) {
            return null;
        }

        @Override
        public JsonNode cancelAllOrders() {
            return null;
        }

        @Override
        public JsonNode getOpenOrders() {
            return null;
        }

        @Override
        public JsonNode getOrder(String orderId, String clientOrderId) {
            return null;
        }

        @Override
        public JsonNode getPositions() {
            return null;
        }

        @Override
        public JsonNode getAccountSummary() {
            return null;
        }

        @Override
        public JsonNode getFillHistory(long startTimeNanos, long endTimeNanos, int limit) {
            return null;
        }

        @Override
        public JsonNode buildSignedOrderRequest(GrvtOrder order) {
            return null;
        }
    }

    private static final class NoOpTickerRegistry implements ITickerRegistry {
        @Override
        public Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString) {
            return null;
        }

        @Override
        public Ticker lookupByCommonSymbol(InstrumentType instrumentType, String commonSymbol) {
            return null;
        }

        @Override
        public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
            return commonSymbol;
        }
    }
}
