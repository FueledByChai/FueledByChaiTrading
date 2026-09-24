package com.fueledbychai.marketdata.qfex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.ILevel1Quote;
import com.fueledbychai.marketdata.OrderFlow;
import com.fueledbychai.marketdata.QuoteType;
import com.fueledbychai.qfex.common.api.IQfexRestApi;
import com.fueledbychai.qfex.common.api.IQfexWebSocketApi;
import com.fueledbychai.qfex.common.api.QfexContract;
import com.fueledbychai.qfex.common.api.ws.QfexMarketDataStream;
import com.fueledbychai.qfex.common.api.ws.QfexStream;
import com.fueledbychai.qfex.common.api.ws.QfexTradeStream;

class QfexQuoteEngineTest {

    static class CapturingEngine extends QfexQuoteEngine {
        final List<ILevel1Quote> quotes = new ArrayList<>();
        final List<OrderFlow> flows = new ArrayList<>();

        CapturingEngine(IQfexWebSocketApi ws) {
            super(new StubRest(), ws);
        }

        @Override
        public void fireLevel1Quote(ILevel1Quote quote) {
            quotes.add(quote);
        }

        @Override
        public void fireOrderFlow(OrderFlow orderFlow) {
            flows.add(orderFlow);
        }
    }

    static class StubWs implements IQfexWebSocketApi {
        final QfexMarketDataStream mds = new QfexMarketDataStream("wss://unit.test");

        @Override public void connect() { }
        @Override public void connectOrderEntryWebSocket() { }
        @Override public void disconnectAll() { }
        @Override public QfexMarketDataStream marketData() { return mds; }
        @Override public QfexTradeStream trade() { return null; }
    }

    static class StubRest implements IQfexRestApi {
        @Override public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType t) { return new InstrumentDescriptor[0]; }
        @Override public InstrumentDescriptor getInstrumentDescriptor(String s) { return null; }
        @Override public List<QfexContract> getContracts() { return List.of(); }
        @Override public QfexContract getContract(String s) { return null; }
        @Override public JsonNode getCandles(String s, String r, Instant f, Instant t) { return null; }
        @Override public JsonNode getOrderBook(String s) { return null; }
        @Override public JsonNode getPositions() { return null; }
        @Override public boolean isPublicApiOnly() { return true; }
    }

    private CapturingEngine engine;
    private final Ticker ticker = new Ticker("AAPL-USD");

    @BeforeEach
    void setUp() {
        engine = new CapturingEngine(new StubWs());
        engine.subscribeLevel1(ticker, q -> { });
    }

    @Test
    void bboBecomesBidAskQuote() throws Exception {
        engine.onMessage(json("""
                {"type":"bbo","time":"2026-09-24T22:32:50.958348987Z","symbol":"AAPL-USD",
                 "bid":[["336.12","0.888"]],"ask":[["336.18","431.477"]]}"""));

        ILevel1Quote q = engine.quotes.get(0);
        assertEquals(0, new BigDecimal("336.12").compareTo(q.getValue(QuoteType.BID)));
        assertEquals(0, new BigDecimal("336.18").compareTo(q.getValue(QuoteType.ASK)));
        assertEquals(0, new BigDecimal("0.888").compareTo(q.getValue(QuoteType.BID_SIZE)));
    }

    @Test
    void tradeFiresLastAndOrderFlow() throws Exception {
        engine.onMessage(json("""
                {"type":"trade","time":"2026-09-24T22:32:55.446430050Z","symbol":"AAPL-USD",
                 "size":"5.005","price":"336.18","side":"SELL"}"""));

        assertEquals(0, new BigDecimal("336.18").compareTo(engine.quotes.get(0).getValue(QuoteType.LAST)));
        assertEquals(OrderFlow.Side.SELL, engine.flows.get(0).getSide());
    }

    @Test
    void markPriceAndUnsubscribedSymbols() throws Exception {
        engine.onMessage(json("{\"type\":\"mark_price\",\"symbol\":\"AAPL-USD\",\"price\":\"336.18\"}"));
        engine.onMessage(json("{\"type\":\"mark_price\",\"symbol\":\"TSLA-USD\",\"price\":\"400\"}"));

        assertEquals(1, engine.quotes.size());
        assertTrue(engine.quotes.get(0).containsType(QuoteType.MARK_PRICE));
        assertFalse(engine.quotes.get(0).containsType(QuoteType.BID));
    }

    private static JsonNode json(String s) throws Exception {
        return QfexStream.MAPPER.readTree(s);
    }
}
