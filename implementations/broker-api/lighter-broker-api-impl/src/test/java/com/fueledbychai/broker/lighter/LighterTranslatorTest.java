package com.fueledbychai.broker.lighter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.order.LighterOrderType;
import com.fueledbychai.lighter.common.api.order.LighterTimeInForce;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;
import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;
import com.fueledbychai.util.ITickerRegistry;

@ExtendWith(MockitoExtension.class)
class LighterTranslatorTest {

    @Mock
    private ITickerRegistry mockTickerRegistry;

    private LighterTranslator translator;
    private ITickerRegistry originalTickerRegistry;

    @BeforeEach
    void setUp() {
        translator = new LighterTranslator();
        originalTickerRegistry = LighterTranslator.tickerRegistry;
        LighterTranslator.tickerRegistry = mockTickerRegistry;
    }

    @AfterEach
    void tearDown() {
        LighterTranslator.tickerRegistry = originalTickerRegistry;
    }

    @Test
    void translateCreateOrder_MapsLimitOrderFields() {
        Ticker ticker = new Ticker("BTC");
        ticker.setId("7");
        ticker.setMinimumTickSize(new BigDecimal("0.1"));
        ticker.setOrderSizeIncrement(new BigDecimal("0.001"));

        OrderTicket order = new OrderTicket();
        order.setTicker(ticker);
        order.setClientOrderId("12345");
        order.setType(OrderTicket.Type.LIMIT);
        order.setDirection(TradeDirection.BUY);
        order.setSize(new BigDecimal("0.025"));
        order.setLimitPrice(new BigDecimal("70123.4"));
        order.setDuration(OrderTicket.Duration.GOOD_UNTIL_CANCELED);
        order.addModifier(OrderTicket.Modifier.REDUCE_ONLY);

        LighterCreateOrderRequest request = translator.translateCreateOrder(order, 255L, 3, 1001L);

        assertEquals(7, request.getMarketIndex());
        assertEquals(12345L, request.getClientOrderIndex());
        assertEquals(25L, request.getBaseAmount());
        assertEquals(701234, request.getPrice());
        assertFalse(request.isAsk());
        assertEquals(LighterOrderType.LIMIT, request.getOrderType());
        assertEquals(LighterTimeInForce.GTT, request.getTimeInForce());
        assertTrue(request.isReduceOnly());
        assertEquals(1001L, request.getNonce());
        assertEquals(3, request.getApiKeyIndex());
        assertEquals(255L, request.getAccountIndex());
    }

    @Test
    void translateCreateOrder_AcceptsZeroMarketIndexFromTicker() {
        Ticker ticker = new Ticker("ETH");
        ticker.setId("0");
        ticker.setMinimumTickSize(new BigDecimal("0.1"));
        ticker.setOrderSizeIncrement(new BigDecimal("0.001"));

        OrderTicket order = new OrderTicket();
        order.setTicker(ticker);
        order.setClientOrderId("12346");
        order.setType(OrderTicket.Type.LIMIT);
        order.setDirection(TradeDirection.BUY);
        order.setSize(new BigDecimal("0.005"));
        order.setLimitPrice(new BigDecimal("2500.1"));
        order.setDuration(OrderTicket.Duration.GOOD_UNTIL_CANCELED);

        LighterCreateOrderRequest request = translator.translateCreateOrder(order, 255L, 3, 1002L);

        assertEquals(0, request.getMarketIndex());
        assertEquals(5L, request.getBaseAmount());
        assertEquals(25001, request.getPrice());
    }

    @Test
    void translateCreateOrder_ResolvesZeroMarketIndexFromRegistry() {
        Ticker inputTicker = new Ticker("ETH");
        inputTicker.setMinimumTickSize(new BigDecimal("0.01"));
        inputTicker.setOrderSizeIncrement(new BigDecimal("0.001"));

        Ticker canonicalTicker = new Ticker("ETH");
        canonicalTicker.setId("0");
        canonicalTicker.setMinimumTickSize(new BigDecimal("0.1"));
        canonicalTicker.setOrderSizeIncrement(new BigDecimal("0.001"));

        when(mockTickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "ETH")).thenReturn(canonicalTicker);

        OrderTicket order = new OrderTicket();
        order.setTicker(inputTicker);
        order.setClientOrderId("12347");
        order.setType(OrderTicket.Type.LIMIT);
        order.setDirection(TradeDirection.BUY);
        order.setSize(new BigDecimal("0.006"));
        order.setLimitPrice(new BigDecimal("2500.2"));
        order.setDuration(OrderTicket.Duration.GOOD_UNTIL_CANCELED);

        LighterCreateOrderRequest request = translator.translateCreateOrder(order, 255L, 3, 1003L);

        assertEquals(0, request.getMarketIndex());
        assertEquals("0", inputTicker.getId());
        assertEquals(6L, request.getBaseAmount());
        assertEquals(25002, request.getPrice());
    }

    @Test
    void translateCreateOrder_MarketOrderWithoutPriceUsesAggressiveFallbackPrice() {
        Ticker buyTicker = new Ticker("BTC");
        buyTicker.setId("7");
        buyTicker.setMinimumTickSize(new BigDecimal("0.1"));
        buyTicker.setOrderSizeIncrement(new BigDecimal("0.001"));

        OrderTicket buyOrder = new OrderTicket();
        buyOrder.setTicker(buyTicker);
        buyOrder.setClientOrderId("20001");
        buyOrder.setType(OrderTicket.Type.MARKET);
        buyOrder.setDirection(TradeDirection.BUY);
        buyOrder.setSize(new BigDecimal("0.010"));
        buyOrder.setDuration(OrderTicket.Duration.IMMEDIATE_OR_CANCEL);

        LighterCreateOrderRequest buyRequest = translator.translateCreateOrder(buyOrder, 255L, 3, 1002L);
        assertEquals(Integer.MAX_VALUE, buyRequest.getPrice());
        assertEquals(LighterOrderType.MARKET, buyRequest.getOrderType());
        assertEquals(LighterTimeInForce.IOC, buyRequest.getTimeInForce());

        Ticker sellTicker = new Ticker("BTC");
        sellTicker.setId("7");
        sellTicker.setMinimumTickSize(new BigDecimal("0.1"));
        sellTicker.setOrderSizeIncrement(new BigDecimal("0.001"));

        OrderTicket sellOrder = new OrderTicket();
        sellOrder.setTicker(sellTicker);
        sellOrder.setClientOrderId("20002");
        sellOrder.setType(OrderTicket.Type.MARKET);
        sellOrder.setDirection(TradeDirection.SELL);
        sellOrder.setSize(new BigDecimal("0.010"));
        sellOrder.setDuration(OrderTicket.Duration.IMMEDIATE_OR_CANCEL);

        LighterCreateOrderRequest sellRequest = translator.translateCreateOrder(sellOrder, 255L, 3, 1003L);
        assertEquals(0, sellRequest.getPrice());
        assertEquals(LighterOrderType.MARKET, sellRequest.getOrderType());
        assertEquals(LighterTimeInForce.IOC, sellRequest.getTimeInForce());
    }

    @Test
    void translateOrderStatus_MapsOpenOrderToPartialFill() {
        Ticker ticker = new Ticker("BTC");
        ticker.setId("7");
        when(mockTickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "7")).thenReturn(ticker);

        LighterOrder order = new LighterOrder();
        order.setOrderId("55");
        order.setClientOrderId("101");
        order.setMarketIndex(7);
        order.setStatus("open");
        order.setInitialBaseAmount(new BigDecimal("1.0"));
        order.setFilledBaseAmount(new BigDecimal("0.6"));
        order.setRemainingBaseAmount(new BigDecimal("0.4"));
        order.setPrice(new BigDecimal("100.0"));
        order.setUpdatedAt(1_700_000_000_000L);

        OrderStatus status = translator.translateOrderStatus(order);

        assertNotNull(status);
        assertEquals(OrderStatus.Status.PARTIAL_FILL, status.getStatus());
        assertEquals(new BigDecimal("0.6"), status.getFilled());
        assertEquals(new BigDecimal("0.4"), status.getRemaining());
        assertEquals(new BigDecimal("100.0"), status.getFillPrice());
        assertEquals("55", status.getOrderId());
        assertEquals("101", status.getClientOrderId());
    }

    @Test
    void translateFill_MapsBidAccountAsTakerWhenMakerAskTrue() {
        Ticker ticker = new Ticker("BTC");
        ticker.setId("7");
        when(mockTickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "7")).thenReturn(ticker);

        LighterTrade trade = new LighterTrade();
        trade.setId(777L);
        trade.setMarketId(7);
        trade.setPrice(new BigDecimal("69000.0"));
        trade.setSize(new BigDecimal("0.5"));
        trade.setMakerAsk(Boolean.TRUE);
        trade.setAskAccountId(255L);
        trade.setBidAccountId(500L);
        trade.setAskId(12L);
        trade.setBidId(13L);
        trade.setTakerFee(new BigDecimal("0.25"));
        trade.setTimestamp(1_700_000_002_000L);

        Fill fill = translator.translateFill(trade, 500L);

        assertNotNull(fill);
        assertEquals("777", fill.getFillId());
        assertEquals(TradeDirection.BUY, fill.getSide());
        assertEquals("13", fill.getOrderId());
        assertTrue(fill.isTaker());
        assertEquals(new BigDecimal("0.25"), fill.getCommission());
        assertEquals(new BigDecimal("69000.0"), fill.getPrice());
        assertEquals(new BigDecimal("0.5"), fill.getSize());

        assertNull(translator.translateFill(trade, 999L));
    }
}
