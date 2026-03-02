package com.fueledbychai.binancefutures.common.api.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class BinanceFuturesJsonProcessorTest {

    @Test
    void ignoresSubscribeAcknowledgements() {
        TestProcessor processor = new TestProcessor();
        assertNull(processor.parse("{\"result\":null,\"id\":1}"));
    }

    @Test
    void unwrapsCombinedStreamPayloads() {
        TestProcessor processor = new TestProcessor();
        JsonNode payload = processor.parse("{\"stream\":\"btcusdt@aggTrade\",\"data\":{\"p\":\"101.5\"}}");

        assertNotNull(payload);
        assertEquals("101.5", payload.path("p").asText());
    }

    private static final class TestProcessor extends BinanceFuturesJsonProcessor {
        private TestProcessor() {
            super(() -> {
            });
        }

        private JsonNode parse(String message) {
            return parseMessage(message);
        }
    }
}
