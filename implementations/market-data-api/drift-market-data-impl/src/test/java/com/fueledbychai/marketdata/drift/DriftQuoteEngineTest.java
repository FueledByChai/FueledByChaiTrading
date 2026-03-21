package com.fueledbychai.marketdata.drift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.drift.common.api.IDriftRestApi;
import com.fueledbychai.drift.common.api.IDriftWebSocketApi;
import com.fueledbychai.drift.common.api.model.DriftMarketType;
import com.fueledbychai.drift.common.api.model.DriftOrderBookLevel;
import com.fueledbychai.drift.common.api.model.DriftOrderBookSnapshot;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;

@ExtendWith(MockitoExtension.class)
class DriftQuoteEngineTest {

    @Mock
    private IDriftRestApi restApi;

    @Mock
    private IDriftWebSocketApi webSocketApi;

    @Mock
    private ITickerRegistry tickerRegistry;

    @Test
    void subscribeLevel1DeliversImmediateTopOfBookFromSynchronousOrderBookSnapshot() throws Exception {
        Ticker ticker = perpTicker("SOL-PERP");
        DriftOrderBookSnapshot snapshot = snapshot("SOL-PERP", new BigDecimal("101.10"), new BigDecimal("101.30"),
                level("101.10", "2.5"), level("101.30", "1.75"));

        doAnswer(invocation -> {
            invocation.<com.fueledbychai.drift.common.api.ws.DriftOrderBookListener>getArgument(2)
                    .onWebSocketEvent(snapshot);
            return null;
        }).when(webSocketApi).subscribeOrderBook(eq("SOL-PERP"), eq(DriftMarketType.PERP), any());
        when(restApi.getMarket("SOL-PERP")).thenReturn(null);

        DriftQuoteEngine engine = new DriftQuoteEngine(restApi, webSocketApi, tickerRegistry);
        AtomicReference<ILevel1Quote> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            engine.startEngine();
            engine.subscribeLevel1(ticker, quote -> {
                if (quote.getValue(QuoteType.BID) != null && quote.getValue(QuoteType.ASK) != null) {
                    received.set(quote);
                    latch.countDown();
                }
            });

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("101.10"), received.get().getValue(QuoteType.BID));
            assertEquals(new BigDecimal("101.30"), received.get().getValue(QuoteType.ASK));
            assertEquals(new BigDecimal("2.5"), received.get().getValue(QuoteType.BID_SIZE));
            assertEquals(new BigDecimal("1.75"), received.get().getValue(QuoteType.ASK_SIZE));
        } finally {
            engine.stopEngine();
            engine.shutdownNow();
        }
    }

    @Test
    void handleOrderBookUpdateFallsBackToFirstLevelsWhenBestBidAskFieldsMissing() throws Exception {
        Ticker ticker = perpTicker("SOL-PERP");
        DriftOrderBookSnapshot snapshot = snapshot("SOL-PERP", null, null, level("101.10", "2.5"),
                level("101.30", "1.75"));
        when(restApi.getMarket("SOL-PERP")).thenReturn(null);

        DriftQuoteEngine engine = new DriftQuoteEngine(restApi, webSocketApi, tickerRegistry);
        AtomicReference<ILevel1Quote> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            engine.startEngine();
            engine.subscribeLevel1(ticker, quote -> {
                if (quote.getValue(QuoteType.BID) != null && quote.getValue(QuoteType.ASK) != null) {
                    received.set(quote);
                    latch.countDown();
                }
            });

            engine.handleOrderBookUpdate(snapshot);

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("101.10"), received.get().getValue(QuoteType.BID));
            assertEquals(new BigDecimal("101.30"), received.get().getValue(QuoteType.ASK));
            assertEquals(new BigDecimal("2.5"), received.get().getValue(QuoteType.BID_SIZE));
            assertEquals(new BigDecimal("1.75"), received.get().getValue(QuoteType.ASK_SIZE));
        } finally {
            engine.stopEngine();
            engine.shutdownNow();
        }
    }

    @Test
    void subscribeLevel1UsesSubscribedTickerWhenSnapshotOmitsMarketName() throws Exception {
        Ticker ticker = perpTicker("SOL-PERP");
        DriftOrderBookSnapshot snapshot = new DriftOrderBookSnapshot(null, DriftMarketType.PERP, 0, 1710000000123L,
                456L, null, new BigDecimal("101.10"), new BigDecimal("101.30"), null,
                List.of(level("101.10", "2.5")), List.of(level("101.30", "1.75")));

        doAnswer(invocation -> {
            invocation.<com.fueledbychai.drift.common.api.ws.DriftOrderBookListener>getArgument(2)
                    .onWebSocketEvent(snapshot);
            return null;
        }).when(webSocketApi).subscribeOrderBook(eq("SOL-PERP"), eq(DriftMarketType.PERP), any());
        when(restApi.getMarket("SOL-PERP")).thenReturn(null);

        DriftQuoteEngine engine = new DriftQuoteEngine(restApi, webSocketApi, tickerRegistry);
        AtomicReference<ILevel1Quote> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            engine.startEngine();
            engine.subscribeLevel1(ticker, quote -> {
                if (quote.getValue(QuoteType.BID) != null && quote.getValue(QuoteType.ASK) != null) {
                    received.set(quote);
                    latch.countDown();
                }
            });

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("101.10"), received.get().getValue(QuoteType.BID));
            assertEquals(new BigDecimal("101.30"), received.get().getValue(QuoteType.ASK));
        } finally {
            engine.stopEngine();
            engine.shutdownNow();
        }
    }

    @Test
    void subscribeLevel1PublishesInitialOrderBookSnapshotFromRestApi() throws Exception {
        Ticker ticker = perpTicker("SOL-PERP");
        DriftOrderBookSnapshot snapshot = new DriftOrderBookSnapshot(null, DriftMarketType.PERP, 0, 1710000000123L,
                456L, null, new BigDecimal("101.10"), new BigDecimal("101.30"), null,
                List.of(level("101.10", "2.5")), List.of(level("101.30", "1.75")));

        when(restApi.getOrderBook("SOL-PERP", DriftMarketType.PERP)).thenReturn(snapshot);
        when(restApi.getMarket("SOL-PERP")).thenReturn(null);

        DriftQuoteEngine engine = new DriftQuoteEngine(restApi, webSocketApi, tickerRegistry);
        AtomicReference<ILevel1Quote> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            engine.startEngine();
            engine.subscribeLevel1(ticker, quote -> {
                if (quote.getValue(QuoteType.BID) != null && quote.getValue(QuoteType.ASK) != null) {
                    received.set(quote);
                    latch.countDown();
                }
            });

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("101.10"), received.get().getValue(QuoteType.BID));
            assertEquals(new BigDecimal("101.30"), received.get().getValue(QuoteType.ASK));
        } finally {
            engine.stopEngine();
            engine.shutdownNow();
        }
    }

    private static DriftOrderBookSnapshot snapshot(String marketName, BigDecimal bestBidPrice, BigDecimal bestAskPrice,
            DriftOrderBookLevel bestBidLevel, DriftOrderBookLevel bestAskLevel) {
        return new DriftOrderBookSnapshot(marketName, DriftMarketType.PERP, 0, 1710000000123L, 456L, null,
                bestBidPrice, bestAskPrice, null, List.of(bestBidLevel), List.of(bestAskLevel));
    }

    private static DriftOrderBookLevel level(String price, String size) {
        return new DriftOrderBookLevel(new BigDecimal(price), new BigDecimal(size), Map.of());
    }

    private static Ticker perpTicker(String symbol) {
        return new Ticker(symbol).setExchange(Exchange.DRIFT).setPrimaryExchange(Exchange.DRIFT)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES).setMinimumTickSize(new BigDecimal("0.01"))
                .setOrderSizeIncrement(new BigDecimal("0.001"));
    }
}
