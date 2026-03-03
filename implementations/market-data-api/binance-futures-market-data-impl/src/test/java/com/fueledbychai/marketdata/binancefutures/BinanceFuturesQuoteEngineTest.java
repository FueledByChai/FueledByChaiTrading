package com.fueledbychai.marketdata.binancefutures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesRestApi;
import com.fueledbychai.binancefutures.common.api.IBinanceFuturesWebSocketApi;
import com.fueledbychai.binancefutures.common.api.ws.BinanceFuturesWebSocketClient;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.websocket.IWebSocketEventListener;

class BinanceFuturesQuoteEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void startAndStopEngineToggleConnectionState() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        CapturingQuoteEngine engine = newEngine(webSocketApi);

        engine.startEngine();
        assertTrue(engine.started());
        assertTrue(engine.isConnected());

        engine.stopEngine();
        assertFalse(engine.started());
        assertFalse(engine.isConnected());
        assertEquals(1, webSocketApi.disconnectCount.get());
    }

    @Test
    void subscribeLevel1StartsBookTickerAndMarkPriceOnce() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        CapturingQuoteEngine engine = newEngine(webSocketApi);
        Ticker ticker = ticker();
        Level1QuoteListener listener = quote -> {
        };

        engine.subscribeLevel1(ticker, listener);
        engine.subscribeLevel1(ticker, listener);

        assertEquals(1, webSocketApi.bookTickerSubscriptions.get());
        assertEquals(1, webSocketApi.symbolTickerSubscriptions.get());
        assertEquals(1, webSocketApi.markPriceSubscriptions.get());
    }

    @Test
    void subscribeLevel1ForOptionsUsesBookTickerAndOptionTicker() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        CapturingQuoteEngine engine = newEngine(webSocketApi);

        engine.subscribeLevel1(optionTicker(), quote -> {
        });

        assertEquals(1, webSocketApi.bookTickerSubscriptions.get());
        assertEquals(1, webSocketApi.symbolTickerSubscriptions.get());
        assertEquals(0, webSocketApi.markPriceSubscriptions.get());
    }

    @Test
    void subscribeLevel1RegistersListenerBeforeStartingStreams() throws Exception {
        ImmediateMessageWebSocketApi webSocketApi = new ImmediateMessageWebSocketApi(
                json("{\"E\":1710000000123,\"bo\":\"1.25\",\"bq\":\"2.0\",\"ao\":\"1.35\",\"aq\":\"3.0\",\"V\":\"11\",\"A\":\"14.85\",\"c\":\"1.30\",\"mp\":\"1.28\"}"));
        ForwardingQuoteEngine engine = newForwardingEngine(webSocketApi);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ILevel1Quote> quoteRef = new AtomicReference<>();

        engine.subscribeLevel1(optionTicker(), quote -> {
            quoteRef.set(quote);
            latch.countDown();
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(quoteRef.get());
        assertEquals(new BigDecimal("1.25"), quoteRef.get().getValue(QuoteType.BID));
    }

    @Test
    void subscribeMarketDepthAndTradesStartEachStreamOnce() {
        StubWebSocketApi webSocketApi = new StubWebSocketApi();
        CapturingQuoteEngine engine = newEngine(webSocketApi);
        Ticker ticker = ticker();

        engine.subscribeMarketDepth(ticker, new NoOpLevel2Listener());
        engine.subscribeMarketDepth(ticker, new NoOpLevel2Listener());
        engine.subscribeOrderFlow(ticker, new NoOpOrderFlowListener());
        engine.subscribeOrderFlow(ticker, new NoOpOrderFlowListener());

        assertEquals(1, webSocketApi.depthSubscriptions.get());
        assertEquals(1, webSocketApi.tradeSubscriptions.get());
    }

    @Test
    void bookTickerUpdatesProduceLevel1Quote() throws Exception {
        CapturingQuoteEngine engine = newEngine(new StubWebSocketApi());
        engine.onBookTickerUpdate(ticker(),
                json("{\"E\":1710000000123,\"b\":\"100.1\",\"B\":\"2.5\",\"a\":\"100.2\",\"A\":\"3.0\"}"));

        ILevel1Quote quote = engine.lastLevel1Quote;
        assertNotNull(quote);
        assertEquals(new BigDecimal("100.1"), quote.getValue(QuoteType.BID));
        assertEquals(new BigDecimal("2.5"), quote.getValue(QuoteType.BID_SIZE));
        assertEquals(new BigDecimal("100.2"), quote.getValue(QuoteType.ASK));
        assertEquals(new BigDecimal("3.0"), quote.getValue(QuoteType.ASK_SIZE));
    }

    @Test
    void markPriceUpdatesProduceFundingQuotes() throws Exception {
        CapturingQuoteEngine engine = newEngine(new StubWebSocketApi());
        engine.onMarkPriceUpdate(ticker(),
                json("{\"E\":1710000000123,\"p\":\"100.5\",\"i\":\"100.4\",\"r\":\"0.0008\"}"));

        ILevel1Quote quote = engine.lastLevel1Quote;
        assertNotNull(quote);
        assertEquals(new BigDecimal("100.5"), quote.getValue(QuoteType.MARK_PRICE));
        assertEquals(new BigDecimal("100.4"), quote.getValue(QuoteType.UNDERLYING_PRICE));
        assertEquals(1.0d, quote.getValue(QuoteType.FUNDING_RATE_HOURLY_BPS).doubleValue(), 1.0e-9);
        assertEquals(87.6d, quote.getValue(QuoteType.FUNDING_RATE_APR).doubleValue(), 1.0e-9);
    }

    @Test
    void symbolTickerUpdatesProduceVolumeQuotes() throws Exception {
        CapturingQuoteEngine engine = newEngine(new StubWebSocketApi());
        engine.onSymbolTickerUpdate(ticker(),
                json("{\"E\":1710000000123,\"v\":\"123.45\",\"q\":\"67890.12\"}"));

        ILevel1Quote quote = engine.lastLevel1Quote;
        assertNotNull(quote);
        assertEquals(new BigDecimal("123.45"), quote.getValue(QuoteType.VOLUME));
        assertEquals(new BigDecimal("67890.12"), quote.getValue(QuoteType.VOLUME_NOTIONAL));
    }

    @Test
    void optionTickerUpdatesProduceLevel1Quote() throws Exception {
        CapturingQuoteEngine engine = newEngine(new StubWebSocketApi());
        engine.onSymbolTickerUpdate(optionTicker(),
                json("{\"E\":1710000000123,\"bo\":\"1.25\",\"bq\":\"2.0\",\"ao\":\"1.35\",\"aq\":\"3.0\",\"V\":\"11\",\"A\":\"14.85\",\"c\":\"1.32\",\"Q\":\"0.5\",\"mp\":\"1.30\"}"));

        ILevel1Quote quote = engine.lastLevel1Quote;
        assertNotNull(quote);
        assertEquals(new BigDecimal("1.25"), quote.getValue(QuoteType.BID));
        assertEquals(new BigDecimal("2.0"), quote.getValue(QuoteType.BID_SIZE));
        assertEquals(new BigDecimal("1.35"), quote.getValue(QuoteType.ASK));
        assertEquals(new BigDecimal("3.0"), quote.getValue(QuoteType.ASK_SIZE));
        assertEquals(new BigDecimal("1.32"), quote.getValue(QuoteType.LAST));
        assertEquals(new BigDecimal("0.5"), quote.getValue(QuoteType.LAST_SIZE));
        assertEquals(new BigDecimal("11"), quote.getValue(QuoteType.VOLUME));
        assertEquals(new BigDecimal("14.85"), quote.getValue(QuoteType.VOLUME_NOTIONAL));
        assertEquals(new BigDecimal("1.30"), quote.getValue(QuoteType.MARK_PRICE));
    }

    @Test
    void depthUpdatesProduceLevel2Quote() throws Exception {
        CapturingQuoteEngine engine = newEngine(new StubWebSocketApi());
        engine.onOrderBookUpdate(ticker(),
                json("{\"E\":1710000000123,\"b\":[[\"100.0\",\"1.5\"]],\"a\":[[\"100.2\",\"2.0\"]]}"));

        ILevel2Quote quote = engine.lastLevel2Quote;
        assertNotNull(quote);
        assertEquals(0, quote.getOrderBook().getBestBidWithSize().getPrice().compareTo(new BigDecimal("100.0")));
        assertEquals(1.5d, quote.getOrderBook().getBestBidWithSize().getSize(), 1.0e-9);
        assertEquals(0, quote.getOrderBook().getBestAskWithSize().getPrice().compareTo(new BigDecimal("100.2")));
        assertEquals(2.0d, quote.getOrderBook().getBestAskWithSize().getSize(), 1.0e-9);
    }

    @Test
    void tradeUpdatesProduceOrderFlow() throws Exception {
        CapturingQuoteEngine engine = newEngine(new StubWebSocketApi());
        engine.onTradeUpdate(ticker(), json("{\"T\":1710000000123,\"p\":\"99.9\",\"q\":\"4.0\",\"m\":true}"));

        OrderFlow orderFlow = engine.lastOrderFlow;
        assertNotNull(orderFlow);
        assertEquals(new BigDecimal("99.9"), orderFlow.getPrice());
        assertEquals(new BigDecimal("4.0"), orderFlow.getSize());
        assertEquals(OrderFlow.Side.SELL, orderFlow.getSide());
        assertNotNull(orderFlow.getTimestamp());
    }

    @Test
    void optionTradeUpdatesUseSignedDirection() throws Exception {
        CapturingQuoteEngine engine = newEngine(new StubWebSocketApi());
        engine.onTradeUpdate(optionTicker(),
                json("{\"T\":1710000000123,\"p\":\"1.20\",\"q\":\"3.0\",\"S\":-1}"));

        OrderFlow orderFlow = engine.lastOrderFlow;
        assertNotNull(orderFlow);
        assertEquals(new BigDecimal("1.20"), orderFlow.getPrice());
        assertEquals(new BigDecimal("3.0"), orderFlow.getSize());
        assertEquals(OrderFlow.Side.SELL, orderFlow.getSide());
    }

    @Test
    void getServerTimeDelegatesToRestApi() {
        Date expected = new Date(1710000000123L);
        CapturingQuoteEngine engine = new CapturingQuoteEngine(new StubRestApi(expected), new StubWebSocketApi(),
                new NoOpTickerRegistry());
        assertEquals(expected, engine.getServerTime());
    }

    private static CapturingQuoteEngine newEngine(StubWebSocketApi webSocketApi) {
        return new CapturingQuoteEngine(new StubRestApi(new Date(1710000000123L)), webSocketApi,
                new NoOpTickerRegistry());
    }

    private static ForwardingQuoteEngine newForwardingEngine(StubWebSocketApi webSocketApi) {
        return new ForwardingQuoteEngine(new StubRestApi(new Date(1710000000123L)), webSocketApi,
                new NoOpTickerRegistry());
    }

    private static Ticker ticker() {
        return new Ticker("BTCUSDT")
                .setExchange(Exchange.BINANCE_FUTURES)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .setMinimumTickSize(new BigDecimal("0.1"))
                .setFundingRateInterval(8);
    }

    private static Ticker optionTicker() {
        return new Ticker("BTC-260627-50000-C")
                .setExchange(Exchange.BINANCE_FUTURES)
                .setInstrumentType(InstrumentType.OPTION)
                .setMinimumTickSize(new BigDecimal("0.0001"));
    }

    private static JsonNode json(String value) throws Exception {
        return OBJECT_MAPPER.readTree(value);
    }

    private static final class CapturingQuoteEngine extends BinanceFuturesQuoteEngine {
        private ILevel1Quote lastLevel1Quote;
        private ILevel2Quote lastLevel2Quote;
        private OrderFlow lastOrderFlow;

        private CapturingQuoteEngine(IBinanceFuturesRestApi restApi, IBinanceFuturesWebSocketApi webSocketApi,
                ITickerRegistry tickerRegistry) {
            super(restApi, webSocketApi, tickerRegistry);
        }

        @Override
        public void fireLevel1Quote(ILevel1Quote quote) {
            this.lastLevel1Quote = quote;
        }

        @Override
        public void fireMarketDepthQuote(ILevel2Quote quote) {
            this.lastLevel2Quote = quote;
        }

        @Override
        public void fireOrderFlow(OrderFlow orderFlow) {
            this.lastOrderFlow = orderFlow;
        }
    }

    private static final class ForwardingQuoteEngine extends BinanceFuturesQuoteEngine {
        private ForwardingQuoteEngine(IBinanceFuturesRestApi restApi, IBinanceFuturesWebSocketApi webSocketApi,
                ITickerRegistry tickerRegistry) {
            super(restApi, webSocketApi, tickerRegistry);
        }
    }

    private static final class StubRestApi implements IBinanceFuturesRestApi {
        private final Date serverTime;

        private StubRestApi(Date serverTime) {
            this.serverTime = serverTime;
        }

        @Override
        public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
            return new InstrumentDescriptor[0];
        }

        @Override
        public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
            return null;
        }

        @Override
        public Date getServerTime() {
            return serverTime;
        }

        @Override
        public boolean isPublicApiOnly() {
            return true;
        }
    }

    private static class StubWebSocketApi implements IBinanceFuturesWebSocketApi {
        private final AtomicInteger bookTickerSubscriptions = new AtomicInteger();
        private final AtomicInteger symbolTickerSubscriptions = new AtomicInteger();
        private final AtomicInteger markPriceSubscriptions = new AtomicInteger();
        private final AtomicInteger depthSubscriptions = new AtomicInteger();
        private final AtomicInteger tradeSubscriptions = new AtomicInteger();
        private final AtomicInteger disconnectCount = new AtomicInteger();

        @Override
        public void connect() {
        }

        @Override
        public BinanceFuturesWebSocketClient subscribeBookTicker(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
            bookTickerSubscriptions.incrementAndGet();
            return null;
        }

        @Override
        public BinanceFuturesWebSocketClient subscribeSymbolTicker(Ticker ticker,
                IWebSocketEventListener<JsonNode> listener) {
            symbolTickerSubscriptions.incrementAndGet();
            return null;
        }

        @Override
        public BinanceFuturesWebSocketClient subscribePartialDepth(Ticker ticker, int depth,
                IWebSocketEventListener<JsonNode> listener) {
            depthSubscriptions.incrementAndGet();
            return null;
        }

        @Override
        public BinanceFuturesWebSocketClient subscribeAggTrades(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
            tradeSubscriptions.incrementAndGet();
            return null;
        }

        @Override
        public BinanceFuturesWebSocketClient subscribeMarkPrice(Ticker ticker, IWebSocketEventListener<JsonNode> listener) {
            markPriceSubscriptions.incrementAndGet();
            return null;
        }

        @Override
        public void disconnectAll() {
            disconnectCount.incrementAndGet();
        }
    }

    private static final class ImmediateMessageWebSocketApi extends StubWebSocketApi {
        private final JsonNode symbolTickerMessage;

        private ImmediateMessageWebSocketApi(JsonNode symbolTickerMessage) {
            this.symbolTickerMessage = symbolTickerMessage;
        }

        @Override
        public BinanceFuturesWebSocketClient subscribeSymbolTicker(Ticker ticker,
                IWebSocketEventListener<JsonNode> listener) {
            super.subscribeSymbolTicker(ticker, listener);
            if (symbolTickerMessage != null) {
                listener.onWebSocketEvent(symbolTickerMessage);
            }
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
            return null;
        }
    }

    private static final class NoOpLevel2Listener implements Level2QuoteListener {
        @Override
        public void level2QuoteReceived(ILevel2Quote quote) {
        }
    }

    private static final class NoOpOrderFlowListener implements OrderFlowListener {
        @Override
        public void orderflowReceived(OrderFlow orderFlow) {
        }
    }
}
