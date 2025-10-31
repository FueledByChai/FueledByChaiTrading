package com.fueledbychai.time;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WsLatency {
    private static final Logger LAT = LoggerFactory.getLogger(Span.LATENCY_LOGGER_NAME);

    public static void onMessage(String type, String traceId, long recvMs, long exchangeEventTsMs) {
        long exDelayMs = recvMs - exchangeEventTsMs;
        ZonedDateTime exTs = ZonedDateTime.ofInstant(Instant.ofEpochMilli(exchangeEventTsMs), ZoneOffset.UTC);

        LAT.info("t={} phase=WS_MSG type={} exchTime={} exDelayMs={}", traceId, type, exTs.format(Span.formatter),
                exDelayMs);
    }
}
