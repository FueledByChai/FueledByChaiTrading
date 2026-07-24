package com.fueledbychai.broker.grvt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.grvt.common.api.IGrvtRestApi;
import com.fueledbychai.grvt.common.api.IGrvtWebSocketApi;
import com.fueledbychai.util.ITickerRegistry;

class GrvtBrokerOrderLifecycleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void placeholderCreateIdIsNotIndexedAlongsideRealWebsocketId() throws Exception {
        Harness harness = harness();

        harness.broker.apply(snapshot("0x00", "client-1"));
        assertNull(harness.broker.getOrderRegistry().getOpenOrderById("0x00"));
        assertNotNull(harness.broker.getOrderRegistry().getOpenOrderByClientId("client-1"));

        harness.broker.apply(snapshot("0x010101-real", "client-1"));
        assertNotNull(harness.broker.getOrderRegistry().getOpenOrderById("0x010101-real"));
        assertEquals(1, harness.broker.getOrderRegistry().getOpenOrders().size());
    }

    @Test
    void statusLookupByClientOrderIdUsesGrvtOrderEndpoint() throws Exception {
        Harness harness = harness();
        harness.broker.setConnected(true);
        when(harness.restApi.getOrder(null, "client-2"))
                .thenReturn(snapshot("0x010101-two", "client-2"));

        OrderTicket result = harness.broker.requestOrderStatusByClientOrderId("client-2");

        assertNotNull(result);
        assertEquals("0x010101-two", result.getOrderId());
        verify(harness.restApi).getOrder(null, "client-2");
    }

    @Test
    void connectFailsClosedWhenAuthenticatedSnapshotCannotBeRead() {
        Harness harness = harness();
        when(harness.restApi.isPublicApiOnly()).thenReturn(false);
        doThrow(new IllegalStateException("HTTP 401"))
                .when(harness.restApi).getOpenOrders();

        assertThrows(IllegalStateException.class, harness.broker::connect);
        assertFalse(harness.broker.isConnected());
        verify(harness.webSocketApi, never()).connectTradeData();
    }

    @Test
    void emptyRestSnapshotActuallyClearsTrackedOpenOrders() throws Exception {
        Harness harness = harness();
        OrderTicket existing = harness.broker.apply(snapshot("0x010101-stale", "client-stale"));
        assertNotNull(existing);
        when(harness.restApi.getOpenOrders()).thenReturn(MAPPER.createArrayNode());

        harness.broker.refreshOpenOrdersFromRest();

        assertEquals(0, harness.broker.getOrderRegistry().getOpenOrders().size());
    }

    private static Harness harness() {
        IGrvtRestApi restApi = mock(IGrvtRestApi.class);
        IGrvtWebSocketApi webSocketApi = mock(IGrvtWebSocketApi.class);
        ITickerRegistry tickerRegistry = mock(ITickerRegistry.class);
        Ticker ticker = new Ticker("STRK_USDT_Perp").setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        when(tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "STRK_USDT_Perp"))
                .thenReturn(ticker);
        when(tickerRegistry.lookupByCommonSymbol(InstrumentType.PERPETUAL_FUTURES, "STRK_USDT_Perp"))
                .thenReturn(ticker);
        return new Harness(restApi, webSocketApi,
                new TestGrvtBroker(restApi, webSocketApi, tickerRegistry));
    }

    private static JsonNode snapshot(String orderId, String clientOrderId) throws Exception {
        return MAPPER.readTree("""
                {
                  "order_id":"%s",
                  "metadata":{"client_order_id":"%s"},
                  "legs":[{"instrument":"STRK_USDT_Perp","size":"1.0",
                           "limit_price":"1.25","is_buying_asset":true}],
                  "state":{"status":"OPEN","traded_size":["0"],
                           "book_size":["1.0"],"update_time":"1784399550000000000"}
                }
                """.formatted(orderId, clientOrderId));
    }

    private record Harness(IGrvtRestApi restApi, IGrvtWebSocketApi webSocketApi,
            TestGrvtBroker broker) {
    }

    private static final class TestGrvtBroker extends GrvtBroker {
        private TestGrvtBroker(IGrvtRestApi restApi, IGrvtWebSocketApi webSocketApi,
                ITickerRegistry tickerRegistry) {
            super(restApi, webSocketApi, tickerRegistry, null, "sub-account");
        }

        private OrderTicket apply(JsonNode snapshot) {
            return applyOrderSnapshot(snapshot, false);
        }

        private void setConnected(boolean connected) {
            this.connected = connected;
        }
    }
}
