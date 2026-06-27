package com.fueledbychai.datarecording.parquet;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;

import com.fueledbychai.datarecording.MarketDataRecorder;
import com.fueledbychai.datarecording.RecordedBookEvent;
import com.fueledbychai.datarecording.RecordedBookSnapshot;
import com.fueledbychai.datarecording.RecordedFundingRate;
import com.fueledbychai.datarecording.RecordedOpenInterest;
import com.fueledbychai.datarecording.RecordedOwnEvent;
import com.fueledbychai.datarecording.RecordedTrade;
import com.jerolba.carpet.CarpetWriter;

/**
 * Partitioned-Parquet {@link MarketDataRecorder}. Each row is routed to a
 * {@code {dataType}/exchange=/symbol=/date=} partition (see {@link PartitionKey}) and
 * written by Carpet (Java record &rarr; Parquet, zstd-compressed). One file per partition
 * is open at a time; it rolls when it gets old or large
 * ({@link RecorderConfig#maxRollMillis()} / {@link RecorderConfig#maxRollRows()}). A daily
 * {@link ParquetCompactor} pass later merges each partition's parts into one sorted file.
 *
 * <h3>Threading</h3>
 * {@code record*} methods are called from many feed threads and never block: they stamp a
 * {@link PartitionKey} and hand the row to a bounded queue (overflow is dropped with a
 * counter, mirroring chaiwala's {@code MetricsWriter}). A single writer thread owns every
 * open Carpet writer, so no per-writer locking is needed. {@link #flush()} and
 * {@link #close()} coordinate with that thread via an in-band control message / join.
 */
public final class ParquetMarketDataRecorder implements MarketDataRecorder {

    private static final Logger LOG = System.getLogger(ParquetMarketDataRecorder.class.getName());

    static final String DATA_TYPE_TRADES = "trades";
    static final String DATA_TYPE_BOOK_EVENTS = "book_events";
    static final String DATA_TYPE_BOOK_SNAPSHOTS = "book_snapshots";
    static final String DATA_TYPE_OWN_EVENTS = "own_events";
    static final String DATA_TYPE_FUNDING = "funding";
    static final String DATA_TYPE_OPEN_INTEREST = "open_interest";

    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);

    private final RecorderConfig cfg;
    private final BlockingQueue<QueueItem> queue;
    private final Thread writerThread;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong fileSeq = new AtomicLong();

    /** Per-stream ingest counters for the collector's health line. Concurrent: touched by
     * every feed thread on accept, read by the stats reporter. Keyed by {@link #streamKey}. */
    private final ConcurrentHashMap<String, StreamStat> stats = new ConcurrentHashMap<>();

    /** Open writers keyed by partition. Touched ONLY by {@link #writerThread}. */
    private final Map<PartitionKey, PartitionWriter> writers = new HashMap<>();

    private volatile boolean running = true;

    public ParquetMarketDataRecorder(RecorderConfig cfg) {
        this.cfg = cfg;
        this.queue = new ArrayBlockingQueue<>(cfg.queueCapacity());
        this.writerThread = new Thread(this::drainLoop, "ParquetRecorder-writer");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
        LOG.log(Level.INFO, "ParquetMarketDataRecorder started: root={0} rollMs={1} rollRows={2} queue={3}",
                cfg.rootDir(), cfg.maxRollMillis(), cfg.maxRollRows(), cfg.queueCapacity());
    }

    // ------------------------------------------------------------------ contract

    @Override
    public void recordTrade(RecordedTrade t) {
        if (t == null) {
            return;
        }
        enqueue(PartitionKey.of(DATA_TYPE_TRADES, t.exchange(), t.symbol(), t.recvTimestampMicros()),
                t.recvTimestampMicros(), t);
    }

    @Override
    public void recordBookEvent(RecordedBookEvent e) {
        if (e == null) {
            return;
        }
        enqueue(PartitionKey.of(DATA_TYPE_BOOK_EVENTS, e.exchange(), e.symbol(), e.recvTimestampMicros()),
                e.recvTimestampMicros(), e);
    }

    @Override
    public void recordBookSnapshot(RecordedBookSnapshot s) {
        if (s == null) {
            return;
        }
        enqueue(PartitionKey.of(DATA_TYPE_BOOK_SNAPSHOTS, s.exchange(), s.symbol(), s.recvTimestampMicros()),
                s.recvTimestampMicros(), s);
    }

    @Override
    public void recordOwnEvent(RecordedOwnEvent o) {
        if (o == null) {
            return;
        }
        enqueue(PartitionKey.of(DATA_TYPE_OWN_EVENTS, o.exchange(), o.symbol(), o.recvTimestampMicros()),
                o.recvTimestampMicros(), o);
    }

    @Override
    public void recordFunding(RecordedFundingRate f) {
        if (f == null) {
            return;
        }
        enqueue(PartitionKey.of(DATA_TYPE_FUNDING, f.exchange(), f.symbol(), f.recvTimestampMicros()),
                f.recvTimestampMicros(), f);
    }

    @Override
    public void recordOpenInterest(RecordedOpenInterest oi) {
        if (oi == null) {
            return;
        }
        enqueue(PartitionKey.of(DATA_TYPE_OPEN_INTEREST, oi.exchange(), oi.symbol(), oi.recvTimestampMicros()),
                oi.recvTimestampMicros(), oi);
    }

    private void enqueue(PartitionKey key, long recvMicros, Object row) {
        if (!running) {
            return; // closed; per contract further records are illegal — drop quietly
        }
        if (queue.offer(new DataRow(key, row))) {
            StreamStat s = stats.computeIfAbsent(streamKey(key),
                    k -> new StreamStat(key.exchange(), key.symbol(), key.dataType()));
            s.rows.incrementAndGet();
            s.lastEventMicros = recvMicros; // last accepted; monotonic enough for an age readout
        } else {
            long n = dropped.incrementAndGet();
            if (n == 1 || n % 10_000 == 0) {
                LOG.log(Level.WARNING, "ParquetMarketDataRecorder queue full, dropping rows (total dropped: {0})", n);
            }
        }
    }

    private static String streamKey(PartitionKey key) {
        return key.exchange() + '|' + key.symbol() + '|' + key.dataType();
    }

    /** Total rows dropped due to queue overflow since start (telemetry). */
    public long droppedRows() {
        return dropped.get();
    }

    /** Current depth of the hand-off queue (rows accepted but not yet written). */
    public int queueDepth() {
        return queue.size();
    }

    /** Capacity of the hand-off queue — pairs with {@link #queueDepth()} for a fill ratio. */
    public int queueCapacity() {
        return cfg.queueCapacity();
    }

    /**
     * Immutable per-stream ingest snapshot for the collector's health line. {@code rows} is
     * cumulative accepted rows since start; {@code lastEventMicros} is the recv-time of the
     * most recent accepted row (0 if none yet) for an age/staleness readout.
     */
    public record StreamSnapshot(String exchange, String symbol, String dataType,
                                 long rows, long lastEventMicros) {
    }

    /** Per-stream counters, sorted by exchange/symbol/dataType. Safe to call from any thread. */
    public List<StreamSnapshot> statsSnapshot() {
        List<StreamSnapshot> out = new ArrayList<>(stats.size());
        for (StreamStat s : stats.values()) {
            out.add(new StreamSnapshot(s.exchange, s.symbol, s.dataType, s.rows.get(), s.lastEventMicros));
        }
        out.sort(Comparator.comparing(StreamSnapshot::exchange)
                .thenComparing(StreamSnapshot::symbol)
                .thenComparing(StreamSnapshot::dataType));
        return out;
    }

    @Override
    public void flush() {
        if (!running) {
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        if (!queue.offer(new Control(this::closeAllOpenWriters, done))) {
            return; // queue saturated; best-effort flush skipped
        }
        try {
            done.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (!running) {
            return;
        }
        running = false;
        try {
            writerThread.join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        // The writer thread has exited (or timed out); we now own the writers map.
        closeAllOpenWriters();
        long d = dropped.get();
        if (d > 0) {
            LOG.log(Level.WARNING, "ParquetMarketDataRecorder closed; {0} rows were dropped due to overflow this run", d);
        }
    }

    // ------------------------------------------------------------------ writer thread

    private void drainLoop() {
        while (running || !queue.isEmpty()) {
            QueueItem first;
            try {
                first = queue.poll(cfg.drainPollMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            long now = System.currentTimeMillis();
            if (first == null) {
                rollSweep(now); // idle: close out files old enough to roll so they become readable
                continue;
            }
            List<QueueItem> batch = new ArrayList<>(cfg.maxBatchRows());
            batch.add(first);
            queue.drainTo(batch, cfg.maxBatchRows() - 1);
            processBatch(batch, now);
            rollSweep(now);
        }
    }

    private void processBatch(List<QueueItem> batch, long now) {
        LinkedHashMap<PartitionKey, List<Object>> pending = new LinkedHashMap<>();
        for (QueueItem item : batch) {
            if (item instanceof DataRow d) {
                pending.computeIfAbsent(d.key(), k -> new ArrayList<>()).add(d.row());
            } else if (item instanceof Control c) {
                // Preserve ordering: everything queued before the control must be written first.
                flushPending(pending, now);
                pending.clear();
                try {
                    c.action().run();
                } catch (RuntimeException re) {
                    LOG.log(Level.WARNING, "ParquetMarketDataRecorder control action failed", re);
                } finally {
                    c.latch().countDown();
                }
            }
        }
        flushPending(pending, now);
    }

    private void flushPending(Map<PartitionKey, List<Object>> pending, long now) {
        for (Map.Entry<PartitionKey, List<Object>> e : pending.entrySet()) {
            PartitionKey key = e.getKey();
            try {
                writerFor(key, now).writeAll(e.getValue());
            } catch (IOException io) {
                LOG.log(Level.WARNING, "Write failed for partition " + key.relativeDir() + "; dropping its open writer", io);
                discardWriter(key);
            }
        }
    }

    private PartitionWriter writerFor(PartitionKey key, long now) throws IOException {
        PartitionWriter w = writers.get(key);
        if (w == null) {
            w = createWriter(key, now);
            writers.put(key, w);
        }
        return w;
    }

    private PartitionWriter createWriter(PartitionKey key, long now) throws IOException {
        File dir = cfg.rootDir().resolve(key.relativeDir()).toFile();
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Could not create partition dir " + dir);
        }
        String name = "part-" + FILE_TS.format(Instant.ofEpochMilli(now)) + "-" + fileSeq.getAndIncrement() + ".parquet";
        File file = new File(dir, name);
        CarpetWriter<?> cw = newCarpetWriter(file, key.dataType());
        return new PartitionWriter(cw, file, now);
    }

    private CarpetWriter<?> newCarpetWriter(File file, String dataType) throws IOException {
        return switch (dataType) {
            case DATA_TYPE_TRADES -> buildCarpet(file, RecordedTrade.class);
            case DATA_TYPE_BOOK_EVENTS -> buildCarpet(file, RecordedBookEvent.class);
            case DATA_TYPE_BOOK_SNAPSHOTS -> buildCarpet(file, RecordedBookSnapshot.class);
            case DATA_TYPE_OWN_EVENTS -> buildCarpet(file, RecordedOwnEvent.class);
            case DATA_TYPE_FUNDING -> buildCarpet(file, RecordedFundingRate.class);
            case DATA_TYPE_OPEN_INTEREST -> buildCarpet(file, RecordedOpenInterest.class);
            default -> throw new IllegalArgumentException("Unknown dataType: " + dataType);
        };
    }

    private <T> CarpetWriter<T> buildCarpet(File file, Class<T> type) throws IOException {
        return new CarpetWriter.Builder<T>(new LocalOutputFile(file.toPath()), type)
                .withCompressionCodec(CompressionCodecName.ZSTD)
                .withDefaultDecimal(cfg.decimalPrecision(), cfg.decimalScale())
                .withBigDecimalScaleAdjustment(RoundingMode.HALF_UP)
                .build();
    }

    /** Close + reopen-on-next-write any partition whose file has aged out or filled up. */
    private void rollSweep(long now) {
        Iterator<Map.Entry<PartitionKey, PartitionWriter>> it = writers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PartitionKey, PartitionWriter> e = it.next();
            if (e.getValue().shouldRoll(now, cfg)) {
                closeQuietly(e.getKey(), e.getValue());
                it.remove();
            }
        }
    }

    /** Close and forget all open writers (writes their footers, making the files readable). */
    private void closeAllOpenWriters() {
        for (Map.Entry<PartitionKey, PartitionWriter> e : writers.entrySet()) {
            closeQuietly(e.getKey(), e.getValue());
        }
        writers.clear();
    }

    private void discardWriter(PartitionKey key) {
        PartitionWriter w = writers.remove(key);
        if (w != null) {
            closeQuietly(key, w);
        }
    }

    private void closeQuietly(PartitionKey key, PartitionWriter w) {
        try {
            w.close();
        } catch (IOException io) {
            LOG.log(Level.WARNING, "Failed to close writer for " + key.relativeDir(), io);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static final class PartitionWriter {
        private final CarpetWriter<?> writer;
        final File file;
        private final long openedAtMillis;
        private long rows;

        PartitionWriter(CarpetWriter<?> writer, File file, long openedAtMillis) {
            this.writer = writer;
            this.file = file;
            this.openedAtMillis = openedAtMillis;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        void writeAll(List<Object> batch) throws IOException {
            ((CarpetWriter) writer).write(batch);
            rows += batch.size();
        }

        boolean shouldRoll(long now, RecorderConfig cfg) {
            return (now - openedAtMillis) >= cfg.maxRollMillis() || rows >= cfg.maxRollRows();
        }

        void close() throws IOException {
            writer.close();
        }
    }

    /** Mutable ingest counters for one (exchange, symbol, dataType) stream. */
    private static final class StreamStat {
        final String exchange;
        final String symbol;
        final String dataType;
        final AtomicLong rows = new AtomicLong();
        volatile long lastEventMicros;

        StreamStat(String exchange, String symbol, String dataType) {
            this.exchange = exchange;
            this.symbol = symbol;
            this.dataType = dataType;
        }
    }

    private sealed interface QueueItem permits DataRow, Control {
    }

    private record DataRow(PartitionKey key, Object row) implements QueueItem {
    }

    private record Control(Runnable action, CountDownLatch latch) implements QueueItem {
    }
}
