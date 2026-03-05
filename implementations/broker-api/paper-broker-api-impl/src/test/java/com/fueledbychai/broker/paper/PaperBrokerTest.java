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
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Type;
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
    public void testTracksPositionsPerTicker() {
        Ticker btcTicker = new Ticker("BTCUSDT");
        Ticker ethTicker = new Ticker("ETHUSDT");
        PaperBroker broker = new PaperBroker(quoteEngine, List.of(btcTicker, ethTicker),
                PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY, 1000.0);

        broker.bidUpdated(btcTicker, new BigDecimal("99.0"), false, null);
        broker.askUpdated(btcTicker, new BigDecimal("100.0"), false, null);
        broker.bidUpdated(ethTicker, new BigDecimal("199.0"), false, null);
        broker.askUpdated(ethTicker, new BigDecimal("200.0"), false, null);

        OrderTicket btcBuy = new OrderTicket("btc-buy", btcTicker, new BigDecimal("1.0"), TradeDirection.BUY);
        btcBuy.setType(Type.MARKET);
        OrderTicket ethSell = new OrderTicket("eth-sell", ethTicker, new BigDecimal("2.0"), TradeDirection.SELL);
        ethSell.setType(Type.MARKET);

        broker.placeOrder(btcBuy);
        broker.placeOrder(ethSell);

        List<Position> positions = broker.getAllPositions();
        assertEquals(2, positions.size());

        Map<String, Position> bySymbol = positions.stream()
                .collect(Collectors.toMap(position -> position.getTicker().getSymbol(), position -> position));
        assertEquals(0, bySymbol.get("BTCUSDT").getSize().compareTo(new BigDecimal("1.0")));
        assertEquals(0, bySymbol.get("ETHUSDT").getSize().compareTo(new BigDecimal("-2.0")));
    }

    @Test
    public void testLimitAskTouchesAndPartiallyFillsOnTradePrint() {
        Ticker ticker = new Ticker("BTCUSDT");
        PaperBroker broker = new PaperBroker(quoteEngine, ticker, PaperBrokerCommission.PARADEX_COMMISSION, ZERO_LATENCY,
                1000.0);

        broker.bidUpdated(ticker, new BigDecimal("100.0"), false, null);
        broker.askUpdated(ticker, new BigDecimal("102.0"), false, null);

        OrderTicket askOrder = new OrderTicket("ask-touch", ticker, new BigDecimal("1.0"), TradeDirection.SELL);
        askOrder.setType(Type.LIMIT);
        askOrder.setLimitPrice(new BigDecimal("101.0"));
        broker.placeOrder(askOrder);

        assertTrue(broker.openAsks.containsKey(askOrder.getOrderId()));

        broker.bidUpdated(ticker, new BigDecimal("101.0"), true, new BigDecimal("0.4"));
        assertEquals(OrderStatus.Status.PARTIAL_FILL, askOrder.getCurrentStatus());
        assertEquals(0, askOrder.getFilledSize().compareTo(new BigDecimal("0.4")));
        assertTrue(broker.openAsks.containsKey(askOrder.getOrderId()));

        broker.bidUpdated(ticker, new BigDecimal("101.0"), false, null);
        assertEquals(OrderStatus.Status.FILLED, askOrder.getCurrentStatus());
        assertEquals(0, askOrder.getFilledSize().compareTo(new BigDecimal("1.0")));
        assertFalse(broker.openAsks.containsKey(askOrder.getOrderId()));
    }
}
