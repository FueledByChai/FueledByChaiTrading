package com.fueledbychai.broker.aster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.aster.common.api.IAsterRestApi;
import com.fueledbychai.aster.common.api.IAsterWebSocketApi;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.order.OrderTicket;
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

        private BrokerRequestResult validateOrderForTest(OrderTicket order) {
            return validateOrder(order);
        }
    }
}
