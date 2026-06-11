package com.fueledbychai.datarecording.parquet;

import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;

import com.fueledbychai.datarecording.RecordedBookEvent;
import com.fueledbychai.datarecording.RecordedBookSnapshot;
import com.fueledbychai.datarecording.RecordedOwnEvent;
import com.fueledbychai.datarecording.RecordedTrade;
import com.jerolba.carpet.CarpetReader;
import com.jerolba.carpet.CarpetWriter;

/**
 * Merges the many small {@code part-*.parquet} files a live partition accumulates into one
 * compacted file per partition, recovering the small-file penalty that frequent rolling
 * trades for crash-safety. Pure Parquet&rarr;Parquet, so the read path stays single-format
 * the whole time.
 *
 * <p>Rows within each source file are already in receive order; this concatenates the day's
 * files. Global ordering across files is approximate — analysis sorts/asof-joins on the
 * {@code recvTimestampMicros} column, not on file order — so a strict re-sort is left as a
 * later refinement. Compaction is idempotent: a partition holding a single file is skipped,
 * and a partition with a prior compacted file plus new parts is simply re-merged.
 */
public final class ParquetCompactor {

    private final int decimalPrecision;
    private final int decimalScale;

    public ParquetCompactor() {
        this(38, 18);
    }

    public ParquetCompactor(int decimalPrecision, int decimalScale) {
        this.decimalPrecision = decimalPrecision;
        this.decimalScale = decimalScale;
    }

    /**
     * Compact every partition directory under {@code root} whose {@code date=} is strictly
     * before today (UTC) — i.e. days that can no longer receive new live rows. Returns the
     * number of partitions compacted.
     */
    public int compactCompletedDays(Path root) throws IOException {
        return compactCompletedDays(root, LocalDate.now(ZoneOffset.UTC));
    }

    /** Testable variant: compact partitions with {@code date <} {@code today}. */
    public int compactCompletedDays(Path root, LocalDate today) throws IOException {
        File rootDir = root.toFile();
        File[] dataTypeDirs = rootDir.listFiles(File::isDirectory);
        if (dataTypeDirs == null) {
            return 0;
        }
        int compacted = 0;
        for (File dataTypeDir : dataTypeDirs) {
            String dataType = dataTypeDir.getName();
            for (File exchangeDir : listDirs(dataTypeDir)) {
                for (File symbolDir : listDirs(exchangeDir)) {
                    for (File dateDir : listDirs(symbolDir)) {
                        LocalDate date = parseDate(dateDir.getName());
                        if (date != null && date.isBefore(today)) {
                            if (compactPartition(dateDir.toPath(), dataType) > 0) {
                                compacted++;
                            }
                        }
                    }
                }
            }
        }
        return compacted;
    }

    /**
     * Compact one partition directory. Returns rows written, or 0 if there was nothing to do
     * (zero or one file present).
     */
    public long compactPartition(Path partitionDir, String dataType) throws IOException {
        List<File> parts = listParquet(partitionDir);
        if (parts.size() <= 1) {
            return 0;
        }
        File dir = partitionDir.toFile();
        File tmp = new File(dir, ".compacting-" + System.currentTimeMillis() + ".parquet.tmp");
        long rows = mergeByType(parts, tmp, dataType);

        // tmp now holds every source row; safe to drop the sources and swap the result in.
        File finalOut = new File(dir, "part-compacted-" + partitionDate(dir) + ".parquet");
        for (File p : parts) {
            Files.deleteIfExists(p.toPath());
        }
        Files.move(tmp.toPath(), finalOut.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return rows;
    }

    private long mergeByType(List<File> parts, File out, String dataType) throws IOException {
        return switch (dataType) {
            case ParquetMarketDataRecorder.DATA_TYPE_TRADES -> merge(parts, out, RecordedTrade.class);
            case ParquetMarketDataRecorder.DATA_TYPE_BOOK_EVENTS -> merge(parts, out, RecordedBookEvent.class);
            case ParquetMarketDataRecorder.DATA_TYPE_BOOK_SNAPSHOTS -> merge(parts, out, RecordedBookSnapshot.class);
            case ParquetMarketDataRecorder.DATA_TYPE_OWN_EVENTS -> merge(parts, out, RecordedOwnEvent.class);
            default -> throw new IllegalArgumentException("Unknown dataType: " + dataType);
        };
    }

    private <T> long merge(List<File> parts, File out, Class<T> type) throws IOException {
        long n = 0;
        try (CarpetWriter<T> writer = new CarpetWriter.Builder<T>(new LocalOutputFile(out.toPath()), type)
                .withCompressionCodec(CompressionCodecName.ZSTD)
                .withDefaultDecimal(decimalPrecision, decimalScale)
                .withBigDecimalScaleAdjustment(RoundingMode.HALF_UP)
                .build()) {
            for (File part : parts) {
                List<T> rows = new CarpetReader<>(part, type).toList();
                writer.write(rows);
                n += rows.size();
            }
        }
        return n;
    }

    private static List<File> listParquet(Path partitionDir) {
        File[] files = partitionDir.toFile().listFiles((d, name) -> name.endsWith(".parquet"));
        if (files == null) {
            return List.of();
        }
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort((a, b) -> a.getName().compareTo(b.getName()));
        return list;
    }

    private static List<File> listDirs(File parent) {
        File[] dirs = parent.listFiles(File::isDirectory);
        return dirs == null ? List.of() : Arrays.asList(dirs);
    }

    /** {@code date=2026-06-09} &rarr; {@code LocalDate}, or null if the dir isn't a date partition. */
    private static LocalDate parseDate(String dirName) {
        String value = stripPartitionPrefix(dirName, "date=");
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String partitionDate(File dateDir) {
        String value = stripPartitionPrefix(dateDir.getName(), "date=");
        return value != null ? value : "unknown";
    }

    private static String stripPartitionPrefix(String dirName, String prefix) {
        return dirName.startsWith(prefix) ? dirName.substring(prefix.length()) : null;
    }
}
