package com.fueledbychai.broker.paradex;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.ResponseException;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.common.api.IParadexRestApi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class demonstrating how to use ResilientParadexBroker and its resilience
 * features.
 */
public class ResilientParadexBrokerTest {

    @Test
    public void testPlaceOrderWithRetryOnIOException() {
        // Create a mock REST API that fails twice then succeeds
        IParadexRestApi mockRestApi = mock(IParadexRestApi.class);
        when(mockRestApi.placeOrder(anyString(), any()))
                .thenThrow(new FueledByChaiException("Network error", new java.io.IOException("Connection timeout")))
                .thenThrow(new FueledByChaiException("Network error", new java.io.IOException("Connection timeout")))
                .thenReturn("ORDER123");

        when(mockRestApi.getJwtToken()).thenReturn("test-jwt-token");

        // Create ParadexBroker with mock API
        ParadexBroker delegate = new ParadexBroker(mockRestApi);

        // Wrap with resilient broker
        ResilientParadexBroker resilientBroker = new ResilientParadexBroker(delegate);

        // Create test order
        OrderTicket order = new OrderTicket();
        order.setTicker(new Ticker("BTC-USD"));
        order.setDirection(TradeDirection.BUY);
        order.setSize(new BigDecimal("1.0"));
        order.setLimitPrice(new BigDecimal("50000.00"));
        order.setType(OrderTicket.Type.LIMIT);

        // This should succeed after retries
        assertDoesNotThrow(() -> resilientBroker.placeOrder(order));

        // Verify the order was placed successfully after retries
        assertEquals("ORDER123", order.getOrderId());

        // Verify that the REST API was called 3 times (2 failures + 1 success)
        verify(mockRestApi, times(3)).placeOrder(anyString(), any());
    }

    @Test
    public void testPlaceOrderWithRetryOnHttpServerError() {
        // Create a mock REST API that fails with server error then succeeds
        IParadexRestApi mockRestApi = mock(IParadexRestApi.class);
        when(mockRestApi.placeOrder(anyString(), any())).thenThrow(new ResponseException("Internal Server Error", 500))
                .thenReturn("ORDER456");

        when(mockRestApi.getJwtToken()).thenReturn("test-jwt-token");

        // Create ParadexBroker with mock API
        ParadexBroker delegate = new ParadexBroker(mockRestApi);

        // Wrap with resilient broker
        ResilientParadexBroker resilientBroker = new ResilientParadexBroker(delegate);

        // Create test order
        OrderTicket order = new OrderTicket();
        order.setTicker(new Ticker("ETH-USD"));
        order.setDirection(TradeDirection.SELL);
        order.setSize(new BigDecimal("10.0"));
        order.setLimitPrice(new BigDecimal("3000.00"));
        order.setType(OrderTicket.Type.LIMIT);

        // This should succeed after retry
        assertDoesNotThrow(() -> resilientBroker.placeOrder(order));

        // Verify the order was placed successfully after retry
        assertEquals("ORDER456", order.getOrderId());

        // Verify that the REST API was called 2 times (1 failure + 1 success)
        verify(mockRestApi, times(2)).placeOrder(anyString(), any());
    }

    @Test
    public void testPlaceOrderNoRetryOnClientError() {
        // Create a mock REST API that fails with client error (400)
        IParadexRestApi mockRestApi = mock(IParadexRestApi.class);
        when(mockRestApi.placeOrder(anyString(), any())).thenThrow(new ResponseException("Bad Request", 400));

        when(mockRestApi.getJwtToken()).thenReturn("test-jwt-token");

        // Create ParadexBroker with mock API
        ParadexBroker delegate = new ParadexBroker(mockRestApi);

        // Wrap with resilient broker
        ResilientParadexBroker resilientBroker = new ResilientParadexBroker(delegate);

        // Create test order
        OrderTicket order = new OrderTicket();
        order.setTicker(new Ticker("SOL-USD"));
        order.setDirection(TradeDirection.BUY);
        order.setSize(new BigDecimal("100.0"));
        order.setLimitPrice(new BigDecimal("100.00"));
        order.setType(OrderTicket.Type.LIMIT);

        // This should fail immediately without retry
        assertThrows(ResponseException.class, () -> resilientBroker.placeOrder(order));

        // Verify that the REST API was called only once (no retries for client errors)
        verify(mockRestApi, times(1)).placeOrder(anyString(), any());
    }

    @Test
    public void testGetCircuitBreakerState() {
        // Create a working mock REST API
        IParadexRestApi mockRestApi = mock(IParadexRestApi.class);
        when(mockRestApi.getJwtToken()).thenReturn("test-jwt-token");

        // Create ParadexBroker with mock API
        ParadexBroker delegate = new ParadexBroker(mockRestApi);

        // Wrap with resilient broker
        ResilientParadexBroker resilientBroker = new ResilientParadexBroker(delegate);

        // Initially circuit breaker should be closed
        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED,
                resilientBroker.getPlaceOrderCircuitBreakerState());

        // Test retry metrics access
        assertNotNull(resilientBroker.getPlaceOrderRetryMetrics());
        assertNotNull(resilientBroker.getCancelOrderRetryMetrics());
    }

    @Test
    public void testPlaceOrderWithReconciliationAfterFailure() {
        // Create a mock REST API that fails on placeOrder but succeeds on
        // getOrderByClientOrderId
        IParadexRestApi mockRestApi = mock(IParadexRestApi.class);

        // placeOrder always fails
        when(mockRestApi.placeOrder(anyString(), any())).thenThrow(new ResponseException("Internal Server Error", 500));

        // But the order actually exists when we check by client order ID
        when(mockRestApi.getOrderByClientOrderId(anyString(), anyString()))
                .thenReturn(createMockParadexOrder("SERVER_ORDER_123", "CLIENT_ORDER_456"));

        when(mockRestApi.getJwtToken()).thenReturn("test-jwt-token");

        // Create ParadexBroker with mock API
        ParadexBroker delegate = new ParadexBroker(mockRestApi);

        // Wrap with resilient broker
        ResilientParadexBroker resilientBroker = new ResilientParadexBroker(delegate);

        // Create test order with client order ID
        OrderTicket order = new OrderTicket();
        order.setTicker(new Ticker("BTC-USD"));
        order.setDirection(TradeDirection.BUY);
        order.setSize(new BigDecimal("1.0"));
        order.setLimitPrice(new BigDecimal("50000.00"));
        order.setType(OrderTicket.Type.LIMIT);
        order.setClientOrderId("CLIENT_ORDER_456");

        // This should succeed via reconciliation despite placeOrder failures
        assertDoesNotThrow(() -> resilientBroker.placeOrder(order));

        // Verify the order was reconciled with server data
        assertEquals("SERVER_ORDER_123", order.getOrderId());

        // Verify that reconciliation was attempted by checking getOrderByClientOrderId
        // was called
        verify(mockRestApi, atLeastOnce()).getOrderByClientOrderId(anyString(), eq("CLIENT_ORDER_456"));
    }

    @Test
    public void testPlaceOrderFailsWhenReconciliationAlsoFails() {
        // Create a mock REST API that fails on both placeOrder and
        // getOrderByClientOrderId
        IParadexRestApi mockRestApi = mock(IParadexRestApi.class);

        // placeOrder fails
        when(mockRestApi.placeOrder(anyString(), any())).thenThrow(new ResponseException("Internal Server Error", 500));

        // getOrderByClientOrderId also fails (order not found)
        when(mockRestApi.getOrderByClientOrderId(anyString(), anyString())).thenReturn(null);

        when(mockRestApi.getJwtToken()).thenReturn("test-jwt-token");

        // Create ParadexBroker with mock API
        ParadexBroker delegate = new ParadexBroker(mockRestApi);

        // Wrap with resilient broker
        ResilientParadexBroker resilientBroker = new ResilientParadexBroker(delegate);

        // Create test order
        OrderTicket order = new OrderTicket();
        order.setTicker(new Ticker("ETH-USD"));
        order.setDirection(TradeDirection.SELL);
        order.setSize(new BigDecimal("5.0"));
        order.setLimitPrice(new BigDecimal("3000.00"));
        order.setType(OrderTicket.Type.LIMIT);
        order.setClientOrderId("CLIENT_ORDER_789");

        // This should fail since both placeOrder and reconciliation fail
        assertThrows(RuntimeException.class, () -> resilientBroker.placeOrder(order));

        // Verify that reconciliation was attempted
        verify(mockRestApi, atLeastOnce()).getOrderByClientOrderId(anyString(), eq("CLIENT_ORDER_789"));
    }

    /**
     * Helper method to create a mock ParadexOrder for testing
     */
    private com.fueledbychai.paradex.common.api.order.ParadexOrder createMockParadexOrder(String orderId,
            String clientOrderId) {
        com.fueledbychai.paradex.common.api.order.ParadexOrder mockOrder = mock(
                com.fueledbychai.paradex.common.api.order.ParadexOrder.class);
        when(mockOrder.getOrderId()).thenReturn(orderId);
        when(mockOrder.getClientId()).thenReturn(clientOrderId);
        return mockOrder;
    }
}