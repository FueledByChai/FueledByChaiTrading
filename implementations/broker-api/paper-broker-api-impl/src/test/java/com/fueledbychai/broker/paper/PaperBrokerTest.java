package com.fueledbychai.broker.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.QuoteEngine;

@ExtendWith(MockitoExtension.class)
public class PaperBrokerTest {

    private static final PaperBrokerLatency ZERO_LATENCY = new PaperBrokerLatency(0, 0, 0, 0);

    @Mock
    private QuoteEngine quoteEngine;

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
}
