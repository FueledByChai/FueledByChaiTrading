package com.fueledbychai.broker.hibachi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.hibachi.common.api.HibachiContract;
import com.fueledbychai.hibachi.common.api.signer.IHibachiSigner;
import com.fueledbychai.hibachi.common.api.signer.SignatureScheme;

class HibachiTranslatorTriggerOrderTest {

    private static final IHibachiSigner SIGNER = new IHibachiSigner() {
        @Override
        public String sign(byte[] packedPayload) {
            return "sig";
        }

        @Override
        public SignatureScheme scheme() {
            return SignatureScheme.ECDSA;
        }
    };

    private static final HibachiContract CONTRACT = HibachiContract.builder()
            .id(2)
            .symbol("EUR/USDT-P")
            .underlyingDecimals(2)
            .settlementDecimals(6)
            .tickSize(new BigDecimal("0.00001"))
            .stepSize(new BigDecimal("0.01"))
            .build();

    private final HibachiTranslator translator = new HibachiTranslator();

    private static OrderTicket stop(TradeDirection direction, OrderTicket.Type type) {
        OrderTicket order = new OrderTicket();
        order.setTradeDirection(direction);
        order.setType(type);
        order.setSize(new BigDecimal("100"));
        order.setStopPrice(new BigDecimal("1.08500"));
        order.addModifier(OrderTicket.Modifier.REDUCE_ONLY);
        return order;
    }

    private Map<String, Object> place(OrderTicket order) {
        return translator.translatePlace(order, CONTRACT, 7L, 1L, 0L, new BigDecimal("0.05"), SIGNER).params;
    }

    @Test
    void sellStopIsMarketTriggerOrderFiringLow() {
        Map<String, Object> params = place(stop(TradeDirection.SELL, OrderTicket.Type.STOP));

        assertEquals("MARKET", params.get("orderType"));
        assertEquals("ASK", params.get("side"));
        assertEquals("1.08500", params.get("triggerPrice"));
        assertEquals("LOW", params.get("triggerDirection"));
        assertEquals("REDUCE_ONLY", params.get("orderFlags"));
        assertFalse(params.containsKey("price"));
    }

    @Test
    void buyStopLimitCarriesLimitPriceAndFiresHigh() {
        OrderTicket order = stop(TradeDirection.BUY, OrderTicket.Type.STOP_LIMIT);
        order.setLimitPrice(new BigDecimal("1.08600"));

        Map<String, Object> params = place(order);

        assertEquals("LIMIT", params.get("orderType"));
        assertEquals("1.08600", params.get("price"));
        assertEquals("HIGH", params.get("triggerDirection"));
    }

    @Test
    void plainLimitHasNoTriggerFields() {
        OrderTicket order = new OrderTicket();
        order.setTradeDirection(TradeDirection.BUY);
        order.setType(OrderTicket.Type.LIMIT);
        order.setSize(new BigDecimal("100"));
        order.setLimitPrice(new BigDecimal("1.07"));
        order.setStopPrice(new BigDecimal("1.05"));

        Map<String, Object> params = place(order);

        assertFalse(params.containsKey("triggerPrice"));
        assertFalse(params.containsKey("triggerDirection"));
    }

    @Test
    void stopWithoutStopPriceIsRejected() {
        OrderTicket order = stop(TradeDirection.SELL, OrderTicket.Type.STOP);
        order.setStopPrice(null);

        assertThrows(IllegalArgumentException.class, () -> place(order));
    }

    @Test
    void modifyOfStopSendsUpdatedTriggerPrice() {
        OrderTicket order = stop(TradeDirection.SELL, OrderTicket.Type.STOP);
        order.setOrderId("555");
        order.setStopPrice(new BigDecimal("1.08000"));

        Map<String, Object> params = translator.translateModify(
                order, CONTRACT, 7L, 2L, 0L, new BigDecimal("0.05"), SIGNER).params;

        assertEquals("1.08000", params.get("updatedTriggerPrice"));
        assertEquals("1.08000", params.get("triggerPrice"));
        assertFalse(params.containsKey("updatedPrice"));
    }
}
