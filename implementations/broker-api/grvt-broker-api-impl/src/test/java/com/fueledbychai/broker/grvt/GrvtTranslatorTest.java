package com.fueledbychai.broker.grvt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.OrderTicket.Modifier;
import com.fueledbychai.broker.order.OrderTicket.Type;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.grvt.common.api.model.GrvtOrder;
import com.fueledbychai.grvt.common.api.model.GrvtTimeInForce;

class GrvtTranslatorTest {

    @Test
    void mapsRpiModifierToGrvtRpiTimeInForceAndKeepsPostOnly() {
        OrderTicket ticket = limitOrder();
        ticket.addModifier(Modifier.RPI);
        ticket.addModifier(Modifier.POST_ONLY);

        GrvtOrder order = new GrvtTranslator().toGrvtOrder(ticket, "sub-account-1");

        assertEquals(GrvtTimeInForce.RETAIL_PRICE_IMPROVEMENT, order.getTimeInForce());
        assertTrue(order.isPostOnly());
        assertEquals(5, order.getTimeInForce().getSignValue());
    }

    @Test
    void postOnlyWithoutRpiRemainsGoodTillTime() {
        OrderTicket ticket = limitOrder();
        ticket.addModifier(Modifier.POST_ONLY);

        GrvtOrder order = new GrvtTranslator().toGrvtOrder(ticket, "sub-account-1");

        assertEquals(GrvtTimeInForce.GOOD_TILL_TIME, order.getTimeInForce());
        assertTrue(order.isPostOnly());
    }

    private static OrderTicket limitOrder() {
        Ticker ticker = new Ticker("BTC_USDT_Perp")
                .setExchange(Exchange.GRVT)
                .setInstrumentType(InstrumentType.PERPETUAL_FUTURES);
        OrderTicket ticket = new OrderTicket("", ticker, new BigDecimal("0.01"), TradeDirection.BUY);
        ticket.setClientOrderId("123");
        ticket.setType(Type.LIMIT);
        ticket.setLimitPrice(new BigDecimal("100000"));
        return ticket;
    }
}
