package com.fueledbychai.broker.lighter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fueledbychai.broker.BrokerAccountInfoListener;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.ResponseException;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.lighter.common.api.ILighterRestApi;
import com.fueledbychai.lighter.common.api.ILighterWebSocketApi;
import com.fueledbychai.lighter.common.api.order.LighterCreateOrderRequest;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStats;
import com.fueledbychai.lighter.common.api.ws.model.LighterAccountStatsUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrder;
import com.fueledbychai.lighter.common.api.ws.model.LighterOrdersUpdate;
import com.fueledbychai.lighter.common.api.ws.model.LighterSendTxResponse;
import com.fueledbychai.lighter.common.api.ws.model.LighterTrade;
import com.fueledbychai.lighter.common.api.ws.model.LighterTradesUpdate;

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
    void placeOrder_ReusesLocalNonceCounterAcrossOrders() {
        OrderTicket firstOrder = buildLimitOrder("101");
        OrderTicket secondOrder = buildLimitOrder("102");

        List<Long> usedNonces = new ArrayList<>();
        when(mockRestApi.getNextNonce(255L, 3)).thenReturn(99L);
        when(mockTranslator.translateCreateOrder(any(OrderTicket.class), eq(255L), eq(3), anyLong()))
                .thenAnswer(invocation -> {
                    long nonce = invocation.getArgument(3, Long.class).longValue();
                    usedNonces.add(Long.valueOf(nonce));
                    LighterCreateOrderRequest request = new LighterCreateOrderRequest();
                    request.setMarketIndex(7);
                    request.setNonce(nonce);
                    return request;
                });
        when(mockWebSocketApi.submitOrder(any(LighterCreateOrderRequest.class)))
                .thenReturn(new LighterSendTxResponse("1", 200, "ok", "{}"));

        BrokerRequestResult firstResult = broker.placeOrder(firstOrder);
        BrokerRequestResult secondResult = broker.placeOrder(secondOrder);

        assertTrue(firstResult.isSuccess());
        assertTrue(secondResult.isSuccess());
        assertEquals(List.of(Long.valueOf(99L), Long.valueOf(100L)), usedNonces);
        verify(mockRestApi, times(1)).getNextNonce(255L, 3);
    }

    @Test
    void placeOrder_InvalidNonceResponseRefreshesAndRetries() {
        OrderTicket order = buildLimitOrder("103");

        LighterCreateOrderRequest firstRequest = new LighterCreateOrderRequest();
        firstRequest.setMarketIndex(7);
        firstRequest.setNonce(99L);

        LighterCreateOrderRequest retryRequest = new LighterCreateOrderRequest();
        retryRequest.setMarketIndex(7);
        retryRequest.setNonce(120L);

        when(mockRestApi.getNextNonce(255L, 3)).thenReturn(99L, 120L);
        when(mockTranslator.translateCreateOrder(order, 255L, 3, 99L)).thenReturn(firstRequest);
        when(mockTranslator.translateCreateOrder(order, 255L, 3, 120L)).thenReturn(retryRequest);
        when(mockWebSocketApi.submitOrder(firstRequest))
                .thenReturn(new LighterSendTxResponse("1", 400, "invalid nonce", "{}"));
        when(mockWebSocketApi.submitOrder(retryRequest)).thenReturn(new LighterSendTxResponse("2", 200, "ok", "{}"));

        BrokerRequestResult result = broker.placeOrder(order);

        assertTrue(result.isSuccess());
        verify(mockRestApi, times(2)).getNextNonce(255L, 3);
        verify(mockWebSocketApi).submitOrder(firstRequest);
        verify(mockWebSocketApi).submitOrder(retryRequest);
    }

    @Test
    void connect_PrefetchesNonceOnStartup() {
        broker.connected = false;

        when(mockRestApi.getApiToken(255L)).thenReturn("token");
        when(mockRestApi.getNextNonce(255L, 3)).thenReturn(501L);

        broker.connect();

        assertTrue(broker.isConnected());
        verify(mockRestApi).getApiToken(255L);
        verify(mockRestApi).getNextNonce(255L, 3);
    }

    @Test
    void connect_SubscribesAccountOrdersOnceUsingSingleStream() {
        broker.connected = false;

        when(mockRestApi.getApiToken(255L)).thenReturn("token");
        when(mockRestApi.getNextNonce(255L, 3)).thenReturn(501L);

        broker.connect();

        verify(mockWebSocketApi, times(1)).subscribeAccountOrders(eq(0), eq(255L), eq("token"), any());
    }

    @Test
    void connect_ResolvesAccountIndexFromAddressWhenConfiguredIndexInvalid() {
        LighterBroker reconnectingBroker = spy(new LighterBroker(mockRestApi, mockWebSocketApi, mockTranslator, 999L, 3));
        reconnectingBroker.connected = false;
        doReturn("0xabc123").when(reconnectingBroker).resolveConfiguredAccountAddress();

        when(mockRestApi.getApiToken(999L))
                .thenThrow(new RuntimeException("Unexpected code 401: Unauthorized",
                        new ResponseException("Unable to create Lighter API token: invalid auth: couldnt find account",
                                401)));
        when(mockRestApi.resolveAccountIndex("0xabc123")).thenReturn(255L);
        when(mockRestApi.getApiToken(255L)).thenReturn("token");
        when(mockRestApi.getNextNonce(255L, 3)).thenReturn(501L);

        reconnectingBroker.connect();

        assertTrue(reconnectingBroker.isConnected());
        assertEquals(255L, reconnectingBroker.accountIndex);
        verify(mockRestApi).getApiToken(999L);
        verify(mockRestApi).resolveAccountIndex("0xabc123");
        verify(reconnectingBroker).publishResolvedAccountIndexToConfiguration(255L);
        verify(mockRestApi).getApiToken(255L);
        verify(mockRestApi).getNextNonce(255L, 3);
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
        assertEquals("500", broker.clientOrderIdByOrderId.get("100"));
    }

    @Test
    void onLighterAccountAllTradesEvent_BackfillsClientOrderIdFromOrderIdCache() throws InterruptedException {
        broker.clientOrderIdByOrderId.put("100", "500");

        Fill fill = new Fill();
        fill.setOrderId("100");
        fill.setSize(new BigDecimal("0.25"));

        when(mockTranslator.translateFill(any(LighterTrade.class), eq(255L))).thenReturn(fill);

        LighterTradesUpdate update = new LighterTradesUpdate("account_all_trades/255", "update/account_all_trades",
                List.of(new LighterTrade()));

        AtomicReference<Fill> receivedFill = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        broker.addFillEventListener(received -> {
            receivedFill.set(received);
            latch.countDown();
        });

        broker.onLighterAccountAllTradesEvent(update);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(receivedFill.get());
        assertEquals("500", receivedFill.get().getClientOrderId());
    }

    private OrderTicket buildLimitOrder(String clientOrderId) {
        Ticker ticker = new Ticker("BTC");
        ticker.setId("7");
        ticker.setMinimumTickSize(new BigDecimal("0.1"));
        ticker.setOrderSizeIncrement(new BigDecimal("0.001"));

        OrderTicket order = new OrderTicket();
        order.setTicker(ticker);
        order.setClientOrderId(clientOrderId);
        order.setType(OrderTicket.Type.LIMIT);
        order.setDirection(TradeDirection.BUY);
        order.setSize(new BigDecimal("0.01"));
        order.setLimitPrice(new BigDecimal("70000.0"));
        return order;
    }
}
