package com.fueledbychai.broker.grvt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.grvt.common.api.IGrvtRestApi;
import com.fueledbychai.grvt.common.api.IGrvtWebSocketApi;
import com.fueledbychai.util.ITickerRegistry;

class GrvtBrokerAccountSnapshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void publishesFullAccountSummaryFields() throws Exception {
        CapturingGrvtBroker broker = brokerWithSummary("""
                {"total_equity":"1234.56","available_balance":"987.65"}
                """);

        broker.refreshAccountSnapshotFromRest();

        assertEquals(1234.56, broker.equity, 0.000001);
        assertEquals(987.65, broker.availableFunds, 0.000001);
    }

    @Test
    void publishesLiteAccountSummaryFields() throws Exception {
        CapturingGrvtBroker broker = brokerWithSummary("""
                {"te":"42.25","ab":"40.00"}
                """);

        broker.refreshAccountSnapshotFromRest();

        assertEquals(42.25, broker.equity, 0.000001);
        assertEquals(40.00, broker.availableFunds, 0.000001);
    }

    @Test
    void missingBalanceFieldsDoesNotPublishZero() throws Exception {
        CapturingGrvtBroker broker = brokerWithSummary("""
                {"sub_account_id":"123"}
                """);

        broker.refreshAccountSnapshotFromRest();

        assertNull(broker.equity);
        assertNull(broker.availableFunds);
    }

    private static CapturingGrvtBroker brokerWithSummary(String json) throws Exception {
        IGrvtRestApi restApi = mock(IGrvtRestApi.class);
        when(restApi.getAccountSummary()).thenReturn(MAPPER.readTree(json));
        return new CapturingGrvtBroker(restApi, mock(IGrvtWebSocketApi.class), mock(ITickerRegistry.class));
    }

    private static final class CapturingGrvtBroker extends GrvtBroker {
        private Double equity;
        private Double availableFunds;

        private CapturingGrvtBroker(IGrvtRestApi restApi, IGrvtWebSocketApi webSocketApi,
                ITickerRegistry tickerRegistry) {
            super(restApi, webSocketApi, tickerRegistry, null, "123");
        }

        @Override
        protected void fireAccountEquityUpdated(double equity) {
            this.equity = equity;
        }

        @Override
        protected void fireAvailableFundsUpdated(double availableFunds) {
            this.availableFunds = availableFunds;
        }
    }
}
