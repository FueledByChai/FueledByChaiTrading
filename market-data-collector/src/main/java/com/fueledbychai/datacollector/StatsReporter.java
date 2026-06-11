package com.fueledbychai.datacollector;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fueledbychai.datarecording.parquet.ParquetMarketDataRecorder;
import com.fueledbychai.datarecording.parquet.ParquetMarketDataRecorder.StreamSnapshot;

/**
 * Periodic one-block health line — the headless collector's only observability surface.
 *
 * <p>Each tick logs every (exchange, symbol, dataType) stream's cumulative rows, ingest rate
 * since the previous tick, and age of the last event, plus the global queue fill and overflow
 * drops. It is built to be tail-/grep-able: the block is logged at {@code WARNING} (not
 * {@code INFO}) whenever any stream is stale or rows have been dropped, so a plain
 * {@code grep WARNING} over the log surfaces a dead feed or a saturated queue without parsing.
 *
 * <p>Not thread-safe: a single scheduled thread is expected to call {@link #report()} (it
 * carries the previous-tick counters needed to compute rates).
 */
final class StatsReporter {

    private static final Logger LOG = System.getLogger(StatsReporter.class.getName());

    private final ParquetMarketDataRecorder recorder;
    private final long staleSeconds;
    private final Map<String, Long> prevRows = new HashMap<>();
    private long prevNanos;

    StatsReporter(ParquetMarketDataRecorder recorder, long staleSeconds) {
        this.recorder = recorder;
        this.staleSeconds = staleSeconds;
    }

    void report() {
        long nowNanos = System.nanoTime();
        double elapsedSec = prevNanos == 0L ? Double.NaN : (nowNanos - prevNanos) / 1e9;
        prevNanos = nowNanos;

        List<StreamSnapshot> streams = recorder.statsSnapshot();
        long nowMicros = System.currentTimeMillis() * 1_000L;
        long dropped = recorder.droppedRows();

        StringBuilder sb = new StringBuilder(128 + streams.size() * 80);
        sb.append(String.format("collector stats | streams=%d queue=%d/%d dropped=%d",
                streams.size(), recorder.queueDepth(), recorder.queueCapacity(), dropped));

        boolean alarm = dropped > 0;
        for (StreamSnapshot s : streams) {
            long total = s.rows();
            Long prev = prevRows.put(streamKey(s), total);
            String rate = (prev == null || Double.isNaN(elapsedSec) || elapsedSec <= 0.0)
                    ? "   --/s"
                    : String.format("%6.1f/s", (total - prev) / elapsedSec);
            double ageSec = s.lastEventMicros() == 0L
                    ? Double.NaN
                    : (nowMicros - s.lastEventMicros()) / 1e6;
            boolean stale = !Double.isNaN(ageSec) && ageSec > staleSeconds;
            alarm |= stale;
            sb.append(String.format("%n  %-15s %-12s %-13s %10d rows  %s  age %s%s",
                    s.exchange(), s.symbol(), s.dataType(), total, rate,
                    Double.isNaN(ageSec) ? "n/a" : String.format("%.1fs", ageSec),
                    stale ? "  ** STALE **" : ""));
        }
        LOG.log(alarm ? Level.WARNING : Level.INFO, sb.toString());
    }

    private static String streamKey(StreamSnapshot s) {
        return s.exchange() + '|' + s.symbol() + '|' + s.dataType();
    }
}
