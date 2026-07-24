package com.fueledbychai.marketdata.extended;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.marketdata.OrderBook;

/**
 * Order book for an Extended (extended.exchange) market. Mirrors the Paradex
 * order book: snapshots replace the full book, deltas apply incremental
 * bid/ask changes (a zero quantity removes a level), and a periodic notifier
 * coalesces updates into throttled listener notifications.
 *
 * <p>
 * Extended order-book frames carry {@code bid}/{@code ask} arrays of
 * {@code {"price":"..","qty":".."}} entries. A {@code SNAPSHOT} frame contains
 * the full book; a {@code DELTA} frame contains only changed levels (qty 0 =
 * remove). Parsing is defensive: missing/null fields are skipped.
 * </p>
 */
public class ExtendedOrderBook extends OrderBook implements IExtendedOrderBook {

    private final ScheduledExecutorService notificationScheduler;
    private final AtomicBoolean hasUpdates = new AtomicBoolean(false);
    private final AtomicReference<ZonedDateTime> lastUpdateTimestamp = new AtomicReference<>();
    private static final long NOTIFICATION_INTERVAL_MS = 100; // 100ms between notifications

    public ExtendedOrderBook(Ticker ticker) {
        super(ticker);
        this.notificationScheduler = createNotificationScheduler();
        startPeriodicNotifications();
    }

    public ExtendedOrderBook(Ticker ticker, BigDecimal tickSize) {
        super(ticker, tickSize);
        this.notificationScheduler = createNotificationScheduler();
        startPeriodicNotifications();
    }

    private ScheduledExecutorService createNotificationScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ExtendedOrderBook-notification-" + ticker.getSymbol());
                t.setDaemon(true);
                return t;
            }
        });
    }

    private void startPeriodicNotifications() {
        notificationScheduler.scheduleAtFixedRate(this::sendPeriodicNotifications, NOTIFICATION_INTERVAL_MS,
                NOTIFICATION_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void sendPeriodicNotifications() {
        try {
            if (hasUpdates.compareAndSet(true, false) && initialized) {
                ZonedDateTime timestamp = lastUpdateTimestamp.get();
                if (timestamp != null) {
                    double obi = calculateWeightedOrderBookImbalance(obiLambda);
                    super.notifyOrderBookUpdateListenersImbalance(BigDecimal.valueOf(obi), timestamp);
                    super.notifyOrderBookUpdateListenersNewOrderBookSnapshot(timestamp);
                }
            }
        } catch (Exception e) {
            logger.error("Error during periodic notification: {}", e.getMessage(), e);
        }
    }

    private void markUpdated(ZonedDateTime timestamp) {
        hasUpdates.set(true);
        lastUpdateTimestamp.set(timestamp);
    }

    @Override
    public void shutdown() {
        if (notificationScheduler != null && !notificationScheduler.isShutdown()) {
            notificationScheduler.shutdown();
            try {
                if (!notificationScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    notificationScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                notificationScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        super.shutdown();
    }

    @Override
    public void handleSnapshot(JsonObject data, ZonedDateTime timestamp) {
        // Full book replace
        buySide.clear();
        sellSide.clear();
        applyLevels(data, timestamp, true);
        initialized = true;
        markUpdated(timestamp);
    }

    @Override
    public void applyDelta(JsonObject data, ZonedDateTime timestamp) {
        applyLevels(data, timestamp, false);
        markUpdated(timestamp);
    }

    /**
     * Applies the {@code bid}/{@code ask} level arrays from an Extended order-book
     * frame. A zero (or blank/missing) quantity removes the level; otherwise the
     * level is inserted/updated. On a snapshot all present levels are inserts.
     */
    private void applyLevels(JsonObject data, ZonedDateTime timestamp, boolean snapshot) {
        if (data == null) {
            return;
        }
        applySide(data, "bid", true, timestamp);
        applySide(data, "ask", false, timestamp);
    }

    private void applySide(JsonObject data, String key, boolean buy, ZonedDateTime timestamp) {
        if (!data.has(key) || data.get(key).isJsonNull() || !data.get(key).isJsonArray()) {
            return;
        }
        JsonArray levels = data.getAsJsonArray(key);
        for (JsonElement el : levels) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject level = el.getAsJsonObject();
            BigDecimal price = getBigDecimal(level, "price");
            Double qty = getDouble(level, "qty");
            if (price == null) {
                continue;
            }
            if (qty == null || qty == 0.0) {
                if (buy) {
                    buySide.remove(price, timestamp);
                } else {
                    sellSide.remove(price, timestamp);
                }
            } else {
                if (buy) {
                    buySide.update(price, qty, timestamp);
                } else {
                    sellSide.update(price, qty, timestamp);
                }
            }
        }
    }

    private static BigDecimal getBigDecimal(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return new BigDecimal(obj.get(key).getAsString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double getDouble(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            String s = obj.get(key).getAsString();
            if (s == null || s.isBlank()) {
                return null;
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
