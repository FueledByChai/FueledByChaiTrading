package com.fueledbychai.broker.qfex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Side;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.qfex.common.api.IQfexRestApi;
import com.fueledbychai.qfex.common.api.IQfexWebSocketApi;
import com.fueledbychai.qfex.common.api.QfexContract;
import com.fueledbychai.qfex.common.api.QfexHmacSigner;
import com.fueledbychai.qfex.common.api.ws.QfexMarketDataStream;
import com.fueledbychai.qfex.common.api.ws.QfexStream;
import com.fueledbychai.qfex.common.api.ws.QfexTradeStream;

class QfexBrokerTest {

    /** Trade socket double: records outbound messages and answers each one asynchronously. */
    static class FakeVenue extends QfexTradeStream {
        final List<JsonNode> sent = new ArrayList<>();
        volatile Function<JsonNode, String> responder = m -> null;

        FakeVenue() {
            super("wss://trade.unit.test", new QfexHmacSigner("qfex_pub_x", "qfex_secret_x"), null, false, 2000);
        }

        @Override
        public boolean send(JsonNode message) {
            sent.add(message);
            String reply = responder.apply(message);
            if (reply != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        messageReceived(reply);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        throw t;
                    }
                });
            }
            return true;
        }

        JsonNode last(String type) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (type.equals(sent.get(i).path("type").asText())) {
                    return sent.get(i);
                }
            }
            return null;
        }
    }

    static class StubWs implements IQfexWebSocketApi {
        final FakeVenue venue = new FakeVenue();
        @Override public void connect() { }
        @Override public void connectOrderEntryWebSocket() { venue.messageReceived("{\"authenticated\":true}"); }
        @Override public void disconnectAll() { }
        @Override public QfexMarketDataStream marketData() { return null; }
        @Override public QfexTradeStream trade() { return venue; }
    }

    static class StubRest implements IQfexRestApi {
        @Override public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType t) { return new InstrumentDescriptor[0]; }
        @Override public InstrumentDescriptor getInstrumentDescriptor(String s) { return null; }
        @Override public List<QfexContract> getContracts() { return List.of(); }
        @Override public QfexContract getContract(String s) { return null; }
        @Override public JsonNode getCandles(String s, String r, Instant f, Instant t) { return null; }
        @Override public JsonNode getOrderBook(String s) { return null; }
        @Override public JsonNode getPositions() { return null; }
        @Override public boolean isPublicApiOnly() { return true; }
    }

    private StubWs ws;
    private QfexBroker broker;
    private final Ticker ticker = new Ticker("AAPL-USD");

    @BeforeEach
    void setUp() {
        ws = new StubWs();
        broker = new QfexBroker(new StubRest(), ws, 2000);
        broker.connect();
    }

    @Test
    void postOnlyLimitIsSentAsAloAndAckSetsOrderId() {
        ws.venue.responder = m -> "add_order".equals(m.path("type").asText())
                ? "{\"order_response\":{\"order_id\":\"o-1\",\"symbol\":\"AAPL-USD\",\"status\":\"ACK\",\"client_order_id\":\""
                        + m.path("params").path("client_order_id").asText() + "\"}}"
                : null;
        OrderTicket order = limit(TradeDirection.BUY, "300.00", "1").addModifier(OrderTicket.Modifier.POST_ONLY);

        BrokerRequestResult r = broker.placeOrder(order);

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals("o-1", order.getOrderId());
        JsonNode p = ws.venue.last("add_order").path("params");
        assertEquals("ALO", p.path("order_type").asText());
        assertEquals("GTC", p.path("order_time_in_force").asText());
        assertEquals("BUY", p.path("side").asText());
        assertTrue(p.path("price").isNumber());
        assertFalse(p.path("reduce_only").asBoolean());
    }

    @Test
    void stopAckWithoutClientIdIsMatchedByShape() {
        // The venue's stop-order example echoes an empty client_order_id.
        ws.venue.responder = m -> "add_order".equals(m.path("type").asText())
                ? "{\"order_response\":{\"order_id\":\"s-1\",\"symbol\":\"AAPL-USD\",\"status\":\"ACK\",\"type\":\"STOP_LOSS\","
                        + "\"side\":\"SELL\",\"price\":290.0,\"quantity\":1.0,\"client_order_id\":\"\"}}"
                : null;
        OrderTicket stop = new OrderTicket().setTicker(ticker).setTradeDirection(TradeDirection.SELL)
                .setSize(BigDecimal.ONE).setType(OrderTicket.Type.STOP).setStopPrice(new BigDecimal("290.00"))
                .addModifier(OrderTicket.Modifier.REDUCE_ONLY);

        BrokerRequestResult r = broker.placeOrder(stop);

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals("s-1", stop.getOrderId());
        JsonNode p = ws.venue.last("add_order").path("params");
        assertEquals("STOP_LOSS", p.path("order_type").asText());
        assertTrue(p.path("reduce_only").asBoolean());
    }

    @Test
    void rejectStatusAndErrorsFailThePlace() {
        ws.venue.responder = m -> "{\"order_response\":{\"order_id\":\"o-2\",\"status\":\"FAILED_MARGIN_CHECK\",\"client_order_id\":\""
                + m.path("params").path("client_order_id").asText() + "\"}}";
        BrokerRequestResult r = broker.placeOrder(limit(TradeDirection.BUY, "300", "1"));
        assertFalse(r.isSuccess());
        assertEquals("FAILED_MARGIN_CHECK", r.getMessage());

        ws.venue.responder = m -> "{\"err\":{\"error_code\":\"InvalidOrder\",\"message\":\"tick\",\"incoming_message\":"
                + m.toString() + "}}";
        BrokerRequestResult r2 = broker.placeOrder(limit(TradeDirection.BUY, "300.001", "1"));
        assertFalse(r2.isSuccess());
        assertTrue(r2.getMessage().startsWith("InvalidOrder"));
    }

    @Test
    void modifyAdoptsTheNewOrderIdAndRepeatsReduceOnly() {
        ws.venue.responder = m -> switch (m.path("type").asText()) {
            case "add_order" -> "{\"order_response\":{\"order_id\":\"o-1\",\"status\":\"ACK\",\"client_order_id\":\""
                    + m.path("params").path("client_order_id").asText() + "\"}}";
            case "modify_order" -> "{\"order_response\":{\"order_id\":\"o-9\",\"symbol\":\"AAPL-USD\",\"status\":\"MODIFIED\","
                    + "\"side\":\"BUY\",\"price\":299.5,\"quantity\":1.0}}";
            default -> null;
        };
        OrderTicket order = limit(TradeDirection.BUY, "300", "1").addModifier(OrderTicket.Modifier.POST_ONLY);
        broker.placeOrder(order);
        order.setLimitPrice(new BigDecimal("299.5"));

        BrokerRequestResult r = broker.modifyOrder(order);

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals("o-9", order.getOrderId());
        JsonNode p = ws.venue.last("modify_order").path("params");
        assertEquals("o-1", p.path("order_id").asText());
        assertTrue(p.has("reduce_only"));
        assertEquals("ALO", p.path("order_type").asText());
    }

    @Test
    void cancellingAStopUsesStopOrderId() {
        ws.venue.responder = m -> "cancel_stop_order".equals(m.path("type").asText())
                ? "{\"order_response\":{\"order_id\":\"s-7\",\"status\":\"CANCELLED\"}}"
                : null;
        OrderTicket stop = new OrderTicket().setTicker(ticker).setTradeDirection(TradeDirection.SELL)
                .setSize(BigDecimal.ONE).setType(OrderTicket.Type.STOP).setStopPrice(new BigDecimal("290"));
        stop.setOrderId("s-7");

        assertTrue(broker.cancelOrder(stop).isSuccess());
        assertEquals("s-7", ws.venue.last("cancel_stop_order").path("params").path("stop_order_id").asText());
    }

    @Test
    void fillsOrderEventsAndPositionsAreTranslated() {
        List<Fill> fills = new ArrayList<>();
        List<OrderEvent> events = new ArrayList<>();
        broker.addFillEventListener(fills::add);
        broker.addOrderEventListener(events::add);

        broker.onFill(json("""
                {"trade_id":"t1","symbol":"AAPL-USD","price":"300.5","quantity":"2","side":"SELL","aggressor_side":"BUY",
                 "order_id":"o-1","client_order_id":"c-1","fee":"0.03","timestamp":1790196660.25,"realised_pnl":"1.5"}"""));
        broker.onOrderResponse(json("""
                {"order_id":"o-1","symbol":"AAPL-USD","status":"CANCELLED","quantity":2.0,"quantity_remaining":0.0,
                 "client_order_id":"c-1"}"""));
        broker.onPosition(json("{\"symbol\":\"AAPL-USD\",\"position\":\"-2\",\"average_price\":\"300.5\"}"));

        Fill f = fills.get(0);
        assertEquals(TradeDirection.SELL, f.getSide());
        assertFalse(f.isTaker());
        assertEquals(0, new BigDecimal("0.03").compareTo(f.getCommission()));
        assertEquals(1790196660250L, f.getTime().toInstant().toEpochMilli());
        assertEquals(OrderStatus.Status.CANCELED, events.get(0).getOrderStatus().getStatus());
        assertEquals("c-1", events.get(0).getOrderStatus().getClientOrderId());
        Position p = broker.getAllPositions().get(0);
        assertEquals(Side.SHORT, p.getSide());
        assertEquals(0, new BigDecimal("2").compareTo(p.getSize()));
    }

    @Test
    void partialFillStatusKeepsOrderOpen() {
        assertEquals(OrderStatus.Status.PARTIAL_FILL, QfexTranslator.toStatus("FILLED", new BigDecimal("0.5")));
        assertEquals(OrderStatus.Status.FILLED, QfexTranslator.toStatus("FILLED", BigDecimal.ZERO));
        assertEquals(OrderStatus.Status.REJECTED, QfexTranslator.toStatus("REJECTED_MARKET_CLOSED", null));
    }

    private OrderTicket limit(TradeDirection dir, String price, String size) {
        return new OrderTicket().setTicker(ticker).setTradeDirection(dir).setType(OrderTicket.Type.LIMIT)
                .setDuration(OrderTicket.Duration.GOOD_UNTIL_CANCELED)
                .setLimitPrice(new BigDecimal(price)).setSize(new BigDecimal(size));
    }

    private static JsonNode json(String s) {
        try {
            return QfexStream.MAPPER.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
