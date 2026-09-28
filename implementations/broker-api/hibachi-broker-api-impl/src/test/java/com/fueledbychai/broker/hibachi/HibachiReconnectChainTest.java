package com.fueledbychai.broker.hibachi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fueledbychai.hibachi.common.api.HibachiConfiguration;

/**
 * Regression cover for Longshot on Hibachi, 2026-09-28: a short internet drop
 * made the first reconnect attempts time out. Each failed attempt called
 * scheduleReconnect() from inside itself, saw its own (still running) task as
 * pending, and scheduled nothing - so the trade and account sockets never
 * came back while resting orders kept filling unseen.
 */
class HibachiReconnectChainTest {

    @Test
    void tradeSocketKeepsRetryingThroughConsecutiveFailures() throws Exception {
        AtomicInteger failuresLeft = new AtomicInteger(3);
        AtomicBoolean up = new AtomicBoolean(); // written by the reconnect thread
        HibachiTradeWebSocketClient c = new HibachiTradeWebSocketClient(HibachiConfiguration.getInstance(), 1L, "k") {
            @Override
            protected synchronized void doConnect() throws Exception {
                if (failuresLeft.getAndDecrement() > 0) {
                    throw new IllegalStateException("Hibachi trade WS handshake timed out");
                }
                up.set(true);
            }

            @Override
            public boolean isConnected() {
                return up.get();
            }
        };
        c.reconnectBackoffMs = 50; // independent of any configured backoff: ~50, 100, 200, 400 ms
        c.scheduleReconnect();
        assertTrue(waitFor(up::get, 10_000), "never reconnected after 3 failed attempts");
        c.disconnect();
    }

    @Test
    void accountStreamKeepsRetryingThroughConsecutiveFailures() throws Exception {
        AtomicInteger failuresLeft = new AtomicInteger(3);
        AtomicBoolean up = new AtomicBoolean(); // written by the reconnect thread
        HibachiAccountStreamClient c = new HibachiAccountStreamClient(HibachiConfiguration.getInstance(), 1L, "k") {
            @Override
            protected synchronized void doConnect() throws Exception {
                if (failuresLeft.getAndDecrement() > 0) {
                    throw new IllegalStateException("Hibachi account WS handshake timed out");
                }
                up.set(true);
            }

            @Override
            public boolean isConnected() {
                return up.get();
            }
        };
        c.reconnectBackoffMs = 50; // independent of any configured backoff: ~50, 100, 200, 400 ms
        c.scheduleReconnect();
        assertTrue(waitFor(up::get, 10_000), "never reconnected after 3 failed attempts");
        c.disconnect();
    }

    private static boolean waitFor(java.util.function.BooleanSupplier cond, long ms) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return cond.getAsBoolean();
    }
}
