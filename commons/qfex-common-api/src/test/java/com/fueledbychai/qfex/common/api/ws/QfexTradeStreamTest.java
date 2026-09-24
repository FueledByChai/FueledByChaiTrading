package com.fueledbychai.qfex.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueledbychai.qfex.common.api.QfexHmacSigner;

class QfexTradeStreamTest {

    /** Captures outbound messages instead of writing to a socket. */
    static class CapturingStream extends QfexTradeStream {
        final List<JsonNode> sent = new ArrayList<>();

        CapturingStream() {
            super("wss://trade.unit.test", new QfexHmacSigner("qfex_pub_x", "qfex_secret_x"), null, true, 2000);
        }

        @Override
        public boolean send(JsonNode message) {
            sent.add(message);
            return true;
        }

        void receive(String json) {
            messageReceived(json);
        }
    }

    private CapturingStream stream;
    private final List<JsonNode> orderUpdates = new ArrayList<>();

    @BeforeEach
    void setUp() {
        stream = new CapturingStream();
        stream.addListener(new QfexTradeListener() {
            @Override
            public void onOrderResponse(JsonNode order) {
                orderUpdates.add(order);
            }
        });
    }

    @Test
    void authMessageCarriesHmacCredentials() {
        stream.onOpened();
        JsonNode auth = stream.sent.get(0);
        assertEquals("auth", auth.path("type").asText());
        assertEquals("qfex_pub_x", auth.path("params").path("hmac").path("public_key").asText());
        assertEquals(64, auth.path("params").path("hmac").path("signature").asText().length());
    }

    @Test
    void acceptsBothAuthReplyShapesAndSubscribes() {
        stream.receive("{\"authenticated\":true}");
        assertTrue(stream.isAuthenticated());
        List<String> channels = stream.sent.stream()
                .filter(m -> "subscribe".equals(m.path("type").asText()))
                .map(m -> m.path("params").path("channels").get(0).asText()).toList();
        assertEquals(List.of("order_responses", "fills", "positions", "balances"), channels);
        assertTrue(stream.sent.stream().anyMatch(m -> "cancel_on_disconnect".equals(m.path("type").asText())));

        CapturingStream other = new CapturingStream();
        other.receive("{\"type\":\"auth\",\"result\":\"success\"}");
        assertTrue(other.isAuthenticated());
    }

    @Test
    void requestFailsFastWhenNotAuthenticated() {
        CompletableFuture<JsonNode> f = stream.request(order("c1"), QfexTradeStream.ORDER_RESPONSE, n -> true);
        assertTrue(f.isCompletedExceptionally());
    }

    @Test
    void orderResponseResolvesMatchingRequestOnly() throws Exception {
        stream.receive("{\"authenticated\":true}");
        CompletableFuture<JsonNode> a = stream.request(order("c1"), QfexTradeStream.ORDER_RESPONSE,
                n -> "c1".equals(n.path("client_order_id").asText()));
        CompletableFuture<JsonNode> b = stream.request(order("c2"), QfexTradeStream.ORDER_RESPONSE,
                n -> "c2".equals(n.path("client_order_id").asText()));

        stream.receive("{\"order_response\":{\"order_id\":\"o2\",\"client_order_id\":\"c2\",\"status\":\"ACK\"}}");

        assertTrue(b.isDone());
        assertEquals("o2", b.get().path("order_id").asText());
        assertFalse(a.isDone());
        assertEquals(1, orderUpdates.size());
    }

    @Test
    void errorIsMatchedThroughEchoedIncomingMessage() throws ExecutionException, InterruptedException {
        stream.receive("{\"authenticated\":true}");
        CompletableFuture<JsonNode> a = stream.request(order("c1"), QfexTradeStream.ORDER_RESPONSE, n -> false);
        CompletableFuture<JsonNode> b = stream.request(order("c2"), QfexTradeStream.ORDER_RESPONSE, n -> false);

        stream.receive("""
                {"err":{"error_code":"InvalidOrder","message":"bad",
                  "incoming_message":{"type":"add_order","params":{"client_order_id":"c2"}}}}""");

        assertTrue(b.isDone());
        assertEquals("InvalidOrder", b.get().path("err").path("error_code").asText());
        assertFalse(a.isDone());
    }

    @Test
    void closeFailsPendingRequestsAndDropsAuth() {
        stream.receive("{\"authenticated\":true}");
        CompletableFuture<JsonNode> a = stream.request(order("c1"), QfexTradeStream.ORDER_RESPONSE, n -> false);
        stream.onClosed();
        assertTrue(a.isCompletedExceptionally());
        assertFalse(stream.isAuthenticated());
    }

    private static ObjectNode order(String clientId) {
        ObjectNode msg = QfexStream.MAPPER.createObjectNode();
        msg.put("type", "add_order");
        msg.putObject("params").put("client_order_id", clientId);
        return msg;
    }
}
