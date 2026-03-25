package com.fueledbychai.marketdata.aster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.aster.common.api.IAsterRestApi;
import com.fueledbychai.aster.common.api.IAsterWebSocketApi;
import com.fueledbychai.aster.common.api.ws.AsterWebSocketClient;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.websocket.IWebSocketEventListener;

class AsterQuoteEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void startAndStopEngineToggleConnectionState() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), webSocketApi, new NoOpTickerRegistry());

        engine.startEngine();
        assertTrue(engine.started());
        assertTrue(engine.isConnected());

        engine.stopEngine();
        assertFalse(engine.started());
        assertFalse(engine.isConnected());
        assertEquals(1, webSocketApi.disconnectCount.get());
    }

    @Test
    void subscribeLevel1StartsStreamsOnce() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), webSocketApi, new NoOpTickerRegistry());
        Ticker ticker = perpTicker();
        Level1QuoteListener listener = quote -> {
        };

        engine.subscribeLevel1(ticker, listener);
        engine.subscribeLevel1(ticker, listener);

        assertEquals(1, webSocketApi.bookTickerSubscriptions.get());
        assertEquals(1, webSocketApi.symbolTickerSubscriptions.get());
        assertEquals(1, webSocketApi.markPriceSubscriptions.get());
    }

    @Test
    void subscribeLevel1ForSpotSkipsMarkPriceStream() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), webSocketApi, new NoOpTickerRegistry());

        engine.subscribeLevel1(spotTicker(), quote -> {
        });

        assertEquals(1, webSocketApi.bookTickerSubscriptions.get());
        assertEquals(1, webSocketApi.symbolTickerSubscriptions.get());
        assertEquals(0, webSocketApi.markPriceSubscriptions.get());
    }

    @Test
    void bookTickerUpdatesProduceLevel1Quote() throws Exception {
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), new StubWebSocketApi(),
                new NoOpTickerRegistry());

        engine.onBookTickerUpdate(perpTicker(), json("""
                {"E":1710000000123,"b":"100.1","B":"2.5","a":"100.2","A":"3.0"}
                """));

        ILevel1Quote quote = engine.lastLevel1Quote;
        assertNotNull(quote);
        assertEquals(new BigDecimal("100.1"), quote.getValue(QuoteType.BID));
        assertEquals(new BigDecimal("2.5"), quote.getValue(QuoteType.BID_SIZE));
        assertEquals(new BigDecimal("100.2"), quote.getValue(QuoteType.ASK));
        assertEquals(new BigDecimal("3.0"), quote.getValue(QuoteType.ASK_SIZE));
    }

    @Test
    void markPriceUpdatesProduceFundingQuotes() throws Exception {
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), new StubWebSocketApi(),
                new NoOpTickerRegistry());

        engine.onMarkPriceUpdate(perpTicker(), json("""
                {"E":1710000000123,"p":"101.5","i":"101.2","r":"0.0008"}
                """));

        ILevel1Quote quote = engine.lastLevel1Quote;
        assertNotNull(quote);
        assertEquals(new BigDecimal("101.5"), quote.getValue(QuoteType.MARK_PRICE));
        assertEquals(new BigDecimal("101.2"), quote.getValue(QuoteType.UNDERLYING_PRICE));
        assertEquals(new BigDecimal("1.0000"), quote.getValue(QuoteType.FUNDING_RATE_HOURLY_BPS));
    }

    @Test
    void symbolTickerUpdatesProduceLastPriceAndVolumeQuotes() throws Exception {
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), new StubWebSocketApi(),
                new NoOpTickerRegistry());

        engine.onSymbolTickerUpdate(perpTicker(), json("""
                {"E":1710000000123,"c":"101.7","Q":"0.25","v":"1500.5","q":"152625.85"}
                """));

        ILevel1Quote quote = engine.lastLevel1Quote;
        assertNotNull(quote);
        assertEquals(new BigDecimal("101.7"), quote.getValue(QuoteType.LAST));
        assertEquals(new BigDecimal("0.25"), quote.getValue(QuoteType.LAST_SIZE));
        assertEquals(new BigDecimal("1500.5"), quote.getValue(QuoteType.VOLUME));
        assertEquals(new BigDecimal("152625.85"), quote.getValue(QuoteType.VOLUME_NOTIONAL));
    }

    @Test
    void depthAndTradeUpdatesProduceQuotes() throws Exception {
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), new StubWebSocketApi(),
                new NoOpTickerRegistry());

        engine.onOrderBookUpdate(perpTicker(), json("""
                {"E":1710000000123,"b":[["100.0","1.5"]],"a":[["100.2","2.0"]]}
                """));
        engine.onTradeUpdate(perpTicker(), json("""
                {"T":1710000000123,"p":"99.9","q":"4.0","m":true}
                """));

        ILevel2Quote depth = engine.lastLevel2Quote;
        assertNotNull(depth);
        assertEquals(0, depth.getOrderBook().getBestBidWithSize().getPrice().compareTo(new BigDecimal("100.0")));
        assertEquals(0, depth.getOrderBook().getBestAskWithSize().getPrice().compareTo(new BigDecimal("100.2")));

        OrderFlow orderFlow = engine.lastOrderFlow;
        assertNotNull(orderFlow);
        assertEquals(new BigDecimal("99.9"), orderFlow.getPrice());
        assertEquals(new BigDecimal("4.0"), orderFlow.getSize());
        assertEquals(OrderFlow.Side.SELL, orderFlow.getSide());
    }

    @Test
    void spotDepthUpdatesSupportSpotBidAndAskFieldNames() throws Exception {
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(), new StubWebSocketApi(),
                new NoOpTickerRegistry());

        engine.onOrderBookUpdate(spotTicker(), json("""
                {"E":1710000000123,"bids":[["100.0","1.5"]],"asks":[["100.2","2.0"]]}
                """));

        ILevel2Quote depth = engine.lastLevel2Quote;
        assertNotNull(depth);
        assertEquals(0, depth.getOrderBook().getBestBidWithSize().getPrice().compareTo(new BigDecimal("100.0")));
        assertEquals(0, depth.getOrderBook().getBestAskWithSize().getPrice().compareTo(new BigDecimal("100.2")));
    }

    private static Ticker perpTicker() {
        return new Ticker("BTCUSDT")
                .setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setMinimumTickSize(new BigDecimal("0.1"))
                .setFundingRateInterval(8);
    }

    private static Ticker spotTicker() {
        return new Ticker("BNBUSDT")
                .setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.CRYPTO_SPOT)
                .setMinimumTickSize(new BigDecimal("0.01"))
                .setFundingRateInterval(0);
    }

    private static JsonNode json(String value) throws Exception {
        return OBJECT_MAPPER.readTree(value);
    }

    private static final class CapturingQuoteEngine extends AsterQuoteEngine {
        private ILevel1Quote lastLevel1Quote;
        private ILevel2Quote lastLevel2Quote;
        private OrderFlow lastOrderFlow;

        private CapturingQuoteEngine(IAsterRestApi restApi, IAsterWebSocketApi webSocketApi, ITickerRegistry tickerRegistry) {
            super(restApi, webSocketApi, tickerRegistry);
        }

        @Override
        public void fireLevel1Quote(com.fueledbychai.marketdata.ILevel1Quote quote) {
            lastLevel1Quote = quote;
        }

        @Override
        public void fireMarketDepthQuote(com.fueledbychai.marketdata.ILevel2Quote quote) {
            lastLevel2Quote = quote;
        }

        @Override
        public void fireOrderFlow(OrderFlow orderFlow) {
            lastOrderFlow = orderFlow;
        }
    }

    private static final class StubRestApi implements IAsterRestApi {

        @Override
        public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
            return new InstrumentDescriptor[0];
        }

        @Override
        public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
            return null;
        }

        @Override
        public boolean isPublicApiOnly() {
            return true;
        }

        @Override
        public Date getServerTime() {
            return new Date(1710000000123L);
        }

        @Override
        public String startUserDataStream() {
            return "";
        }

        @Override
        public void keepAliveUserDataStream(String listenKey) {
        }

        @Override
        public void closeUserDataStream(String listenKey) {
        }

        @Override
        public JsonNode placeOrder(java.util.Map<String, String> params) {
            return null;
        }

        @Override
        public JsonNode cancelOrder(String symbol, String orderId, String origClientOrderId) {
            return null;
        }

        @Override
        public JsonNode cancelAllOpenOrders(String symbol) {
            return null;
        }

        @Override
        public JsonNode queryOrder(String symbol, String orderId, String origClientOrderId) {
            return null;
        }

        @Override
        public JsonNode getOpenOrders(String symbol) {
            return null;
        }

        @Override
        public JsonNode getPositionRisk(String symbol) {
            return null;
        }

        @Override
        public JsonNode getAccountInformation() {
            return null;
        }

        @Override
        public JsonNode getBookTicker(String symbol) {
            return null;
        }
    }

    private static final class StubWebSocketApi implements IAsterWebSocketApi {
        private final AtomicInteger bookTickerSubscriptions = new AtomicInteger();
        private final AtomicInteger symbolTickerSubscriptions = new AtomicInteger();
        private final AtomicInteger markPriceSubscriptions = new AtomicInteger();
        private final AtomicInteger disconnectCount = new AtomicInteger();

        @Override
        public void connect() {
        }

        @Override
        public void connectOrderEntryWebSocket() {
        }

        @Override
        public AsterWebSocketClient subscribeBookTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
            bookTickerSubscriptions.incrementAndGet();
            return null;
        }

        @Override
        public AsterWebSocketClient subscribeSymbolTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
            symbolTickerSubscriptions.incrementAndGet();
            return null;
        }

        @Override
        public AsterWebSocketClient subscribePartialDepth(Ticker ticker, int depth,
                IWebSocketEventListener<JsonNode> listener) {
            return null;
        }

        @Override
        public AsterWebSocketClient subscribeAggTrades(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
            return null;
        }

        @Override
        public AsterWebSocketClient subscribeMarkPrice(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
            markPriceSubscriptions.incrementAndGet();
            return null;
        }

        @Override
        public AsterWebSocketClient subscribeUserData(String listenKey, IWebSocketEventListener<JsonNode> listener) {
            return null;
        }

        @Override
        public void disconnectAll() {
            disconnectCount.incrementAndGet();
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
