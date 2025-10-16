package com.fueledbychai.broker.hyperliquid;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.broker.hyperliquid.translators.ITranslator;
// import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.hyperliquid.HyperliquidUtil;
import com.fueledbychai.hyperliquid.ws.IHyperliquidRestApi;

@ExtendWith(MockitoExtension.class)
public class HyperliquidBrokerTest {

    @Mock
    IHyperliquidRestApi mockRestApi;
    @Mock
    OrderTicket mockOrderTicket;
    @Mock
    ScheduledExecutorService mockScheduler;
    @Mock
    ExecutorService mockExecutor;

    @Mock
    ITranslator mockTranslator;

    HyperliquidBroker broker;

    @BeforeEach
    public void setup() {
        broker = Mockito.spy(new HyperliquidBroker(mockRestApi));
        broker.translator = mockTranslator;
    }

    @Test
    public void testCancelOrderById() {
        broker.connected = true;
        assertThrows(UnsupportedOperationException.class, () -> broker.cancelOrder("orderId"));
    }

    @Test
    public void testCancelOrderByOrderTicket() {
        broker.connected = true;

        assertThrows(UnsupportedOperationException.class, () -> broker.cancelOrder(mockOrderTicket));
    }



    @Test
    public void testGetNextOrderId() {
        assertEquals(HyperliquidUtil.encode128BitHex(1 + ""), broker.getNextOrderId());
    }

    @Test
    public void testConnectSetsConnectedTrue() {

        doNothing().when(broker).startAccountInfoWSClient();
        doNothing().when(broker).startOrderStatusWSClient();
        doNothing().when(broker).startFillWSClient();

        broker.connect();
        assertTrue(broker.connected);
        assertNotNull(broker.orderEventExecutor);
        verify(broker).startAccountInfoWSClient();
        verify(broker).startOrderStatusWSClient();
    }

    @Test
    public void testDisconnectSetsConnectedFalse() {
        doNothing().when(broker).startAccountInfoWSClient();
        doNothing().when(broker).startOrderStatusWSClient();
        doNothing().when(broker).startFillWSClient();
        broker.connect();
        broker.disconnect();
        assertFalse(broker.connected);
    }

    @Test
    public void testUnsupportedMethodsThrow() {
        assertThrows(UnsupportedOperationException.class, () -> broker.isConnected());
        assertThrows(UnsupportedOperationException.class, () -> broker.requestOrderStatus("id"));
        assertThrows(UnsupportedOperationException.class, () -> broker.getOpenOrders());
        assertThrows(UnsupportedOperationException.class, () -> broker.cancelAndReplaceOrder("id", mockOrderTicket));
        assertThrows(UnsupportedOperationException.class, () -> broker.getAllPositions());
    }

    @Test
    public void testCheckConnectedNoop() {
        assertDoesNotThrow(() -> broker.checkConnected());
    }
}
