package com.fueledbychai.datarecording.parquet;

import java.nio.file.Path;

/**
 * Tuning for {@link ParquetMarketDataRecorder}. Use {@link #defaults(Path)} and the
 * {@code with*} methods to override individual knobs.
 *
 * @param rootDir          partition tree root ({@code data/} in the layout docs)
 * @param maxRollMillis    close + reopen a partition's file once it has been open this long
 * @param maxRollRows      close + reopen a partition's file once it holds this many rows
 * @param queueCapacity    bounded hand-off queue size; overflow is dropped with a counter
 * @param drainPollMillis  how long the writer thread blocks waiting for the next item
 * @param maxBatchRows     max rows pulled from the queue per write cycle
 * @param decimalPrecision Parquet DECIMAL precision applied to every un-annotated BigDecimal
 * @param decimalScale     Parquet DECIMAL scale applied to every un-annotated BigDecimal
 */
public record RecorderConfig(
        Path rootDir,
        long maxRollMillis,
        long maxRollRows,
        int queueCapacity,
        long drainPollMillis,
        int maxBatchRows,
        int decimalPrecision,
        int decimalScale) {

    /** Sensible defaults: 15-min / 2M-row rolls, 200k-deep queue, 38,18 decimals. */
    public static RecorderConfig defaults(Path rootDir) {
        return new RecorderConfig(
                rootDir,
                15L * 60L * 1000L,
                2_000_000L,
                200_000,
                200L,
                10_000,
                38,
                18);
    }

    public RecorderConfig withMaxRollMillis(long v) {
        return new RecorderConfig(rootDir, v, maxRollRows, queueCapacity, drainPollMillis, maxBatchRows, decimalPrecision, decimalScale);
    }

    public RecorderConfig withMaxRollRows(long v) {
        return new RecorderConfig(rootDir, maxRollMillis, v, queueCapacity, drainPollMillis, maxBatchRows, decimalPrecision, decimalScale);
    }

    public RecorderConfig withQueueCapacity(int v) {
        return new RecorderConfig(rootDir, maxRollMillis, maxRollRows, v, drainPollMillis, maxBatchRows, decimalPrecision, decimalScale);
    }

    public RecorderConfig withMaxBatchRows(int v) {
        return new RecorderConfig(rootDir, maxRollMillis, maxRollRows, queueCapacity, drainPollMillis, v, decimalPrecision, decimalScale);
    }
}
