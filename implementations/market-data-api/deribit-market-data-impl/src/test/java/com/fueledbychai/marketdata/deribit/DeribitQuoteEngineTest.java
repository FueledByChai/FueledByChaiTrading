package com.fueledbychai.marketdata.deribit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.deribit.common.api.IDeribitRestApi;
import com.fueledbychai.deribit.common.api.IDeribitWebSocketApi;
import com.google.gson.JsonObject;
import com.fueledbychai.deribit.common.api.ws.listener.IDeribitOrderBookListener;
import com.fueledbychai.deribit.common.api.ws.listener.IDeribitTickerListener;
import com.fueledbychai.deribit.common.api.ws.listener.IDeribitTradeListener;
import com.fueledbychai.deribit.common.api.ws.model.DeribitBookUpdate;
import com.fueledbychai.deribit.common.api.ws.model.DeribitTickerUpdate;
import com.fueledbychai.deribit.common.api.ws.model.DeribitTrade;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.util.ITickerRegistry;

class DeribitQuoteEngineTest {

    @Test
    void optionTickerUpdatesExposeOptionSpecificQuoteTypes() throws Exception {
        StubDeribitWebSocketApi webSocketApi = new StubDeribitWebSocketApi();
        Ticker canonical = new Ticker("BTC-27MAR26-90000-C")
                .setExchange(Exchange.DERIBIT)
                .setPrimaryExchange(Exchange.DERIBIT)
                .setInstrumentType(InstrumentType.OPTION)
                .setCurrency("USD")
                .setMinimumTickSize(new BigDecimal("0.0005"))
                .setOrderSizeIncrement(new BigDecimal("0.1"))
                .setContractMultiplier(BigDecimal.ONE)
                .setExpiryYear(2026)
                .setExpiryMonth(3)
                .setExpiryDay(27)
                .setStrike(new BigDecimal("90000"))
                .setRight(Ticker.Right.CALL);
        StubTickerRegistry tickerRegistry = new StubTickerRegistry(canonical);
        DeribitQuoteEngine engine = new DeribitQuoteEngine(new StubDeribitRestApi(), webSocketApi, tickerRegistry);

        try {
            CountDownLatch latch = new CountDownLatch(1);
            final ILevel1Quote[] received = new ILevel1Quote[1];
            Ticker ticker = new Ticker("BTC-27MAR26-90000-C");

            engine.startEngine();
            engine.subscribeLevel1(ticker, quote -> {
                received[0] = quote;
                latch.countDown();
            });

            webSocketApi.emitTicker(new DeribitTickerUpdate("BTC-27MAR26-90000-C", 1_700_000_000_000L,
                    new BigDecimal("100.1"), new BigDecimal("5"), new BigDecimal("100.2"), new BigDecimal("4"),
                    new BigDecimal("100.15"), new BigDecimal("100.18"), new BigDecimal("250"), new BigDecimal("12"),
                    new BigDecimal("1200"), new BigDecimal("89000"), null, new BigDecimal("59.5"),
                    new BigDecimal("60.1"), new BigDecimal("59.8"), new BigDecimal("0.45"),
                    new BigDecimal("0.0012"), new BigDecimal("-3.4"), new BigDecimal("12.5"),
                    new BigDecimal("8.1"), new BigDecimal("0.04")));

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("59.8"), received[0].getValue(QuoteType.MARK_IV));
            assertEquals(new BigDecimal("0.45"), received[0].getValue(QuoteType.DELTA));
            assertEquals(new BigDecimal("12.5"), received[0].getValue(QuoteType.VEGA));
            assertEquals(InstrumentType.OPTION, ticker.getInstrumentType());
            assertEquals(Ticker.Right.CALL, ticker.getRight());
        } finally {
            engine.stopEngine();
            engine.shutdownNow();
        }
    }

    private static class StubDeribitRestApi implements IDeribitRestApi {

        @Override
        public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
            return new InstrumentDescriptor[0];
        }

        @Override
        public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
            return null;
        }

        @Override
        public JsonObject getTicker(String instrumentName) {
            return new JsonObject();
        }

        @Override
        public boolean isPublicApiOnly() {
            return true;
        }
    }

    private static class StubDeribitWebSocketApi implements IDeribitWebSocketApi {

        private IDeribitTickerListener tickerListener;

        @Override
        public void connect() {
        }

        @Override
        public void subscribeTicker(String instrumentName, IDeribitTickerListener listener) {
            this.tickerListener = listener;
        }

        @Override
        public void subscribeOrderBook(String instrumentName, IDeribitOrderBookListener listener) {
        }

        @Override
        public void subscribeTrades(String instrumentName, IDeribitTradeListener listener) {
        }

        @Override
        public void disconnectAll() {
        }

        void emitTicker(DeribitTickerUpdate update) {
            tickerListener.onTicker(update);
        }
    }

    private static class StubTickerRegistry implements ITickerRegistry {

        private final Ticker canonical;

        StubTickerRegistry(Ticker canonical) {
            this.canonical = canonical;
        }

        @Override
        public Ticker lookupByBrokerSymbol(InstrumentType instrumentType, String tickerString) {
            if (instrumentType == InstrumentType.OPTION && canonical.getSymbol().equals(tickerString)) {
                return canonical;
            }
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
