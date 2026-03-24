package com.fueledbychai.time;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WsLatency {
    private static final Logger LAT = LoggerFactory.getLogger(Span.LATENCY_LOGGER_NAME);

    public static void onMessage(String type, String traceId, long recvMs, long exchangeEventTsMs) {
        onMessage(type, traceId, recvMs, exchangeEventTsMs, LAT);
    }

    public static void onMessage(String type, String traceId, long recvMs, long exchangeEventTsMs, String loggerName) {
        onMessage(type, traceId, recvMs, exchangeEventTsMs, LoggerFactory.getLogger(loggerName));
    }

    private static void onMessage(String type, String traceId, long recvMs, long exchangeEventTsMs, Logger logger) {
        long exDelayMs = recvMs - exchangeEventTsMs;
        ZonedDateTime exTs = ZonedDateTime.ofInstant(Instant.ofEpochMilli(exchangeEventTsMs), ZoneOffset.UTC);

        logger.info("t={} phase=WS_MSG type={} exchTime={} exDelayMs={}", traceId, type, exTs.format(Span.formatter),
                exDelayMs);
    }
}
