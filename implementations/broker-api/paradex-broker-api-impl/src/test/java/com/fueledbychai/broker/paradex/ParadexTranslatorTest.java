package com.fueledbychai.broker.paradex;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.common.api.ParadexUtil;
import com.fueledbychai.paradex.common.api.order.OrderType;
import com.fueledbychai.paradex.common.api.order.ParadexOrder;
import com.fueledbychai.paradex.common.api.order.Side;
import com.fueledbychai.paradex.common.api.ws.fills.ParadexFill;
import com.fueledbychai.util.ITickerRegistry;

@ExtendWith(MockitoExtension.class)
class ParadexTranslatorTest {

    @Mock
    private ParadexOrder mockParadexOrder;

    @Mock
    private ITickerRegistry mockTickerRegistry;

    @Mock
    private Ticker mockTicker;

    @Mock
    private OrderTicket mockTradeOrder;

    private ParadexTranslator translator;
    private ITickerRegistry originalTickerRegistry;

    @BeforeEach
    void setUp() {
        translator = new ParadexTranslator();
        // Save original tickerRegistry and inject mock
        originalTickerRegistry = ParadexTranslator.tickerRegistry;
        ParadexTranslator.tickerRegistry = mockTickerRegistry;
    }

    @AfterEach
    void tearDown() {
        // Restore original tickerRegistry
        ParadexTranslator.tickerRegistry = originalTickerRegistry;
    }

    @Test
    void testTranslateFill_BuyTakerFill() {
        // Given
        ParadexFill paradexFill = createParadexFill("BTC-USD-PERP", "50000.50", "0.1", ParadexFill.Side.BUY,
                ParadexFill.LiquidityType.TAKER, "fill-123", "order-456", "5.25", 1633024800000L // 2021-10-01 00:00:00
                                                                                                 // UTC
        );

        when(mockTickerRegistry.lookupByBrokerSymbol("BTC-USD-PERP")).thenReturn(mockTicker);

        // When
        Fill result = translator.translateFill(paradexFill);

        // Then
        assertNotNull(result);
        assertEquals(mockTicker, result.getTicker());
        assertEquals(new BigDecimal("50000.50"), result.getPrice());
        assertEquals("fill-123", result.getFillId());
        assertEquals(new BigDecimal("0.1"), result.getSize());
        assertEquals(TradeDirection.BUY, result.getSide());
        assertEquals("order-456", result.getOrderId());
        assertTrue(result.isTaker());
        assertEquals(new BigDecimal("5.25"), result.getCommission());
    }

    @Test
    void testTranslateFill_SellMakerFill() {
        // Given
        ParadexFill paradexFill = createParadexFill("ETH-USD-PERP", "3000.75", "2.5", ParadexFill.Side.SELL,
                ParadexFill.LiquidityType.MAKER, "fill-789", "order-101", "-1.50", // negative fee for maker rebate
                1633111200000L // 2021-10-02 00:00:00 UTC
        );

        when(mockTickerRegistry.lookupByBrokerSymbol("ETH-USD-PERP")).thenReturn(mockTicker);

        // When
        Fill result = translator.translateFill(paradexFill);

        // Then
        assertNotNull(result);
        assertEquals(mockTicker, result.getTicker());
        assertEquals(new BigDecimal("3000.75"), result.getPrice());
        assertEquals("fill-789", result.getFillId());
        assertEquals(new BigDecimal("2.5"), result.getSize());
        assertEquals(TradeDirection.SELL, result.getSide());
        assertEquals("order-101", result.getOrderId());
        assertFalse(result.isTaker()); // MAKER should be false for isTaker
        assertEquals(new BigDecimal("-1.50"), result.getCommission());
    }

    @Test
    void testTranslateFill_ZeroFee() {
        // Given
        ParadexFill paradexFill = createParadexFill("SOL-USD-PERP", "100.00", "10.0", ParadexFill.Side.BUY,
                ParadexFill.LiquidityType.MAKER, "fill-000", "order-999", "0.00", 1633197600000L // 2021-10-03 00:00:00
                                                                                                 // UTC
        );

        when(mockTickerRegistry.lookupByBrokerSymbol("SOL-USD-PERP")).thenReturn(mockTicker);

        // When
        Fill result = translator.translateFill(paradexFill);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("0.00"), result.getCommission());
        assertFalse(result.isTaker());
    }

    private ParadexFill createParadexFill(String market, String price, String size, ParadexFill.Side side,
            ParadexFill.LiquidityType liquidity, String fillId, String orderId, String fee, long createdAt) {

        ParadexFill fill = new ParadexFill();
        fill.setMarket(market);
        fill.setPrice(price);
        fill.setSize(size);
        fill.setSide(side);
        fill.setLiquidity(liquidity);
        fill.setId(fillId);
        fill.setOrderId(orderId);
        fill.setFee(fee);
        fill.setCreatedAt(createdAt);

        return fill;
    }

    @Test
    void testTranslateTradeOrder_NullValues() {
        // Test behavior with null values where applicable
        when(mockTradeOrder.getClientOrderId()).thenReturn(null);
        when(mockTradeOrder.getTicker()).thenReturn(mockTicker);
        when(mockTicker.getSymbol()).thenReturn("TEST-USD");
        when(mockTradeOrder.getTradeDirection()).thenReturn(TradeDirection.BUY);
        when(mockTradeOrder.getSize()).thenReturn(new BigDecimal("1.0"));
        when(mockTradeOrder.getType()).thenReturn(OrderTicket.Type.MARKET);

        ParadexOrder result = translator.translateOrder(mockTradeOrder);

        assertNotNull(result);
        assertNull(result.getClientId()); // Should handle null reference gracefully
        assertEquals("TEST-USD", result.getTicker());
    }

    @Test
    void testTranslateOrderShouldUseClientOrderIdNotReference() {
        // Setup - This test should FAIL because the current implementation uses
        // getReference() instead of getClientOrderId()
        String expectedClientOrderId = "CLIENT_ORDER_123";

        when(mockTradeOrder.getClientOrderId()).thenReturn(expectedClientOrderId);
        when(mockTradeOrder.getTicker()).thenReturn(mockTicker);
        when(mockTicker.getSymbol()).thenReturn("BTC-USD");
        when(mockTradeOrder.getTradeDirection()).thenReturn(TradeDirection.BUY);
        when(mockTradeOrder.getSize()).thenReturn(new BigDecimal("1.0"));
        when(mockTradeOrder.getType()).thenReturn(OrderTicket.Type.MARKET);

        // Execute
        ParadexOrder result = translator.translateOrder(mockTradeOrder);

        // Verify - This assertion should FAIL because the current implementation uses
        // getReference()
        // instead of getClientOrderId()
        assertEquals(expectedClientOrderId, result.getClientId(),
                "ParadexOrder should use OrderTicket's clientOrderId, not reference");

        // Verify that getClientOrderId() was called (this will currently fail because
        // it's not called)
        verify(mockTradeOrder).getClientOrderId();
    }

    @Test
    void testTranslateParadexOrderToTradeOrder() {
        // Test the method that currently returns empty TradeOrder
        OrderTicket result = translator.translateOrder(mockParadexOrder);

        assertNotNull(result);
        assertInstanceOf(OrderTicket.class, result);
        // Since the method is not implemented, we can only verify it returns a new
        // TradeOrder
    }

    @Test
    void testTranslateTradeOrderToParadexOrder_BuyMarketOrder() {
        // Setup
        when(mockTradeOrder.getClientOrderId()).thenReturn("CLIENT123");
        when(mockTradeOrder.getTicker()).thenReturn(mockTicker);
        when(mockTicker.getSymbol()).thenReturn("BTC-USD");
        when(mockTradeOrder.getTradeDirection()).thenReturn(TradeDirection.BUY);
        when(mockTradeOrder.getSize()).thenReturn(new BigDecimal("1.5"));
        when(mockTradeOrder.getType()).thenReturn(OrderTicket.Type.MARKET);

        // Execute
        ParadexOrder result = translator.translateOrder(mockTradeOrder);

        // Verify
        assertNotNull(result);
        assertEquals("CLIENT123", result.getClientId());
        assertEquals("BTC-USD", result.getTicker());
        assertEquals(Side.BUY, result.getSide());
        assertEquals(new BigDecimal("1.5"), result.getSize());
        assertEquals(OrderType.MARKET, result.getOrderType());
        assertNull(result.getLimitPrice()); // Market orders don't have limit price

        // Verify method calls
        verify(mockTradeOrder).getSize();
        verify(mockTradeOrder, never()).getLimitPrice();
    }

    @Test
    void testTranslateTradeOrderToParadexOrder_SellLimitOrder() {
        // Setup
        when(mockTradeOrder.getClientOrderId()).thenReturn("CLIENT456");
        when(mockTradeOrder.getTicker()).thenReturn(mockTicker);
        when(mockTicker.getSymbol()).thenReturn("ETH-USD");
        when(mockTradeOrder.getTradeDirection()).thenReturn(TradeDirection.SELL);
        when(mockTradeOrder.getSize()).thenReturn(new BigDecimal("2.0"));
        when(mockTradeOrder.getType()).thenReturn(OrderTicket.Type.LIMIT);
        when(mockTradeOrder.getLimitPrice()).thenReturn(new BigDecimal("3000.50"));

        // Execute
        ParadexOrder result = translator.translateOrder(mockTradeOrder);

        // Verify
        assertNotNull(result);
        assertEquals("CLIENT456", result.getClientId());
        assertEquals("ETH-USD", result.getTicker());
        assertEquals(Side.SELL, result.getSide());
        assertEquals(new BigDecimal("2.0"), result.getSize());
        assertEquals(OrderType.LIMIT, result.getOrderType());
        assertEquals(new BigDecimal("3000.50"), result.getLimitPrice());
    }

    @Test
    void testTranslateTradeOrderToParadexOrder_StopOrder() {
        // Setup
        when(mockTradeOrder.getClientOrderId()).thenReturn("CLIENT789");
        when(mockTradeOrder.getTicker()).thenReturn(mockTicker);
        when(mockTicker.getSymbol()).thenReturn("SOL-USD");
        when(mockTradeOrder.getTradeDirection()).thenReturn(TradeDirection.BUY);
        when(mockTradeOrder.getSize()).thenReturn(new BigDecimal("10.0"));
        when(mockTradeOrder.getType()).thenReturn(OrderTicket.Type.STOP);

        // Execute
        ParadexOrder result = translator.translateOrder(mockTradeOrder);

        // Verify
        assertNotNull(result);
        assertEquals("CLIENT789", result.getClientId());
        assertEquals("SOL-USD", result.getTicker());
        assertEquals(Side.BUY, result.getSide());
        assertEquals(new BigDecimal("10.0"), result.getSize());
        assertEquals(OrderType.STOP, result.getOrderType());
    }

    @Test
    void testTranslateTradeOrderToParadexOrder_UnsupportedOrderType() {
        // Setup - using a hypothetical unsupported order type
        when(mockTradeOrder.getClientOrderId()).thenReturn("CLIENT999");
        when(mockTradeOrder.getTicker()).thenReturn(mockTicker);
        when(mockTicker.getSymbol()).thenReturn("ADA-USD");
        when(mockTradeOrder.getTradeDirection()).thenReturn(TradeDirection.BUY);
        when(mockTradeOrder.getSize()).thenReturn(new BigDecimal("100.0"));

        // Create a mock for an unsupported order type
        OrderTicket.Type unsupportedType = mock(OrderTicket.Type.class);
        when(unsupportedType.toString()).thenReturn("UNSUPPORTED_TYPE");
        when(mockTradeOrder.getType()).thenReturn(unsupportedType);

        // Execute & Verify
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> translator.translateOrder(mockTradeOrder));

        assertTrue(exception.getMessage().contains("UNSUPPORTED_TYPE"));
        assertTrue(exception.getMessage().contains("is not supported"));
    }

    @Test
    void testTranslateOrdersList_EmptyList() {
        // Execute
        List<OrderTicket> result = translator.translateOrders(Collections.emptyList());

        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testTranslateOrdersList_MultipleOrders() {
        // Setup
        ParadexOrder order1 = mock(ParadexOrder.class);
        ParadexOrder order2 = mock(ParadexOrder.class);
        List<ParadexOrder> paradexOrders = Arrays.asList(order1, order2);

        // Execute
        List<OrderTicket> result = translator.translateOrders(paradexOrders);

        // Verify
        assertNotNull(result);
        assertEquals(2, result.size());
        // Since translateOrder(ParadexOrder) returns a new TradeOrder, we verify we get
        // TradeOrder instances
        result.forEach(order -> assertInstanceOf(OrderTicket.class, order));
    }
}