package com.fueledbychai.time;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Span implements AutoCloseable {

    public static final String LATENCY_LOGGER_NAME = "latency";

    private final long t0 = TimeUtil.nowNs();
    private final long startTimeMs = TimeUtil.wallMs(); // Use wall clock time for actual timestamp
    private final String phase, traceId;
    public static final org.slf4j.Logger L = org.slf4j.LoggerFactory.getLogger(LATENCY_LOGGER_NAME);
    ZonedDateTime startTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTimeMs), ZoneOffset.UTC);

    protected static String format = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    protected static DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);

    public static Span start(String phase, String traceId) {
        return new Span(phase, traceId);
    }

    private Span(String phase, String traceId) {
        this.phase = phase;
        this.traceId = traceId;
    }

    @Override
    public void close() {
        long now = TimeUtil.nowNs();
        long dtNs = now - t0;
        long endTimeMs = TimeUtil.wallMs(); // Use wall clock time for actual timestamp
        String nowDateString = formatter
                .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(endTimeMs), ZoneOffset.UTC));
        // print whole ms OR fractional:
        L.info("t={} phase={} start={} end={} dtMs={}", traceId, phase, startTime.format(formatter), nowDateString,
                TimeUtil.toMs(dtNs));
        // or: L.info("t={} phase={} dtMs={}", traceId, phase, String.format("%.3f",
        // T.toMsF(dtNs)));
    }
}
