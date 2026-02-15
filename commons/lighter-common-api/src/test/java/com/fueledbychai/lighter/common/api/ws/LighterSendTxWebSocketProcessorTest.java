package com.fueledbychai.lighter.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class LighterSendTxWebSocketProcessorTest {

    @Test
    void parseSendTxAckMessage() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"code\":200,\"msg\":\"ok\",\"id\":\"123\"}";

        LighterSendTxResponse response = processor.parse(message);
        assertNotNull(response);
        assertEquals("123", response.getRequestId());
        assertEquals(200, response.getCode());
        assertEquals("ok", response.getMessage());
        assertTrue(response.isSuccess());
    }

    @Test
    void parseSendTxAckInResultEnvelope() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"id\":42,\"result\":{\"code\":200,\"msg\":\"ok\"}}";

        LighterSendTxResponse response = processor.parse(message);
        assertNotNull(response);
        assertEquals("42", response.getRequestId());
        assertEquals(200, response.getCode());
        assertEquals("ok", response.getMessage());
    }

    @Test
    void parseSendTxAckWithoutId() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"code\":200,\"msg\":\"ok\"}";

        LighterSendTxResponse response = processor.parse(message);
        assertNotNull(response);
        assertNull(response.getRequestId());
        assertEquals(200, response.getCode());
        assertEquals("ok", response.getMessage());
    }

    @Test
    void ignoreAccountTxUpdates() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{"
                + "\"type\":\"update/account_tx\","
                + "\"channel\":\"account_tx:1\","
                + "\"tx\":{"
                + "\"hash\":\"0xabc\""
                + "}"
                + "}";

        assertNull(processor.parse(message));
    }

    @Test
    void parseErrorMessage() {
        TestableProcessor processor = new TestableProcessor();
        String message = "{\"id\":\"abc\",\"error\":\"invalid tx\"}";

        LighterSendTxResponse response = processor.parse(message);
        assertNotNull(response);
        assertEquals("abc", response.getRequestId());
        assertNull(response.getCode());
        assertEquals("invalid tx", response.getMessage());
        assertFalse(response.isSuccess());
    }

    private static class TestableProcessor extends LighterSendTxWebSocketProcessor {
        TestableProcessor() {
            super(() -> {
            });
        }

        LighterSendTxResponse parse(String message) {
            return super.parseMessage(message);
        }
    }
}
