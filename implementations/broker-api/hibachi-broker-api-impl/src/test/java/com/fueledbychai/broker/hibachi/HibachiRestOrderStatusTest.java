package com.fueledbychai.broker.hibachi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueledbychai.broker.order.OrderStatus;

/**
 * Regression cover for the orphan leak found on HYPE-Hibachi 2026-07-28.
 *
 * <p>{@code /trade/orders} returns only currently-ACTIVE orders. parseRestOrder
 * used to leave {@code currentStatus} untouched, so when it reused an
 * OrderTicket from the local registry the ticket kept whatever the last
 * WebSocket event had written — usually CANCELED. chaiwala's reconcile reads
 * that status to decide whether an untracked open order is a real orphan, saw
 * the stale CANCELED, and skipped the order as a "stale terminal-status echo".
 * The order then leaked: never tracked locally, never cancelled. Five live
 * orders were skipped 144,294 times before this was caught.
 *
 * <p>The invariant protected here: a REST-sourced status comes from the venue
 * payload, and an absent or unrecognised field is never terminal.
 */
class HibachiRestOrderStatusTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Mirrors chaiwala's TERMINAL_STATUSES — statuses that suppress orphan cancellation. */
    private static final Set<OrderStatus.Status> TERMINAL = EnumSet.of(
            OrderStatus.Status.CANCELED,
            OrderStatus.Status.FILLED,
            OrderStatus.Status.REJECTED);

    private static JsonNode json(String raw) throws Exception {
        return MAPPER.readTree(raw);
    }

    @Test
    void venueStatusIsHonoured() throws Exception {
        assertEquals(OrderStatus.Status.NEW,
                HibachiBroker.resolveRestOrderStatus(json("{\"status\":\"PLACED\"}")));
        assertEquals(OrderStatus.Status.PARTIAL_FILL,
                HibachiBroker.resolveRestOrderStatus(json("{\"status\":\"PARTIALLY_FILLED\"}")));
        // If the venue genuinely reports terminal, pass it through — that is what
        // keeps the ETH-Hibachi 2026-06-30 jam fixed (a stuck terminal order
        // counted as an orphan every 3s permanently pauses one quote side).
        assertEquals(OrderStatus.Status.CANCELED,
                HibachiBroker.resolveRestOrderStatus(json("{\"status\":\"CANCELLED\"}")));
    }

    @Test
    void alternateFieldNamesAreAccepted() throws Exception {
        assertEquals(OrderStatus.Status.NEW,
                HibachiBroker.resolveRestOrderStatus(json("{\"orderStatus\":\"OPEN\"}")));
        assertEquals(OrderStatus.Status.NEW,
                HibachiBroker.resolveRestOrderStatus(json("{\"state\":\"ACCEPTED\"}")));
    }

    @Test
    void absentStatusIsNotTerminal() throws Exception {
        // Shape drift: no status field at all. Must stay cancellable.
        OrderStatus.Status s = HibachiBroker.resolveRestOrderStatus(
                json("{\"orderId\":\"2\",\"side\":\"ASK\",\"price\":\"101\"}"));
        assertEquals(OrderStatus.Status.UNKNOWN, s);
        assertFalse(TERMINAL.contains(s),
                "absent status must not suppress orphan cancellation");
    }

    @Test
    void unrecognisedStatusIsNotTerminal() throws Exception {
        OrderStatus.Status s = HibachiBroker.resolveRestOrderStatus(
                json("{\"status\":\"SOME_NEW_HIBACHI_STATE\"}"));
        assertEquals(OrderStatus.Status.UNKNOWN, s);
        assertFalse(TERMINAL.contains(s),
                "unrecognised venue status must not suppress orphan cancellation");
    }

    @Test
    void nullNodeIsNotTerminal() {
        OrderStatus.Status s = HibachiBroker.resolveRestOrderStatus(null);
        assertEquals(OrderStatus.Status.UNKNOWN, s);
        assertFalse(TERMINAL.contains(s));
    }

    @Test
    void statusMapperCoversHibachiVocabulary() {
        assertEquals(OrderStatus.Status.NEW, HibachiBroker.parseOrderStatus("OPEN"));
        assertEquals(OrderStatus.Status.PARTIAL_FILL,
                HibachiBroker.parseOrderStatus("PARTIALLY_FILLED"));
        assertEquals(OrderStatus.Status.FILLED, HibachiBroker.parseOrderStatus("FILLED"));
        assertEquals(OrderStatus.Status.CANCELED, HibachiBroker.parseOrderStatus("CANCELLED"));
        assertEquals(OrderStatus.Status.REJECTED, HibachiBroker.parseOrderStatus("REJECTED"));
        assertEquals(OrderStatus.Status.UNKNOWN, HibachiBroker.parseOrderStatus("WAT"));
    }
}
