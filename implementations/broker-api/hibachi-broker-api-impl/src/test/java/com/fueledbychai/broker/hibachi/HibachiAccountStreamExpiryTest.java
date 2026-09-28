package com.fueledbychai.broker.hibachi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.hibachi.common.api.HibachiConfiguration;

/**
 * Regression cover for Longshot on Hibachi, 2026-09-27: the account stream's
 * listenKey expired after ~6 hours ({@code stream_expired}), every ping from
 * then on got 404 "Subscription ... not found", and the client never
 * resubscribed — so 8 hours of fills and order updates were never delivered.
 */
class HibachiAccountStreamExpiryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void streamExpiredEventMeansTheStreamIsGone() throws Exception {
        assertTrue(HibachiAccountStreamClient.isStreamGone(MAPPER.readTree(
                "{\"account\":\"281474976740705\",\"event\":\"stream_expired\","
                        + "\"params\":{\"listenKey\":\"k\",\"timestampMs\":1790573523198}}")));
    }

    @Test
    void pingAnsweredWithSubscriptionNotFoundMeansTheStreamIsGone() throws Exception {
        assertTrue(HibachiAccountStreamClient.isStreamGone(MAPPER.readTree(
                "{\"error\":{\"message\":\"Subscription 281474976740705-abc not found\",\"status\":\"failed\"},"
                        + "\"id\":832467,\"status\":404}")));
    }

    @Test
    void ordinaryFramesAreNot() throws Exception {
        assertFalse(HibachiAccountStreamClient.isStreamGone(MAPPER.readTree(
                "{\"event\":\"order_update\",\"data\":{\"status\":\"Cancelled\"}}")));
        assertFalse(HibachiAccountStreamClient.isStreamGone(MAPPER.readTree(
                "{\"id\":1,\"status\":200,\"result\":{\"listenKey\":\"k\"}}")));
        assertFalse(HibachiAccountStreamClient.isStreamGone(MAPPER.readTree(
                "{\"event\":\"order_request_rejected\",\"data\":{\"error\":\"Order not found\"}}")));
    }

    @Test
    void connectedIsReportedWhenTheListenKeyArrivesNotWhenTheSocketOpens() throws Exception {
        List<Boolean> states = new ArrayList<>();
        HibachiAccountStreamClient c = client(states);
        c.generation = 1;

        c.onMessage(MAPPER.readTree("{\"id\":1,\"status\":200,\"result\":{\"listenKey\":\"k1\"}}"));

        assertTrue(c.streamReady);
        assertEquals(List.of(true), states);
    }

    @Test
    void aCloseFromAnOlderSocketDoesNotTearDownTheReplacement() throws Exception {
        List<Boolean> states = new ArrayList<>();
        HibachiAccountStreamClient c = client(states);
        c.shutdown = true; // no reconnect scheduling in this test
        c.generation = 2;
        c.streamReady = true;
        c.listenKey = "k2";

        c.onClosed(1); // the expired socket's close arrives late
        assertTrue(c.streamReady);
        assertEquals("k2", c.listenKey);
        assertTrue(states.isEmpty());

        c.onClosed(2);
        assertFalse(c.streamReady);
        assertEquals(List.of(false), states);
    }

    private static HibachiAccountStreamClient client(List<Boolean> states) {
        HibachiAccountStreamClient c = new HibachiAccountStreamClient(HibachiConfiguration.getInstance(), 1L, "key");
        c.setConnectionStateListener(states::add);
        return c;
    }
}
