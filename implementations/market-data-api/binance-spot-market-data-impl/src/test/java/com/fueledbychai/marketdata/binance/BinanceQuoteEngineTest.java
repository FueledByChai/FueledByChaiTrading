package com.fueledbychai.marketdata.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.binance.ws.aggtrade.TradeRecord;
import com.fueledbychai.binance.ws.bookticker.BookTickerRecord;
import com.fueledbychai.binance.ws.partialbook.OrderBookSnapshot;
import com.fueledbychai.binance.ws.partialbook.PriceLevel;
import com.fueledbychai.binance.ws.symbolticker.SymbolTickerRecord;
import com.fueledbychai.data.Ticker;
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
public class BinanceQuoteEngineTest {

    @Mock
    private Level1QuoteListener level1Listener;

    @Mock
    private Level2QuoteListener level2Listener;

    @Mock
    private OrderFlowListener orderFlowListener;

    @Mock
    private ITickerRegistry tickerRegistry;

    @Test
    public void testStartStopEngine() {
        BinanceQuoteEngine engine = new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry);

        engine.startEngine();
        assertTrue(engine.started());
        assertTrue(engine.isConnected());

        engine.stopEngine();
        assertFalse(engine.started());
        assertFalse(engine.isConnected());
    }

    @Test
    public void testSubscribeLevel1StartsBookAndSymbolTickerClients() {
        BinanceQuoteEngine engine = spy(new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry));
        Ticker ticker = new Ticker("BTCUSDT");

        doNothing().when(engine).startBookTickerWSClient(ticker);
        doNothing().when(engine).startSymbolTickerWSClient(ticker);

        engine.subscribeLevel1(ticker, level1Listener);

        verify(engine).startBookTickerWSClient(ticker);
        verify(engine).startSymbolTickerWSClient(ticker);
    }

    @Test
    public void testSubscribeMarketDepthStartsPartialBookClient() {
        BinanceQuoteEngine engine = spy(new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry));
        Ticker ticker = new Ticker("BTCUSDT");

        doNothing().when(engine).startPartialOrderBookClient(ticker);

        engine.subscribeMarketDepth(ticker, level2Listener);

        verify(engine).startPartialOrderBookClient(ticker);
    }

    @Test
    public void testSubscribeOrderFlowStartsTradesClient() {
        BinanceQuoteEngine engine = spy(new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry));
        Ticker ticker = new Ticker("BTCUSDT");

        doNothing().when(engine).startTradesWSClient(ticker);

        engine.subscribeOrderFlow(ticker, orderFlowListener);

        verify(engine).startTradesWSClient(ticker);
    }

    @Test
    public void testOnBboUpdateFiresLevel1Quote() {
        BinanceQuoteEngine engine = spy(new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry));
        AtomicReference<ILevel1Quote> captured = new AtomicReference<>();
        Ticker ticker = new Ticker("BTCUSDT");
        ZonedDateTime timeStamp = ZonedDateTime.now(ZoneId.of("UTC"));

        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireLevel1Quote(any(ILevel1Quote.class));

        engine.onBBOUpdate(ticker, new BigDecimal("100.00"), new BigDecimal("1.50"), new BigDecimal("101.00"),
                new BigDecimal("2.25"), timeStamp);

        ILevel1Quote quote = captured.get();
        assertNotNull(quote);
        assertEquals(ticker, quote.getTicker());
        assertEquals(timeStamp, quote.getTimeStamp());
        assertTrue(quote.containsType(QuoteType.BID));
        assertTrue(quote.containsType(QuoteType.BID_SIZE));
        assertTrue(quote.containsType(QuoteType.ASK));
        assertTrue(quote.containsType(QuoteType.ASK_SIZE));
        assertEquals(new BigDecimal("100.00"), quote.getValue(QuoteType.BID));
        assertEquals(new BigDecimal("1.50"), quote.getValue(QuoteType.BID_SIZE));
        assertEquals(new BigDecimal("101.00"), quote.getValue(QuoteType.ASK));
        assertEquals(new BigDecimal("2.25"), quote.getValue(QuoteType.ASK_SIZE));
    }

    @Test
    public void testOnOrderBookUpdateFiresLevel2Quote() {
        BinanceQuoteEngine engine = spy(new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry));
        AtomicReference<ILevel2Quote> captured = new AtomicReference<>();
        Ticker ticker = new Ticker("BTCUSDT");
        ZonedDateTime timeStamp = ZonedDateTime.now(ZoneId.of("UTC"));

        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireMarketDepthQuote(any(ILevel2Quote.class));

        OrderBookSnapshot snapshot = new OrderBookSnapshot();
        snapshot.setBids(List.of(new PriceLevel("100.00", "1.5")));
        snapshot.setAsks(List.of(new PriceLevel("101.00", "2.0")));

        engine.onOrderBookUpdate(ticker, snapshot, timeStamp);

        ILevel2Quote quote = captured.get();
        assertNotNull(quote);
        assertEquals(ticker, quote.getTicker());
        assertEquals(timeStamp, quote.getTimeStamp());
        assertNotNull(quote.getOrderBook());

        IOrderBook.BidSizePair bestBid = quote.getOrderBook().getBestBidWithSize();
        IOrderBook.BidSizePair bestAsk = quote.getOrderBook().getBestAskWithSize();
        assertEquals(0, bestBid.getPrice().compareTo(new BigDecimal("100.00")));
        assertEquals(0, bestAsk.getPrice().compareTo(new BigDecimal("101.00")));
        assertEquals(1.5d, bestBid.getSize(), 0.0001d);
        assertEquals(2.0d, bestAsk.getSize(), 0.0001d);
    }

    @Test
    public void testOnTradeRecordUpdateFiresOrderFlow() {
        BinanceQuoteEngine engine = spy(new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry));
        AtomicReference<OrderFlow> captured = new AtomicReference<>();
        Ticker ticker = new Ticker("BTCUSDT");

        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireOrderFlow(any(OrderFlow.class));

        TradeRecord tradeRecord = new TradeRecord();
        tradeRecord.setPrice("10.5");
        tradeRecord.setQuantity("2.0");
        tradeRecord.setTradeTime(1710000000L);
        tradeRecord.setBuyerMarketMaker(true);

        engine.onTradeRecordUpdate(ticker, tradeRecord);

        OrderFlow orderFlow = captured.get();
        assertNotNull(orderFlow);
        assertEquals(ticker, orderFlow.getTicker());
        assertEquals(new BigDecimal("10.5"), orderFlow.getPrice());
        assertEquals(new BigDecimal("2.0"), orderFlow.getSize());
        assertEquals(OrderFlow.Side.SELL, orderFlow.getSide());
        assertEquals(ZonedDateTime.ofInstant(Instant.ofEpochMilli(1710000000L), ZoneId.of("UTC")),
                orderFlow.getTimestamp());
    }

    @Test
    public void testResolveSnapshotEventTimeUsesExchangeEventTime() {
        BinanceQuoteEngine engine = new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry);
        OrderBookSnapshot snapshot = new OrderBookSnapshot();
        snapshot.setEventTime(1700000000123L);

        ZonedDateTime timestamp = engine.resolveSnapshotEventTime(snapshot);

        assertEquals(ZonedDateTime.ofInstant(Instant.ofEpochMilli(1700000000123L), ZoneId.of("UTC")), timestamp);
    }

    @Test
    public void testResolveSnapshotEventTimeFallsBackToNowWhenMissing() {
        BinanceQuoteEngine engine = new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry);
        OrderBookSnapshot snapshot = new OrderBookSnapshot();

        ZonedDateTime before = ZonedDateTime.now(ZoneId.of("UTC")).minusSeconds(1);
        ZonedDateTime timestamp = engine.resolveSnapshotEventTime(snapshot);
        ZonedDateTime after = ZonedDateTime.now(ZoneId.of("UTC")).plusSeconds(1);

        assertTrue(!timestamp.isBefore(before));
        assertTrue(!timestamp.isAfter(after));
    }

    @Test
    public void testOnBookTickerUpdateUsesExchangeTimestamp() {
        BinanceQuoteEngine engine = spy(new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry));
        AtomicReference<ILevel1Quote> captured = new AtomicReference<>();
        Ticker ticker = new Ticker("BTCUSDT");

        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireLevel1Quote(any(ILevel1Quote.class));

        BookTickerRecord record = new BookTickerRecord();
        record.setEventTime(1710001234567L);
        record.setBestBidPrice("100.10");
        record.setBestBidQty("1.1");
        record.setBestAskPrice("100.20");
        record.setBestAskQty("2.2");

        engine.onBookTickerUpdate(ticker, record);

        ILevel1Quote quote = captured.get();
        assertNotNull(quote);
        assertEquals(ZonedDateTime.ofInstant(Instant.ofEpochMilli(1710001234567L), ZoneId.of("UTC")),
                quote.getTimeStamp());
        assertEquals(new BigDecimal("100.10"), quote.getValue(QuoteType.BID));
        assertEquals(new BigDecimal("1.1"), quote.getValue(QuoteType.BID_SIZE));
        assertEquals(new BigDecimal("100.20"), quote.getValue(QuoteType.ASK));
        assertEquals(new BigDecimal("2.2"), quote.getValue(QuoteType.ASK_SIZE));
    }

    @Test
    public void testOnSymbolTickerUpdateProducesLastAndVolumeQuotes() {
        BinanceQuoteEngine engine = spy(new BinanceQuoteEngine("wss://example.test/stream", tickerRegistry));
        AtomicReference<ILevel1Quote> captured = new AtomicReference<>();
        Ticker ticker = new Ticker("BTCUSDT");

        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(engine).fireLevel1Quote(any(ILevel1Quote.class));

        SymbolTickerRecord record = new SymbolTickerRecord();
        record.setEventTime(1710001234567L);
        record.setLastPrice("100.15");
        record.setLastQuantity("0.35");
        record.setVolume("250.5");
        record.setVolumeNotional("25123.45");

        engine.onSymbolTickerUpdate(ticker, record);

        ILevel1Quote quote = captured.get();
        assertNotNull(quote);
        assertEquals(ZonedDateTime.ofInstant(Instant.ofEpochMilli(1710001234567L), ZoneId.of("UTC")),
                quote.getTimeStamp());
        assertEquals(new BigDecimal("100.15"), quote.getValue(QuoteType.LAST));
        assertEquals(new BigDecimal("0.35"), quote.getValue(QuoteType.LAST_SIZE));
        assertEquals(new BigDecimal("250.5"), quote.getValue(QuoteType.VOLUME));
        assertEquals(new BigDecimal("25123.45"), quote.getValue(QuoteType.VOLUME_NOTIONAL));
    }
}
