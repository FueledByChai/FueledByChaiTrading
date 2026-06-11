package com.fueledbychai.datarecording.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fueledbychai.datarecording.RecordedTrade;
import com.fueledbychai.datarecording.Side;
import com.jerolba.carpet.CarpetReader;

class ParquetMarketDataRecorderTest {

    private static final FilenameFilter PARQUET = (d, n) -> n.endsWith(".parquet");

    @Test
    void writesRollsReadsAndCompacts(@TempDir Path root) throws Exception {
        // maxRollRows=3 + one row per write cycle (maxBatchRows=1) deterministically rolls
        // a new file every 3 trades: 10 trades -> files of [3,3,3,1] = 4 parts.
        RecorderConfig cfg = RecorderConfig.defaults(root).withMaxRollRows(3).withMaxBatchRows(1);
        long baseMicros = 1_700_000_000_000_000L; // 2023-11-14 UTC

        try (ParquetMarketDataRecorder rec = new ParquetMarketDataRecorder(cfg)) {
            for (int i = 0; i < 10; i++) {
                rec.recordTrade(new RecordedTrade(
                        "PARADEX", "SOL-USD-PERP",
                        baseMicros + i * 1_000L, baseMicros + i * 1_000L,
                        (long) i, new BigDecimal("150." + i), 1.5 + i, Side.BUY));
            }
            rec.flush();
            assertEquals(0L, rec.droppedRows(), "no rows should be dropped at this volume");
        }

        Path symbolDir = root.resolve("trades/exchange=PARADEX/symbol=SOL-USD-PERP");
        File[] dateDirs = symbolDir.toFile().listFiles(File::isDirectory);
        assertEquals(1, dateDirs.length, "one UTC date partition");
        Path partition = dateDirs[0].toPath();
        assertEquals("date=2023-11-14", dateDirs[0].getName());

        File[] partsBefore = partition.toFile().listFiles(PARQUET);
        assertEquals(4, partsBefore.length, "size-roll should produce 4 part files");

        assertEquals(10, countRows(partsBefore), "all rows readable across rolled files");

        // Compact the day into a single file.
        long compactedRows = new ParquetCompactor().compactPartition(partition, "trades");
        assertEquals(10L, compactedRows);

        File[] partsAfter = partition.toFile().listFiles(PARQUET);
        assertEquals(1, partsAfter.length, "compaction collapses to one file");

        List<RecordedTrade> merged = new CarpetReader<>(partsAfter[0], RecordedTrade.class).toList();
        assertEquals(10, merged.size());
        assertEquals("PARADEX", merged.get(0).exchange());
        assertEquals(new BigDecimal("150.0").compareTo(merged.get(0).price()), 0);
    }

    private static int countRows(File[] parquetFiles) throws Exception {
        int total = 0;
        for (File f : parquetFiles) {
            total += new CarpetReader<>(f, RecordedTrade.class).toList().size();
        }
        return total;
    }
}
