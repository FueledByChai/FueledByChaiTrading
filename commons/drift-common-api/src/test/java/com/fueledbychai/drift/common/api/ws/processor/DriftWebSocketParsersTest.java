package com.fueledbychai.drift.common.api.ws.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.fueledbychai.drift.common.api.model.DriftGatewayWebSocketEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class DriftWebSocketParsersTest {

    @Test
    void parseGatewayEventExtractsNestedEventTypePayload() {
        JsonObject message = JsonParser.parseString("""
                {
                  "channel": "orders",
                  "subAccountId": 0,
                  "data": {
                    "orderCreate": {
                      "order": {
                        "orderId": 157,
                        "marketIndex": 0,
                        "marketType": "perp",
                        "orderType": "limit",
                        "amount": "0.1",
                        "filled": "0",
                        "price": "80.5",
                        "direction": "buy",
                        "postOnly": true,
                        "reduceOnly": false,
                        "userOrderId": 12
                      },
                      "ts": 1704777347
                    }
                  }
                }
                """).getAsJsonObject();

        DriftGatewayWebSocketEvent event = DriftWebSocketParsers.parseGatewayEvent(message);

        assertNotNull(event);
        assertEquals("orders", event.getChannel());
        assertEquals("orderCreate", event.getEventType());
        assertEquals(0, event.getSubAccountId());
        assertEquals(1704777347L, event.getPayload().get("ts").getAsLong());
        assertEquals(157L, event.getPayload().getAsJsonObject("order").get("orderId").getAsLong());
    }
}
