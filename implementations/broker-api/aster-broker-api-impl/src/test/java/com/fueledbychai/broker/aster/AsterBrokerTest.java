package com.fueledbychai.broker.aster;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueledbychai.aster.common.api.IAsterRestApi;
import com.fueledbychai.aster.common.api.IAsterWebSocketApi;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.util.ITickerRegistry;

class AsterBrokerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void connect_PublishesInitialAccountSnapshot() throws Exception {
        IAsterRestApi restApi = mock(IAsterRestApi.class);
        IAsterWebSocketApi webSocketApi = mock(IAsterWebSocketApi.class);
        ITickerRegistry tickerRegistry = mock(ITickerRegistry.class);

        when(restApi.isPublicApiOnly()).thenReturn(false);
        when(restApi.startUserDataStream()).thenReturn("listen-key");
        when(restApi.getOpenOrders(null)).thenReturn(OBJECT_MAPPER.createArrayNode());
        when(restApi.getPositionRisk(null)).thenReturn(OBJECT_MAPPER.createArrayNode());
        when(restApi.getAccountInformation()).thenReturn(json("""
                {
                  "totalMarginBalance": "150.25",
                  "availableBalance": "90.5",
                  "totalWalletBalance": "140.0"
                }
                """));

        TestableAsterBroker broker = new TestableAsterBroker(restApi, webSocketApi, tickerRegistry);

        broker.connect();

        assertTrue(broker.isConnected());
        assertEquals(150.25d, broker.lastAccountEquity);
        assertEquals(90.5d, broker.lastAvailableFunds);
        verify(webSocketApi).connect();
        verify(webSocketApi).subscribeUserData(eq("listen-key"), any());
        verify(restApi).getAccountInformation();
    }

    @Test
    void validateOrder_RejectsSpotTicker() {
        IAsterRestApi restApi = mock(IAsterRestApi.class);
        IAsterWebSocketApi webSocketApi = mock(IAsterWebSocketApi.class);
        ITickerRegistry tickerRegistry = mock(ITickerRegistry.class);
        TestableAsterBroker broker = new TestableAsterBroker(restApi, webSocketApi, tickerRegistry);

        OrderTicket order = new OrderTicket();
        order.setTicker(new Ticker("BNBUSDT")
                .setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.CRYPTO_SPOT));
        order.setSize(BigDecimal.ONE);

        BrokerRequestResult result = broker.validateOrderForTest(order);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("perpetual futures only"));
    }

    @Test
    void placeOrder_FloorsQuantityWithPrecisionExceedingStepSizeBeforeSendingToAster() {
        IAsterRestApi restApi = mock(IAsterRestApi.class);
        IAsterWebSocketApi webSocketApi = mock(IAsterWebSocketApi.class);
        ITickerRegistry tickerRegistry = mock(ITickerRegistry.class);
        Ticker venueTicker = venueTicker("PIPPINUSDT", "1", "1", "1", "0.1");
        stubVenueTickerLookup(tickerRegistry, venueTicker);
        stubPlaceOrderEcho(restApi);

        TestableAsterBroker broker = connectedBroker(restApi, webSocketApi, tickerRegistry);
        OrderTicket order = marketOrder(rawTicker("PIPPINUSDT"), "149.8");

        BrokerRequestResult result = broker.placeOrder(order);

        assertTrue(result.isSuccess());
        assertSame(venueTicker, order.getTicker());
        assertEquals(new BigDecimal("149"), order.getSize());

        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restApi).placeOrder(paramsCaptor.capture());
        assertEquals("149", paramsCaptor.getValue().get("quantity"));
    }

    @Test
    void placeOrder_FloorsQuantityToVenueStepSizeBeforeSending() {
        IAsterRestApi restApi = mock(IAsterRestApi.class);
        IAsterWebSocketApi webSocketApi = mock(IAsterWebSocketApi.class);
        ITickerRegistry tickerRegistry = mock(ITickerRegistry.class);
        Ticker venueTicker = venueTicker("PIPPINUSDT", "0.1", "0.1", "1", "0.1");
        stubVenueTickerLookup(tickerRegistry, venueTicker);
        stubPlaceOrderEcho(restApi);

        TestableAsterBroker broker = connectedBroker(restApi, webSocketApi, tickerRegistry);
        OrderTicket order = marketOrder(rawTicker("PIPPINUSDT"), "149.87");

        BrokerRequestResult result = broker.placeOrder(order);

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("149.8"), order.getSize());

        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restApi).placeOrder(paramsCaptor.capture());
        assertEquals("149.8", paramsCaptor.getValue().get("quantity"));
    }

    @Test
    void placeOrder_RejectsBelowMinQuantityAfterFlooringWithoutRestCall() {
        IAsterRestApi restApi = mock(IAsterRestApi.class);
        IAsterWebSocketApi webSocketApi = mock(IAsterWebSocketApi.class);
        ITickerRegistry tickerRegistry = mock(ITickerRegistry.class);
        Ticker venueTicker = venueTicker("PIPPINUSDT", "0.1", "0.5", "1", "0.1");
        stubVenueTickerLookup(tickerRegistry, venueTicker);

        TestableAsterBroker broker = connectedBroker(restApi, webSocketApi, tickerRegistry);
        OrderTicket order = marketOrder(rawTicker("PIPPINUSDT"), "0.49");

        BrokerRequestResult result = broker.placeOrder(order);

        assertFalse(result.isSuccess());
        assertEquals(BrokerRequestResult.FailureType.INVALID_SIZE, result.getFailureType());
        assertTrue(result.getMessage().contains("minimum order size"));
        verify(restApi, never()).placeOrder(any());
    }

    @Test
    void placeOrder_RejectsBelowMinNotionalAfterNormalizationWithoutRestCall() {
        IAsterRestApi restApi = mock(IAsterRestApi.class);
        IAsterWebSocketApi webSocketApi = mock(IAsterWebSocketApi.class);
        ITickerRegistry tickerRegistry = mock(ITickerRegistry.class);
        Ticker venueTicker = venueTicker("PIPPINUSDT", "0.1", "0.1", "10", "0.1");
        stubVenueTickerLookup(tickerRegistry, venueTicker);

        TestableAsterBroker broker = connectedBroker(restApi, webSocketApi, tickerRegistry);
        OrderTicket order = limitOrder(rawTicker("PIPPINUSDT"), "1.09", "9.99");

        BrokerRequestResult result = broker.placeOrder(order);

        assertFalse(result.isSuccess());
        assertEquals(BrokerRequestResult.FailureType.VALIDATION_FAILED, result.getFailureType());
        assertTrue(result.getMessage().contains("minimum notional"));
        verify(restApi, never()).placeOrder(any());
    }

    @Test
    void placeOrder_NormalizesPriceTicksForLimitStopAndStopLimitOrders() {
        IAsterRestApi restApi = mock(IAsterRestApi.class);
        IAsterWebSocketApi webSocketApi = mock(IAsterWebSocketApi.class);
        ITickerRegistry tickerRegistry = mock(ITickerRegistry.class);
        Ticker venueTicker = venueTicker("PIPPINUSDT", "0.1", "0.1", "1", "0.25");
        stubVenueTickerLookup(tickerRegistry, venueTicker);
        stubPlaceOrderEcho(restApi);

        TestableAsterBroker broker = connectedBroker(restApi, webSocketApi, tickerRegistry);

        BrokerRequestResult limitResult = broker.placeOrder(limitOrder(rawTicker("PIPPINUSDT"), "2.04", "10.87"));
        BrokerRequestResult stopResult = broker.placeOrder(stopOrder(rawTicker("PIPPINUSDT"), "2.04", "10.87"));
        BrokerRequestResult stopLimitResult = broker.placeOrder(
                stopLimitOrder(rawTicker("PIPPINUSDT"), "2.04", "10.99", "10.88"));

        assertTrue(limitResult.isSuccess());
        assertTrue(stopResult.isSuccess());
        assertTrue(stopLimitResult.isSuccess());

        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restApi, times(3)).placeOrder(paramsCaptor.capture());
        List<Map<String, String>> capturedParams = paramsCaptor.getAllValues();

        assertEquals("2", capturedParams.get(0).get("quantity"));
        assertEquals("10.75", capturedParams.get(0).get("price"));

        assertEquals("2", capturedParams.get(1).get("quantity"));
        assertEquals("10.75", capturedParams.get(1).get("stopPrice"));

        assertEquals("2", capturedParams.get(2).get("quantity"));
        assertEquals("10.75", capturedParams.get(2).get("price"));
        assertEquals("10.75", capturedParams.get(2).get("stopPrice"));
    }

    @Test
    void modifyOrder_UsesTheSameNormalizationPath() {
        IAsterRestApi restApi = mock(IAsterRestApi.class);
        IAsterWebSocketApi webSocketApi = mock(IAsterWebSocketApi.class);
        ITickerRegistry tickerRegistry = mock(ITickerRegistry.class);
        Ticker venueTicker = venueTicker("PIPPINUSDT", "1", "1", "1", "0.25");
        stubVenueTickerLookup(tickerRegistry, venueTicker);
        stubPlaceOrderEcho(restApi);

        TestableAsterBroker broker = connectedBroker(restApi, webSocketApi, tickerRegistry);
        OrderTicket existingOrder = limitOrder(venueTicker, "10", "10.25");
        existingOrder.setOrderId("existing-order");
        existingOrder.setClientOrderId("existing-client");
        broker.getOrderRegistry().addOpenOrder(existingOrder);

        when(restApi.cancelOrder(eq("PIPPINUSDT"), eq("existing-order"), eq("existing-client")))
                .thenReturn(cancelResponse(existingOrder));

        OrderTicket modifyOrder = limitOrder(rawTicker("PIPPINUSDT"), "149.8", "10.87");
        modifyOrder.setOrderId("existing-order");
        modifyOrder.setClientOrderId("existing-client");

        BrokerRequestResult result = broker.modifyOrder(modifyOrder);

        assertTrue(result.isSuccess());
        verify(restApi).cancelOrder("PIPPINUSDT", "existing-order", "existing-client");

        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restApi).placeOrder(paramsCaptor.capture());
        assertEquals("149", paramsCaptor.getValue().get("quantity"));
        assertEquals("10.75", paramsCaptor.getValue().get("price"));
    }

    private static TestableAsterBroker connectedBroker(IAsterRestApi restApi, IAsterWebSocketApi webSocketApi,
            ITickerRegistry tickerRegistry) {
        TestableAsterBroker broker = new TestableAsterBroker(restApi, webSocketApi, tickerRegistry);
        broker.setConnectedForTest(true);
        return broker;
    }

    private static void stubVenueTickerLookup(ITickerRegistry tickerRegistry, Ticker venueTicker) {
        when(tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, venueTicker.getSymbol()))
                .thenReturn(venueTicker);
    }

    private static void stubPlaceOrderEcho(IAsterRestApi restApi) {
        when(restApi.placeOrder(any())).thenAnswer(invocation -> orderResponse(invocation.getArgument(0), "NEW"));
    }

    private static JsonNode orderResponse(Map<String, String> params, String status) {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("symbol", params.get("symbol"));
        response.put("orderId", params.getOrDefault("newClientOrderId", "aster-order"));
        response.put("clientOrderId", params.getOrDefault("newClientOrderId", "aster-client"));
        response.put("side", params.getOrDefault("side", "BUY"));
        response.put("type", params.getOrDefault("type", "MARKET"));
        if (params.containsKey("timeInForce")) {
            response.put("timeInForce", params.get("timeInForce"));
        }
        if (params.containsKey("price")) {
            response.put("price", params.get("price"));
        }
        if (params.containsKey("quantity")) {
            response.put("origQty", params.get("quantity"));
        }
        if (params.containsKey("stopPrice")) {
            response.put("stopPrice", params.get("stopPrice"));
        }
        response.put("executedQty", "0");
        response.put("avgPrice", "0");
        response.put("status", status);
        response.put("updateTime", 1710000000000L);
        return response;
    }

    private static JsonNode cancelResponse(OrderTicket order) {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("symbol", order.getTicker().getSymbol());
        response.put("orderId", order.getOrderId());
        response.put("clientOrderId", order.getClientOrderId());
        response.put("side", order.isBuyOrder() ? "BUY" : "SELL");
        response.put("type", order.getType().name());
        response.put("timeInForce", "GTC");
        response.put("price", order.getLimitPrice() == null ? "0" : order.getLimitPrice().toPlainString());
        response.put("origQty", order.getSize().toPlainString());
        response.put("executedQty", "0");
        response.put("avgPrice", "0");
        response.put("status", "CANCELED");
        response.put("updateTime", 1710000000000L);
        return response;
    }

    private static Ticker rawTicker(String symbol) {
        return new Ticker(symbol)
                .setExchange(Exchange.ASTER)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
    }

    private static Ticker venueTicker(String symbol, String stepSize, String minQty, String minNotional,
            String tickSize) {
        return rawTicker(symbol)
                .setOrderSizeIncrement(new BigDecimal(stepSize))
                .setMinimumOrderSize(new BigDecimal(minQty))
                .setMinimumOrderSizeNotional(new BigDecimal(minNotional))
                .setMinimumTickSize(new BigDecimal(tickSize));
    }

    private static OrderTicket marketOrder(Ticker ticker, String size) {
        return baseOrder(ticker, size).setType(OrderTicket.Type.MARKET);
    }

    private static OrderTicket limitOrder(Ticker ticker, String size, String limitPrice) {
        return baseOrder(ticker, size)
                .setType(OrderTicket.Type.LIMIT)
                .setLimitPrice(new BigDecimal(limitPrice));
    }

    private static OrderTicket stopOrder(Ticker ticker, String size, String stopPrice) {
        return baseOrder(ticker, size)
                .setType(OrderTicket.Type.STOP)
                .setStopPrice(new BigDecimal(stopPrice));
    }

    private static OrderTicket stopLimitOrder(Ticker ticker, String size, String limitPrice, String stopPrice) {
        return baseOrder(ticker, size)
                .setType(OrderTicket.Type.STOP_LIMIT)
                .setLimitPrice(new BigDecimal(limitPrice))
                .setStopPrice(new BigDecimal(stopPrice));
    }

    private static OrderTicket baseOrder(Ticker ticker, String size) {
        return new OrderTicket()
                .setTicker(ticker)
                .setTradeDirection(TradeDirection.BUY)
                .setSize(new BigDecimal(size));
    }

    private static JsonNode json(String value) throws Exception {
        return OBJECT_MAPPER.readTree(value);
    }

    private static final class TestableAsterBroker extends AsterBroker {
        private double lastAccountEquity;
        private double lastAvailableFunds;

        private TestableAsterBroker(IAsterRestApi restApi, IAsterWebSocketApi webSocketApi,
                ITickerRegistry tickerRegistry) {
            super(restApi, webSocketApi, tickerRegistry);
        }

        @Override
        protected synchronized void startKeepAliveTask() {
            // No-op for unit tests.
        }

        @Override
        protected synchronized void stopKeepAliveTask() {
            // No-op for unit tests.
        }

        @Override
        protected void fireAccountEquityUpdated(double equity) {
            this.lastAccountEquity = equity;
        }

        @Override
        protected void fireAvailableFundsUpdated(double availableFunds) {
            this.lastAvailableFunds = availableFunds;
        }

        private void setConnectedForTest(boolean connected) {
            this.connected = connected;
        }

        private BrokerRequestResult validateOrderForTest(OrderTicket order) {
            return validateOrder(order);
        }
    }
}
