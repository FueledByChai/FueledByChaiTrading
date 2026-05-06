package com.fueledbychai.diagnostics;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static, opt-in event bus for low-level REST and WebSocket traffic.
 * Listener registration drives whether the producing sites do any work, so when
 * no listener is attached the cost is a single volatile read per event.
 */
public final class WireTap {

    public enum Direction { OUT, IN }

    private static final List<WireTapListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean enabled = false;

    private WireTap() {
    }

    public static void addListener(WireTapListener listener) {
        if (listener == null) {
            return;
        }
        LISTENERS.add(listener);
        enabled = true;
    }

    public static void removeListener(WireTapListener listener) {
        if (listener == null) {
            return;
        }
        LISTENERS.remove(listener);
        enabled = !LISTENERS.isEmpty();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void publishRest(RestEvent event) {
        if (!enabled || event == null) {
            return;
        }
        for (WireTapListener l : LISTENERS) {
            try {
                l.onRest(event);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void publishWs(WsEvent event) {
        if (!enabled || event == null) {
            return;
        }
        for (WireTapListener l : LISTENERS) {
            try {
                l.onWs(event);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface WireTapListener {
        default void onRest(RestEvent event) {
        }

        default void onWs(WsEvent event) {
        }
    }

    public static final class RestEvent {
        public final long timestampMillis;
        public final Direction direction;
        public final String exchange;
        public final String method;
        public final String url;
        public final int statusCode;
        public final String headers;
        public final String body;
        public final long durationMillis;

        public RestEvent(long timestampMillis,
                         Direction direction,
                         String exchange,
                         String method,
                         String url,
                         int statusCode,
                         String headers,
                         String body,
                         long durationMillis) {
            this.timestampMillis = timestampMillis;
            this.direction = direction;
            this.exchange = exchange;
            this.method = method;
            this.url = url;
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
            this.durationMillis = durationMillis;
        }
    }

    public static final class WsEvent {
        public final long timestampMillis;
        public final Direction direction;
        public final String exchange;
        public final String channel;
        public final String url;
        public final String payload;

        public WsEvent(long timestampMillis,
                       Direction direction,
                       String exchange,
                       String channel,
                       String url,
                       String payload) {
            this.timestampMillis = timestampMillis;
            this.direction = direction;
            this.exchange = exchange;
            this.channel = channel;
            this.url = url;
            this.payload = payload;
        }
    }
}
