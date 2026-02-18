package com.fueledbychai.broker.lighter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.broker.BrokerAccountInfoListener;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.ILighterRestApi;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStats;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStatsUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrdersUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterSendTxResponse;

@ExtendWith(MockitoExtension.class)
class LighterBrokerTest {

    @Mock
    private ILighterRestApi mockRestApi;

    @Mock
    private ILighterWebSocketApi mockWebSocketApi;

    @Mock
    private ILighterTranslator mockTranslator;

    private LighterBroker broker;

    @BeforeEach
    void setUp() {
        broker = new LighterBroker(mockRestApi, mockWebSocketApi, mockTranslator, 255L, 3);
        broker.connected = true;
    }

    @Test
    void placeOrder_TranslatesAndSubmitsOrder() {
        Ticker ticker = new Ticker("BTC");
        ticker.setId("7");
        ticker.setMinimumTickSize(new BigDecimal("0.1"));
        ticker.setOrderSizeIncrement(new BigDecimal("0.001"));

        OrderTicket order = new OrderTicket();
        order.setTicker(ticker);
        order.setClientOrderId("12345");
        order.setType(OrderTicket.Type.LIMIT);
        order.setDirection(TradeDirection.BUY);
        order.setSize(new BigDecimal("0.01"));
        order.setLimitPrice(new BigDecimal("70000.0"));

        LighterCreateOrderRequest request = new LighterCreateOrderRequest();
        request.setMarketIndex(7);
        request.setClientOrderIndex(12345L);

        when(mockRestApi.getNextNonce(255L, 3)).thenReturn(99L);
        when(mockTranslator.translateCreateOrder(order, 255L, 3, 99L)).thenReturn(request);
        when(mockWebSocketApi.submitOrder(request)).thenReturn(new LighterSendTxResponse("1", 200, "ok", "{}"));

        BrokerRequestResult result = broker.placeOrder(order);

        assertTrue(result.isSuccess());
        verify(mockRestApi).getNextNonce(255L, 3);
        verify(mockTranslator).translateCreateOrder(order, 255L, 3, 99L);
        verify(mockWebSocketApi).submitOrder(request);
    }

    @Test
    void onLighterAccountStatsEvent_FiresEquityAndFundsCallbacks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        broker.addBrokerAccountInfoListener(new BrokerAccountInfoListener() {
            @Override
            public void availableFundsUpdated(double funds) {
                if (funds == 50.5d) {
                    latch.countDown();
                }
            }

            @Override
            public void accountEquityUpdated(double equity) {
                if (equity == 100.25d) {
                    latch.countDown();
                }
            }
        });

        LighterAccountStats stats = new LighterAccountStats();
        stats.setPortfolioValue(new BigDecimal("100.25"));
        stats.setAvailableBalance(new BigDecimal("50.5"));

        LighterAccountStatsUpdate update = new LighterAccountStatsUpdate("user_stats/255", "update/user_stats", 255L,
                stats);

        broker.onLighterAccountStatsEvent(update);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void onLighterAccountOrdersEvent_MovesFilledOrderToCompletedRegistry() {
        Ticker ticker = new Ticker("BTC");
        ticker.setId("7");

        LighterOrder lighterOrder = new LighterOrder();
        lighterOrder.setOrderId("100");

        OrderTicket translatedOrder = new OrderTicket();
        translatedOrder.setOrderId("100");
        translatedOrder.setClientOrderId("500");
        translatedOrder.setTicker(ticker);
        translatedOrder.setSize(new BigDecimal("1.0"));

        OrderStatus status = new OrderStatus(OrderStatus.Status.FILLED, "100", new BigDecimal("1.0"),
                BigDecimal.ZERO, new BigDecimal("70000.0"), ticker, broker.getCurrentTime());
        status.setClientOrderId("500");

        when(mockTranslator.translateOrder(lighterOrder)).thenReturn(translatedOrder);
        when(mockTranslator.translateOrderStatus(lighterOrder)).thenReturn(status);

        LighterOrdersUpdate update = new LighterOrdersUpdate("account_orders/7/255", "update/account_orders", 255L,
                1L, Map.of(Integer.valueOf(7), List.of(lighterOrder)));

        broker.onLighterAccountOrdersEvent(update);

        OrderTicket completed = broker.getOrderRegistry().getCompletedOrderById("100");
        assertNotNull(completed);
        assertEquals(OrderStatus.Status.FILLED, completed.getCurrentStatus());
    }
}
