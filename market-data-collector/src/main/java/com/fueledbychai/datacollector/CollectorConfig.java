package com.fueledbychai.datacollector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;

/**
 * Where the collector writes and what it records.
 *
 * @param rootDir              partition-tree root passed to the Parquet recorder
 * @param instruments          venue/instruments to subscribe
 * @param statsIntervalSeconds how often to log the per-stream health line (0 disables it)
 * @param staleSeconds         a stream with no event newer than this is flagged STALE in the line
 * @param fundingPollSeconds   how often to poll perp funding rates via REST (0 disables it)
 * @param oiPollSeconds        how often to poll perp open interest via REST (0 disables it)
 */
public record CollectorConfig(Path rootDir, List<InstrumentSpec> instruments,
        long statsIntervalSeconds, long staleSeconds, long fundingPollSeconds, long oiPollSeconds) {

    /**
     * Load from a {@code .properties} file:
     * <pre>
     *   collector.root.dir=/data/marketdata
     *   collector.instruments=PARADEX:SOL:PERPETUAL_FUTURES,BINANCE_FUTURES:SOL:PERPETUAL_FUTURES,BINANCE_SPOT:SOL:CRYPTO_SPOT
     *   collector.stats.interval.seconds=30   # 0 to disable the health line
     *   collector.stats.stale.seconds=60      # flag a stream STALE past this idle gap
     *   collector.funding.poll.seconds=60     # 0 to disable perp funding-rate polling
     *   collector.oi.poll.seconds=60          # 0 to disable perp open-interest polling
     * </pre>
     */
    public static CollectorConfig fromProperties(Path file) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        String root = p.getProperty("collector.root.dir", "data");
        String instruments = p.getProperty("collector.instruments", "");
        long statsInterval = Long.parseLong(p.getProperty("collector.stats.interval.seconds", "30").trim());
        long stale = Long.parseLong(p.getProperty("collector.stats.stale.seconds", "60").trim());
        long fundingPoll = Long.parseLong(p.getProperty("collector.funding.poll.seconds", "60").trim());
        long oiPoll = Long.parseLong(p.getProperty("collector.oi.poll.seconds", "60").trim());
        List<InstrumentSpec> specs = new ArrayList<>();
        for (String token : instruments.split(",")) {
            if (!token.isBlank()) {
                specs.add(InstrumentSpec.parse(token));
            }
        }
        if (specs.isEmpty()) {
            throw new IllegalArgumentException("collector.instruments is empty in " + file);
        }
        return new CollectorConfig(Path.of(root), List.copyOf(specs), statsInterval, stale, fundingPoll, oiPoll);
    }

    /** Built-in SOL-across-all-venues default for a quick local run with no config file. */
    public static CollectorConfig defaultConfig() {
        List<InstrumentSpec> specs = List.of(
                new InstrumentSpec(Exchange.PARADEX, "SOL", InstrumentType.PERPETUAL_FUTURES),
                new InstrumentSpec(Exchange.HIBACHI, "SOL", InstrumentType.PERPETUAL_FUTURES),
                new InstrumentSpec(Exchange.BINANCE_FUTURES, "SOL", InstrumentType.PERPETUAL_FUTURES),
                new InstrumentSpec(Exchange.BINANCE_SPOT, "SOL", InstrumentType.CRYPTO_SPOT),
                new InstrumentSpec(Exchange.OKX, "SOL", InstrumentType.PERPETUAL_FUTURES));
        return new CollectorConfig(Path.of("data"), specs, 30L, 60L, 60L, 60L);
    }
}
