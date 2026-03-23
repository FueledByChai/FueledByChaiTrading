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
    private final org.slf4j.Logger logger;
    public static final org.slf4j.Logger L = org.slf4j.LoggerFactory.getLogger(LATENCY_LOGGER_NAME);
    ZonedDateTime startTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTimeMs), ZoneOffset.UTC);

    protected static String format = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    protected static DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);

    public static Span start(String phase, String traceId) {
        return new Span(phase, traceId, L);
    }

    /**
     * Start a span that logs to a broker-specific latency logger.
     * Use hierarchical names like "latency.aster" or "latency.paradex"
     * so logback can route to separate files while the parent "latency"
     * logger still captures everything.
     */
    public static Span start(String phase, String traceId, String loggerName) {
        return new Span(phase, traceId, org.slf4j.LoggerFactory.getLogger(loggerName));
    }

    private Span(String phase, String traceId, org.slf4j.Logger logger) {
        this.phase = phase;
        this.traceId = traceId;
        this.logger = logger;
    }

    @Override
    public void close() {
        long now = TimeUtil.nowNs();
        long dtNs = now - t0;
        long endTimeMs = TimeUtil.wallMs(); // Use wall clock time for actual timestamp
        String nowDateString = formatter
                .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(endTimeMs), ZoneOffset.UTC));
        // print whole ms OR fractional:
        logger.info("t={} phase={} start={} end={} dtMs={}", traceId, phase, startTime.format(formatter),
                nowDateString, TimeUtil.toMs(dtNs));
    }
}
