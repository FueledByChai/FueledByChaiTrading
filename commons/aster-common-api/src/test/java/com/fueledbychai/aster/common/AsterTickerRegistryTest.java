package com.fueledbychai.aster.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.aster.common.api.IAsterRestApi;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.ITickerRegistry;

class AsterTickerRegistryTest {

    @Test
    void initializesPerpetualAndSpotTickers() {
        InstrumentDescriptor perpetualDescriptor = new InstrumentDescriptor(InstrumentType.PERPETUAL_FUTURES,
                Exchange.ASTER, "BTC/USDT", "BTCUSDT", "BTC", "USDT", new BigDecimal("0.001"),
                new BigDecimal("0.1"), 5, new BigDecimal("0.001"), 8, BigDecimal.ONE, 1, "BTCUSDT");
        InstrumentDescriptor spotDescriptor = new InstrumentDescriptor(InstrumentType.CRYPTO_SPOT, Exchange.ASTER,
                "BNB/USDT", "BNBUSDT", "BNB", "USDT", new BigDecimal("0.001"), new BigDecimal("0.01"), 5,
                new BigDecimal("0.001"), 0, BigDecimal.ONE, 1, "BNBUSDT");

        ITickerRegistry registry = new AsterTickerRegistry(
                new StubRestApi(new InstrumentDescriptor[] { perpetualDescriptor }, new InstrumentDescriptor[] {
                        spotDescriptor
                }));

        assertNotNull(registry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, "BTCUSDT"));
        assertNotNull(registry.lookupByBrokerSymbol(InstrumentType.CRYPTO_SPOT, "BNBUSDT"));
        assertEquals("BNBUSDT", registry.lookupByCommonSymbol(InstrumentType.CRYPTO_SPOT, "BNB/USDT").getSymbol());
    }

    private static final class StubRestApi implements IAsterRestApi {
        private final InstrumentDescriptor[] perpetualDescriptors;
        private final InstrumentDescriptor[] spotDescriptors;

        private StubRestApi(InstrumentDescriptor[] perpetualDescriptors, InstrumentDescriptor[] spotDescriptors) {
            this.perpetualDescriptors = perpetualDescriptors;
            this.spotDescriptors = spotDescriptors;
        }

        @Override
        public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
            if (instrumentType == InstrumentType.CRYPTO_SPOT) {
                return spotDescriptors;
            }
            return perpetualDescriptors;
        }

        @Override
        public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
            return null;
        }

        @Override
        public boolean isPublicApiOnly() {
            return true;
        }

        @Override
        public Date getServerTime() {
            return new Date();
        }

        @Override
        public String startUserDataStream() {
            return "";
        }

        @Override
        public void keepAliveUserDataStream(String listenKey) {
        }

        @Override
        public void closeUserDataStream(String listenKey) {
        }

        @Override
        public JsonNode placeOrder(Map<String, String> params) {
            return null;
        }

        @Override
        public JsonNode cancelOrder(String symbol, String orderId, String origClientOrderId) {
            return null;
        }

        @Override
        public JsonNode cancelAllOpenOrders(String symbol) {
            return null;
        }

        @Override
        public JsonNode queryOrder(String symbol, String orderId, String origClientOrderId) {
            return null;
        }

        @Override
        public JsonNode getOpenOrders(String symbol) {
            return null;
        }

        @Override
        public JsonNode getPositionRisk(String symbol) {
            return null;
        }

        @Override
        public JsonNode getAccountInformation() {
            return null;
        }

        @Override
        public JsonNode getBookTicker(String symbol) {
            return null;
        }
    }
}
