package com.fueledbychai.time;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class TimeUtil {
    // monotonic start
    public static long nowNs() {
        return System.nanoTime();
    }

    // duration -> whole ms
    public static long toMs(long ns) {
        return TimeUnit.NANOSECONDS.toMillis(ns);
    }

    // duration -> fractional ms (e.g., 12.34 ms)
    public static double toMsF(long ns) {
        return ns / 1_000_000.0;
    }

    // wall clock ms (epoch)
    public static long wallMs() {
        return Instant.now().toEpochMilli();
    }
}
