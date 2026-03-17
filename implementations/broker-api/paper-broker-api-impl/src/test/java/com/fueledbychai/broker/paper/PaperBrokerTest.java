package com.fueledbychai.broker.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.QuoteEngine;

@ExtendWith(MockitoExtension.class)
public class PaperBrokerTest {

    private static final PaperBrokerLatency ZERO_LATENCY = new PaperBrokerLatency(0, 0, 0, 0);

    @Mock
    private QuoteEngine quoteEngine;

    private static final class TestablePaperBroker extends PaperBroker {

        private int startAccountUpdateTaskCalls;

        private TestablePaperBroker(QuoteEngine quoteEngine, Ticker ticker) {
            super(quoteEngine, ticker, PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);
        }

        @Override
        protected void startAccountUpdateTask() {
            startAccountUpdateTaskCalls++;
        }
    }

    @Test
    public void testIsConnectedFalseByDefault() {
        PaperBroker broker = new PaperBroker(quoteEngine, new Ticker("BTCUSDT"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        assertFalse(broker.isConnected());
    }

    @Test
    public void testConnectMarksBrokerConnectedAndStartsAccountUpdateTask() {
        TestablePaperBroker broker = new TestablePaperBroker(quoteEngine, new Ticker("BTCUSDT"));

        broker.connect();

        assertTrue(broker.isConnected());
        assertEquals(1, broker.startAccountUpdateTaskCalls);
    }

    @Test
    public void testConnectWhenAlreadyConnectedDoesNotRestartAccountUpdateTask() {
        TestablePaperBroker broker = new TestablePaperBroker(quoteEngine, new Ticker("BTCUSDT"));

        broker.connect();
        broker.connect();

        assertTrue(broker.isConnected());
        assertEquals(1, broker.startAccountUpdateTaskCalls);
    }

    @Test
    public void testDisconnectMarksBrokerDisconnected() {
        Ticker ticker = new Ticker("BTCUSDT");
        TestablePaperBroker broker = new TestablePaperBroker(quoteEngine, ticker);
        broker.connect();

        broker.disconnect();

        assertFalse(broker.isConnected());
        verify(quoteEngine, times(1)).unsubscribeLevel1(ticker, broker);
        verify(quoteEngine, times(1)).unsubscribeOrderFlow(ticker, broker);
    }

    @Test
    public void testGetOpenOrdersReturnsAllBidsAndAsks() {
        PaperBroker broker = new PaperBroker(quoteEngine, new Ticker("BTCUSDT"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        OrderTicket bidOrder = new OrderTicket("bid-1", new Ticker("BTCUSDT"), new BigDecimal("1.0"),
                TradeDirection.BUY);
        OrderTicket askOrder = new OrderTicket("ask-1", new Ticker("BTCUSDT"), new BigDecimal("2.0"),
                TradeDirection.SELL);

        broker.openBids.put(bidOrder.getOrderId(), bidOrder);
        broker.openAsks.put(askOrder.getOrderId(), askOrder);

        List<OrderTicket> openOrders = broker.getOpenOrders();

        assertEquals(2, openOrders.size());
        assertTrue(openOrders.stream().anyMatch(order -> order == bidOrder));
        assertTrue(openOrders.stream().anyMatch(order -> order == askOrder));
    }

    @Test
    public void testGetOpenOrdersForTickerFiltersByTicker() {
        PaperBroker broker = new PaperBroker(quoteEngine, new Ticker("BTCUSDT"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        Ticker btcTicker = new Ticker("BTCUSDT");
        Ticker ethTicker = new Ticker("ETHUSDT");

        OrderTicket btcBid = new OrderTicket("bid-btc", btcTicker, new BigDecimal("1.0"), TradeDirection.BUY);
        OrderTicket btcAsk = new OrderTicket("ask-btc", btcTicker, new BigDecimal("2.0"), TradeDirection.SELL);
        OrderTicket ethBid = new OrderTicket("bid-eth", ethTicker, new BigDecimal("3.0"), TradeDirection.BUY);

        broker.openBids.put(btcBid.getOrderId(), btcBid);
        broker.openAsks.put(btcAsk.getOrderId(), btcAsk);
        broker.openBids.put(ethBid.getOrderId(), ethBid);

        List<OrderTicket> openOrders = broker.getOpenOrders(btcTicker);

        assertEquals(2, openOrders.size());
        assertTrue(openOrders.stream().anyMatch(order -> order == btcBid));
        assertTrue(openOrders.stream().anyMatch(order -> order == btcAsk));
        assertTrue(openOrders.stream().noneMatch(order -> order == ethBid));
    }

    @Test
    public void testCancelOrderByClientOrderIdNotFound() {
        PaperBroker broker = spy(new PaperBroker(quoteEngine, new Ticker("BTCUSDT"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0));

        BrokerRequestResult result = broker.cancelOrderByClientOrderId("missing-client-id");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("missing-client-id"));
        verify(broker, never()).cancelOrderSubmitWithDelay(anyString(), anyBoolean());
    }

    @Test
    public void testCancelOrderByClientOrderIdFound() {
        PaperBroker broker = spy(new PaperBroker(quoteEngine, new Ticker("BTCUSDT"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0));

        OrderTicket order = new OrderTicket("order-1", new Ticker("BTCUSDT"), new BigDecimal("1.0"),
                TradeDirection.BUY);
        order.setClientOrderId("client-1");
        broker.openOrders.put(order.getOrderId(), order);

        doNothing().when(broker).cancelOrderSubmitWithDelay(order.getOrderId(), true);

        BrokerRequestResult result = broker.cancelOrderByClientOrderId("client-1");

        assertTrue(result.isSuccess());
        verify(broker).cancelOrderSubmitWithDelay(order.getOrderId(), true);
    }

    @Test
    public void testGenerateBalanceFilenameSanitizesSymbol() {
        PaperBroker broker = new PaperBroker(quoteEngine, new Ticker("AZTEC/USDC"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        String filename = broker.generateBalanceFilename("AZTEC/USDC", "LIGHTER");

        assertEquals("AZTEC_USDC-LIGHTER-paperbroker-startingbalance.txt", filename);
        assertFalse(filename.contains("/"));
    }

    @Test
    public void testGenerateCsvFilenameSanitizesSymbol() {
        PaperBroker broker = new PaperBroker(quoteEngine, new Ticker("AZTEC/USDC"),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        String filename = broker.generateCsvFilename("AZTEC/USDC", "LIGHTER");

        assertTrue(filename.matches("\\d{8}-\\d{4}-AZTEC_USDC-LIGHTER-Trades\\.csv"));
        assertFalse(filename.contains("/"));
    }

    @Test
    public void testAutoResolvesProfilesFromTicker() {
        Ticker ticker = new Ticker("BTC-USD-PERP").setExchange(Exchange.PARADEX)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES);

        PaperBroker broker = new PaperBroker(quoteEngine, ticker, 1000.0);

        assertEquals(-0.2 / 10000.0, broker.makerFee, 0.0);
        assertEquals(-2.0 / 10000.0, broker.takerFee, 0.0);
        assertEquals(350, broker.latencyModel.getRestLatencyMsMin());
        assertEquals(550, broker.latencyModel.getRestLatencyMsMax());
        assertEquals(200, broker.latencyModel.getWsLatencyMsMin());
        assertEquals(300, broker.latencyModel.getWsLatencyMsMax());
    }

    @Test
    public void testExplicitProfilesTakePrecedenceOverRegistry() {
        Ticker ticker = new Ticker("BTC-USD-PERP").setExchange(Exchange.PARADEX)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        PaperBrokerCommission customCommission = new PaperBrokerCommission(-1.0, -2.0);
        PaperBrokerLatency customLatency = new PaperBrokerLatency(10, 20, 30, 40);

        PaperBroker broker = new PaperBroker(quoteEngine, ticker, customCommission, customLatency, 1000.0);

        assertEquals(-1.0 / 10000.0, broker.makerFee, 0.0);
        assertEquals(-2.0 / 10000.0, broker.takerFee, 0.0);
        assertEquals(10, broker.latencyModel.getRestLatencyMsMin());
        assertEquals(20, broker.latencyModel.getRestLatencyMsMax());
        assertEquals(30, broker.latencyModel.getWsLatencyMsMin());
        assertEquals(40, broker.latencyModel.getWsLatencyMsMax());
    }

    @Test
    public void testAutoResolvesAsterProfilesFromTicker() {
        Ticker ticker = new Ticker("BTCUSDT").setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES);

        PaperBroker broker = new PaperBroker(quoteEngine, ticker, 1000.0);

        assertEquals(0.0, broker.makerFee, 0.0);
        assertEquals(-4.0 / 10000.0, broker.takerFee, 0.0);
        assertEquals(250, broker.latencyModel.getRestLatencyMsMin());
        assertEquals(450, broker.latencyModel.getRestLatencyMsMax());
        assertEquals(120, broker.latencyModel.getWsLatencyMsMin());
        assertEquals(220, broker.latencyModel.getWsLatencyMsMax());
    }
}
