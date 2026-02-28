package com.fueledbychai.marketdata.lighter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.ws.model.LighterMarketStats;
import com.fueledbychai.lighter.common.api.ws.model.LighterMarketStatsUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrderBookLevel;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrderBookUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;
import com.fueledbychai.lighter.common.api.ws.model.LighterTradesUpdate;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.ILevel2Quote;
import com.fueledbychai.marketdata.IOrderBook;
import com.fueledbychai.marketdata.Level1QuoteListener;
import com.fueledbychai.marketdata.Level2QuoteListener;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;

@ExtendWith(MockitoExtension.class)
public class LighterQuoteEngineTest {

    @Mock
    private ILighterWebSocketApi webSocketApi;

    @Mock
    private ITickerRegistry tickerRegistry;

    @Mock
    private Level1QuoteListener level1Listener;

    @Mock
    private Level2QuoteListener level2Listener;

    @Mock
    private OrderFlowListener orderFlowListener;

    @Test
    void startStopEngine() {
        LighterQuoteEngine engine = new LighterQuoteEngine(webSocketApi, tickerRegistry);

        engine.startEngine();
        assertTrue(engine.started());
        assertTrue(engine.isConnected());

        engine.stopEngine();
        assertFalse(engine.started());
        assertFalse(engine.isConnected());
        verify(webSocketApi).disconnectAll();
    }

    @Test
    void subscribeLevel1SubscribesMarketStatsAndOrderBookOnlyOncePerMarket() {
        LighterQuoteEngine engine = new LighterQuoteEngine(webSocketApi, tickerRegistry);
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES);

        engine.subscribeLevel1(ticker, level1Listener);
        engine.subscribeLevel1(ticker, quote -> {
        });

        verify(webSocketApi, times(1)).subscribeMarketStats(eq(1), any());
        verify(webSocketApi, times(1)).subscribeOrderBook(eq(1), any());
    }

    @Test
    void subscribeMarketDepthSubscribesOrderBookOnlyOncePerMarket() {
        LighterQuoteEngine engine = new LighterQuoteEngine(webSocketApi, tickerRegistry);
        Ticker ticker = createTicker("ETH/USDC", "2048", InstrumentType.CRYPTO_SPOT);

        engine.subscribeMarketDepth(ticker, level2Listener);
        engine.subscribeMarketDepth(ticker, quote -> {
        });

        verify(webSocketApi, times(1)).subscribeOrderBook(eq(2048), any());
    }

    @Test
    void subscribeOrderFlowSubscribesTradesOnlyOncePerMarket() {
        LighterQuoteEngine engine = new LighterQuoteEngine(webSocketApi, tickerRegistry);
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES);

        engine.subscribeOrderFlow(ticker, orderFlowListener);
        engine.subscribeOrderFlow(ticker, orderFlow -> {
        });

        verify(webSocketApi, times(1)).subscribeTrades(eq(1), any());
    }

    @Test
    void subscribeLevel1AcceptsZeroMarketIdFromTicker() {
        LighterQuoteEngine engine = new LighterQuoteEngine(webSocketApi, tickerRegistry);
        Ticker ticker = createTicker("ETH/USDC", "0", InstrumentType.CRYPTO_SPOT);

        engine.subscribeLevel1(ticker, level1Listener);

        verify(webSocketApi).subscribeMarketStats(eq(0), any());
        verify(webSocketApi).subscribeOrderBook(eq(0), any());
    }

    @Test
    void subscribeLevel1ResolvesMarketIdFromTickerRegistry() {
        LighterQuoteEngine engine = new LighterQuoteEngine(webSocketApi, tickerRegistry);
        Ticker inputTicker = createTicker("BTC/USDC", null, InstrumentType.PERPETUAL_FUTURES);
        Ticker canonicalTicker = createTicker("BTC", "7", InstrumentType.PERPETUAL_FUTURES)
                .setMinimumTickSize(new BigDecimal("0.1"))
                .setOrderSizeIncrement(new BigDecimal("0.001"));

        when(tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC/USDC")).thenReturn(null);
        when(tickerRegistry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, "BTC/USDC"))
                .thenReturn(canonicalTicker);

        engine.subscribeLevel1(inputTicker, level1Listener);

        verify(webSocketApi).subscribeMarketStats(eq(7), any());
        assertEquals("7", inputTicker.getId());
        assertEquals(new BigDecimal("0.1"), inputTicker.getMinimumTickSize());
    }

    @Test
    void subscribeLevel1ResolvesZeroMarketIdFromTickerRegistry() {
        LighterQuoteEngine engine = new LighterQuoteEngine(webSocketApi, tickerRegistry);
        Ticker inputTicker = createTicker("ETH/USDC", null, InstrumentType.CRYPTO_SPOT);
        Ticker canonicalTicker = createTicker("ETH/USDC", "0", InstrumentType.CRYPTO_SPOT)
                .setMinimumTickSize(new BigDecimal("0.01"))
                .setOrderSizeIncrement(new BigDecimal("0.001"));

        when(tickerRegistry.lookupByBrokerSymbol(InstrumentType.CRYPTO_SPOT, "ETH/USDC")).thenReturn(canonicalTicker);

        engine.subscribeLevel1(inputTicker, level1Listener);

        verify(webSocketApi).subscribeMarketStats(eq(0), any());
        verify(webSocketApi).subscribeOrderBook(eq(0), any());
        assertEquals("0", inputTicker.getId());
        assertEquals(new BigDecimal("0.01"), inputTicker.getMinimumTickSize());
    }

    @Test
    void handleMarketStatsUpdateFiresLevel1Quote() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));
        AtomicReference<ILevel1Quote> capturedQuote = new AtomicReference<>();
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES).setFundingRateInterval(8);

        doAnswer(invocation -> {
            capturedQuote.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireLevel1Quote(any(ILevel1Quote.class));

        engine.subscribeLevel1(ticker, level1Listener);

        LighterMarketStats stats = new LighterMarketStats();
        stats.setMarketId(1);
        stats.setMarkPrice(new BigDecimal("101.25"));
        stats.setLastPrice(new BigDecimal("101.00"));
        stats.setIndexPrice(new BigDecimal("100.95"));
        stats.setDailyBaseVolume(new BigDecimal("15.5"));
        stats.setDailyQuoteVolume(new BigDecimal("1560.00"));
        stats.setOpenInterest(new BigDecimal("200"));
        stats.setCurrentFundingRate(new BigDecimal("0.0008"));
        stats.setLastFundingRate(new BigDecimal("0.0004"));

        engine.handleMarketStatsUpdate(new LighterMarketStatsUpdate("market_stats/1", Map.of("1", stats)));

        ILevel1Quote quote = capturedQuote.get();
        assertNotNull(quote);
        assertEquals(ticker, quote.getTicker());
        assertEquals(new BigDecimal("101.25"), quote.getValue(QuoteType.MARK_PRICE));
        assertEquals(new BigDecimal("101.00"), quote.getValue(QuoteType.LAST));
        assertEquals(new BigDecimal("100.95"), quote.getValue(QuoteType.UNDERLYING_PRICE));
        assertEquals(new BigDecimal("15.5"), quote.getValue(QuoteType.VOLUME));
        assertEquals(new BigDecimal("1560.00"), quote.getValue(QuoteType.VOLUME_NOTIONAL));
        assertEquals(new BigDecimal("200"), quote.getValue(QuoteType.OPEN_INTEREST));
        assertEquals(new BigDecimal("20250.00"), quote.getValue(QuoteType.OPEN_INTEREST_NOTIONAL));
        assertEquals(0, quote.getValue(QuoteType.FUNDING_RATE_HOURLY_BPS).compareTo(new BigDecimal("0.08")));
        assertEquals(0, quote.getValue(QuoteType.FUNDING_RATE_APR).compareTo(new BigDecimal("7.008")));
    }

    @Test
    void handleMarketStatsUpdateDoesNotUseLastFundingRateWhenCurrentMissing() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));
        AtomicReference<ILevel1Quote> capturedQuote = new AtomicReference<>();
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES).setFundingRateInterval(8);

        doAnswer(invocation -> {
            capturedQuote.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireLevel1Quote(any(ILevel1Quote.class));

        engine.subscribeLevel1(ticker, level1Listener);

        LighterMarketStats stats = new LighterMarketStats();
        stats.setMarketId(1);
        stats.setMarkPrice(new BigDecimal("101.25"));
        stats.setLastFundingRate(new BigDecimal("0.0004"));

        engine.handleMarketStatsUpdate(new LighterMarketStatsUpdate("market_stats/1", Map.of("1", stats)));

        ILevel1Quote quote = capturedQuote.get();
        assertNotNull(quote);
        assertEquals(new BigDecimal("101.25"), quote.getValue(QuoteType.MARK_PRICE));
        assertFalse(quote.containsType(QuoteType.FUNDING_RATE_HOURLY_BPS));
        assertFalse(quote.containsType(QuoteType.FUNDING_RATE_APR));
    }

    @Test
    void handleOrderBookUpdateFiresLevel1AndLevel2Quotes() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));
        AtomicReference<ILevel1Quote> capturedLevel1Quote = new AtomicReference<>();
        AtomicReference<ILevel2Quote> capturedLevel2Quote = new AtomicReference<>();
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES);

        doAnswer(invocation -> {
            capturedLevel1Quote.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireLevel1Quote(any(ILevel1Quote.class));
        doAnswer(invocation -> {
            capturedLevel2Quote.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireMarketDepthQuote(any(ILevel2Quote.class));

        engine.subscribeLevel1(ticker, level1Listener);
        engine.subscribeMarketDepth(ticker, level2Listener);

        LighterOrderBookUpdate update = new LighterOrderBookUpdate(
                "order_book/1",
                1,
                200,
                List.of(new LighterOrderBookLevel(new BigDecimal("101.00"), new BigDecimal("2.50"))),
                List.of(new LighterOrderBookLevel(new BigDecimal("100.50"), new BigDecimal("3.00"))),
                10L,
                12L,
                9L,
                1700000000000L,
                "update/order_book");

        engine.handleOrderBookUpdate(update);

        ILevel1Quote level1Quote = capturedLevel1Quote.get();
        assertNotNull(level1Quote);
        assertEquals(new BigDecimal("100.50"), level1Quote.getValue(QuoteType.BID));
        assertEquals(new BigDecimal("3.0"), level1Quote.getValue(QuoteType.BID_SIZE));
        assertEquals(new BigDecimal("101.00"), level1Quote.getValue(QuoteType.ASK));
        assertEquals(new BigDecimal("2.5"), level1Quote.getValue(QuoteType.ASK_SIZE));

        ILevel2Quote level2Quote = capturedLevel2Quote.get();
        assertNotNull(level2Quote);
        IOrderBook.BidSizePair bestBid = level2Quote.getOrderBook().getBestBidWithSize();
        IOrderBook.BidSizePair bestAsk = level2Quote.getOrderBook().getBestAskWithSize();
        assertEquals(0, bestBid.getPrice().compareTo(new BigDecimal("100.50")));
        assertEquals(3.0d, bestBid.getSize(), 0.00001d);
        assertEquals(0, bestAsk.getPrice().compareTo(new BigDecimal("101.00")));
        assertEquals(2.5d, bestAsk.getSize(), 0.00001d);
    }

    @Test
    void handleOrderBookUpdateAppliesDeltaAfterSnapshot() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));
        AtomicReference<ILevel2Quote> capturedLevel2Quote = new AtomicReference<>();
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES);

        doAnswer(invocation -> {
            capturedLevel2Quote.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireMarketDepthQuote(any(ILevel2Quote.class));

        engine.subscribeMarketDepth(ticker, level2Listener);

        LighterOrderBookUpdate snapshot = new LighterOrderBookUpdate(
                "order_book/1",
                1,
                200,
                List.of(new LighterOrderBookLevel(new BigDecimal("101.00"), new BigDecimal("2.50"))),
                List.of(new LighterOrderBookLevel(new BigDecimal("100.50"), new BigDecimal("3.00"))),
                10L,
                100L,
                99L,
                1700000000000L,
                "update/order_book");
        LighterOrderBookUpdate delta = new LighterOrderBookUpdate(
                "order_book/1",
                1,
                200,
                List.of(),
                List.of(new LighterOrderBookLevel(new BigDecimal("100.75"), new BigDecimal("1.25"))),
                11L,
                101L,
                100L,
                1700000001000L,
                "update/order_book");

        engine.handleOrderBookUpdate(snapshot);
        engine.handleOrderBookUpdate(delta);

        ILevel2Quote level2Quote = capturedLevel2Quote.get();
        assertNotNull(level2Quote);
        IOrderBook.BidSizePair bestBid = level2Quote.getOrderBook().getBestBidWithSize();
        IOrderBook.BidSizePair bestAsk = level2Quote.getOrderBook().getBestAskWithSize();
        assertEquals(0, bestBid.getPrice().compareTo(new BigDecimal("100.75")));
        assertEquals(1.25d, bestBid.getSize(), 0.00001d);
        assertEquals(0, bestAsk.getPrice().compareTo(new BigDecimal("101.00")));
        assertEquals(2.5d, bestAsk.getSize(), 0.00001d);
    }

    @Test
    void handleOrderBookUpdateRemovesLevelWhenSizeIsZero() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));
        AtomicReference<ILevel2Quote> capturedLevel2Quote = new AtomicReference<>();
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES);

        doAnswer(invocation -> {
            capturedLevel2Quote.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireMarketDepthQuote(any(ILevel2Quote.class));

        engine.subscribeMarketDepth(ticker, level2Listener);

        LighterOrderBookUpdate snapshot = new LighterOrderBookUpdate(
                "order_book/1",
                1,
                200,
                List.of(new LighterOrderBookLevel(new BigDecimal("101.00"), new BigDecimal("2.50"))),
                List.of(new LighterOrderBookLevel(new BigDecimal("100.50"), new BigDecimal("3.00"))),
                10L,
                100L,
                99L,
                1700000000000L,
                "update/order_book");
        LighterOrderBookUpdate delta = new LighterOrderBookUpdate(
                "order_book/1",
                1,
                200,
                List.of(new LighterOrderBookLevel(new BigDecimal("101.00"), BigDecimal.ZERO)),
                List.of(),
                11L,
                101L,
                100L,
                1700000001000L,
                "update/order_book");

        engine.handleOrderBookUpdate(snapshot);
        engine.handleOrderBookUpdate(delta);

        ILevel2Quote level2Quote = capturedLevel2Quote.get();
        assertNotNull(level2Quote);
        IOrderBook.BidSizePair bestBid = level2Quote.getOrderBook().getBestBidWithSize();
        IOrderBook.BidSizePair bestAsk = level2Quote.getOrderBook().getBestAskWithSize();
        assertEquals(0, bestBid.getPrice().compareTo(new BigDecimal("100.50")));
        assertEquals(3.0d, bestBid.getSize(), 0.00001d);
        assertEquals(0, bestAsk.getPrice().compareTo(BigDecimal.ZERO));
    }

    @Test
    void handleOrderBookUpdateResetsStateOnNonceGap() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));
        AtomicReference<ILevel2Quote> capturedLevel2Quote = new AtomicReference<>();
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES);

        doAnswer(invocation -> {
            capturedLevel2Quote.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireMarketDepthQuote(any(ILevel2Quote.class));

        engine.subscribeMarketDepth(ticker, level2Listener);

        LighterOrderBookUpdate snapshot = new LighterOrderBookUpdate(
                "order_book/1",
                1,
                200,
                List.of(new LighterOrderBookLevel(new BigDecimal("101.00"), new BigDecimal("2.50"))),
                List.of(new LighterOrderBookLevel(new BigDecimal("100.50"), new BigDecimal("3.00"))),
                10L,
                100L,
                99L,
                1700000000000L,
                "update/order_book");
        LighterOrderBookUpdate nonContiguous = new LighterOrderBookUpdate(
                "order_book/1",
                1,
                200,
                List.of(new LighterOrderBookLevel(new BigDecimal("102.00"), new BigDecimal("1.00"))),
                List.of(new LighterOrderBookLevel(new BigDecimal("99.00"), new BigDecimal("2.00"))),
                11L,
                150L,
                125L,
                1700000001000L,
                "update/order_book");

        engine.handleOrderBookUpdate(snapshot);
        engine.handleOrderBookUpdate(nonContiguous);

        ILevel2Quote level2Quote = capturedLevel2Quote.get();
        assertNotNull(level2Quote);
        IOrderBook.BidSizePair bestBid = level2Quote.getOrderBook().getBestBidWithSize();
        IOrderBook.BidSizePair bestAsk = level2Quote.getOrderBook().getBestAskWithSize();
        assertEquals(0, bestBid.getPrice().compareTo(new BigDecimal("99.00")));
        assertEquals(2.0d, bestBid.getSize(), 0.00001d);
        assertEquals(0, bestAsk.getPrice().compareTo(new BigDecimal("102.00")));
        assertEquals(1.0d, bestAsk.getSize(), 0.00001d);
    }

    @Test
    void handleOrderBookUpdateSkipsStaleNonceUpdate() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));
        AtomicReference<ILevel2Quote> capturedLevel2Quote = new AtomicReference<>();
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES);

        doAnswer(invocation -> {
            capturedLevel2Quote.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireMarketDepthQuote(any(ILevel2Quote.class));

        engine.subscribeMarketDepth(ticker, level2Listener);

        LighterOrderBookUpdate snapshot = new LighterOrderBookUpdate(
                "order_book/1",
                1,
                200,
                List.of(new LighterOrderBookLevel(new BigDecimal("101.00"), new BigDecimal("2.50"))),
                List.of(new LighterOrderBookLevel(new BigDecimal("100.50"), new BigDecimal("3.00"))),
                10L,
                100L,
                99L,
                1700000000000L,
                "update/order_book");
        LighterOrderBookUpdate stale = new LighterOrderBookUpdate(
                "order_book/1",
                1,
                200,
                List.of(new LighterOrderBookLevel(new BigDecimal("110.00"), new BigDecimal("1.00"))),
                List.of(new LighterOrderBookLevel(new BigDecimal("90.00"), new BigDecimal("2.00"))),
                11L,
                100L,
                100L,
                1700000001000L,
                "update/order_book");

        engine.handleOrderBookUpdate(snapshot);
        engine.handleOrderBookUpdate(stale);

        ILevel2Quote level2Quote = capturedLevel2Quote.get();
        assertNotNull(level2Quote);
        IOrderBook.BidSizePair bestBid = level2Quote.getOrderBook().getBestBidWithSize();
        IOrderBook.BidSizePair bestAsk = level2Quote.getOrderBook().getBestAskWithSize();
        assertEquals(0, bestBid.getPrice().compareTo(new BigDecimal("100.50")));
        assertEquals(0, bestAsk.getPrice().compareTo(new BigDecimal("101.00")));
        verify(engine, times(1)).fireMarketDepthQuote(any(ILevel2Quote.class));
    }

    @Test
    void handleOrderBookUpdateSkipsUnknownMarket() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));

        LighterOrderBookUpdate update = new LighterOrderBookUpdate(
                "order_book/999",
                999,
                200,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                "update/order_book");

        engine.handleOrderBookUpdate(update);
        verify(engine, never()).fireMarketDepthQuote(any(ILevel2Quote.class));
    }

    @Test
    void handleTradesUpdateFiresOrderFlow() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));
        AtomicReference<OrderFlow> capturedOrderFlow = new AtomicReference<>();
        Ticker ticker = createTicker("BTC", "1", InstrumentType.PERPETUAL_FUTURES);

        doAnswer(invocation -> {
            capturedOrderFlow.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireOrderFlow(any(OrderFlow.class));

        engine.subscribeOrderFlow(ticker, orderFlowListener);

        LighterTrade trade = new LighterTrade();
        trade.setMarketId(1);
        trade.setType("sell");
        trade.setPrice(new BigDecimal("101.25"));
        trade.setSize(new BigDecimal("0.010"));
        trade.setTimestamp(1700000000000L);
        LighterTradesUpdate update = new LighterTradesUpdate("trade/1", "update/trade", List.of(trade));

        engine.handleTradesUpdate(update);

        OrderFlow orderFlow = capturedOrderFlow.get();
        assertNotNull(orderFlow);
        assertEquals(ticker, orderFlow.getTicker());
        assertEquals(OrderFlow.Side.SELL, orderFlow.getSide());
        assertEquals(new BigDecimal("101.25"), orderFlow.getPrice());
        assertEquals(new BigDecimal("0.010"), orderFlow.getSize());
    }

    @Test
    void handleTradesUpdateSkipsUnknownMarket() {
        LighterQuoteEngine engine = spy(new LighterQuoteEngine(webSocketApi, tickerRegistry));

        LighterTrade trade = new LighterTrade();
        trade.setMarketId(999);
        trade.setType("buy");
        trade.setPrice(new BigDecimal("101.25"));
        trade.setSize(new BigDecimal("0.010"));
        LighterTradesUpdate update = new LighterTradesUpdate("trade/999", "update/trade", List.of(trade));

        engine.handleTradesUpdate(update);
        verify(engine, never()).fireOrderFlow(any(OrderFlow.class));
    }

    private Ticker createTicker(String symbol, String id, InstrumentType instrumentType) {
        Ticker ticker = new Ticker(symbol)
                .setExchange(Exchange.LIGHTER)
                .setInstrumentType(instrumentType)
                .setMinimumTickSize(new BigDecimal("0.01"));
        if (id != null) {
            ticker.setId(id);
        }
        return ticker;
    }
}
