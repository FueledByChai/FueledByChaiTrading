package com.fueledbychai.time;

public class Span implements AutoCloseable {

    public static final String LATENCY_LOGGER_NAME = "latency";

    private final long t0 = TimeUtil.nowNs();
    private final String phase, traceId;
    public static final org.slf4j.Logger L = org.slf4j.LoggerFactory.getLogger(LATENCY_LOGGER_NAME);

    public static Span start(String phase, String traceId) {
        return new Span(phase, traceId);
    }

    private Span(String phase, String traceId) {
        this.phase = phase;
        this.traceId = traceId;
    }

    @Override
    public void close() {
        long dtNs = TimeUtil.nowNs() - t0;
        // print whole ms OR fractional:
        L.info("t={} phase={} dtMs={}", traceId, phase, TimeUtil.toMs(dtNs));
        // or: L.info("t={} phase={} dtMs={}", traceId, phase, String.format("%.3f",
        // T.toMsF(dtNs)));
    }
}
