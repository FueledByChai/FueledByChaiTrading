package com.fueledbychai.datacollector;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fueledbychai.datarecording.parquet.ParquetCompactor;
import com.fueledbychai.datarecording.parquet.ParquetMarketDataRecorder;
import com.fueledbychai.datarecording.parquet.RecorderConfig;

/**
 * Entry point for the always-on market-data collector.
 *
 * <pre>
 *   java ... com.fueledbychai.datacollector.CollectorApp [config.properties]
 * </pre>
 *
 * With no argument it records SOL across all five venues into {@code ./data}. It writes
 * partitioned Parquet via {@link ParquetMarketDataRecorder}, compacts completed days once a
 * day via {@link ParquetCompactor}, and flushes/closes cleanly on SIGINT.
 */
public final class CollectorApp {

    private static final Logger LOG = System.getLogger(CollectorApp.class.getName());

    private CollectorApp() {
    }

    /** Set a system property only if the operator hasn't already overridden it. */
    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    public static void main(String[] args) throws Exception {
        // The collector wants the richest book feed each venue offers. These toggles are read
        // once, lazily, from system properties by the venue config/engine singletons — so set
        // them before any engine is constructed (i.e. before collector.start()):
        //   - Hibachi live_book: public top-N windowed feed (~5ms); no account entitlement.
        //   - Paradex deltas: true per-level delta stream instead of throttled top-15 snapshots.
        setDefault("hibachi.market.data.live.book", "true");
        setDefault("paradex.orderbook.channel.suffix", "deltas");

        CollectorConfig cfg = args.length > 0
                ? CollectorConfig.fromProperties(Path.of(args[0]))
                : CollectorConfig.defaultConfig();

        RecorderConfig recorderConfig = RecorderConfig.defaults(cfg.rootDir());
        ParquetMarketDataRecorder recorder = new ParquetMarketDataRecorder(recorderConfig);
        MarketDataCollector collector = new MarketDataCollector(recorder);
        ParquetCompactor compactor = new ParquetCompactor();

        ScheduledExecutorService housekeeping = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "collector-housekeeping");
            t.setDaemon(true);
            return t;
        });
        housekeeping.scheduleAtFixedRate(() -> {
            try {
                int n = compactor.compactCompletedDays(cfg.rootDir());
                if (n > 0) {
                    LOG.log(Level.INFO, "Compacted {0} completed-day partitions", n);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Compaction pass failed", e);
            }
        }, 1, 24, TimeUnit.HOURS);

        // Per-stream health line: the collector's only observability surface (no UI). Logs at
        // WARNING when a feed goes stale or the queue overflows, so `grep WARNING` catches it.
        if (cfg.statsIntervalSeconds() > 0) {
            StatsReporter reporter = new StatsReporter(recorder, cfg.staleSeconds());
            long interval = cfg.statsIntervalSeconds();
            housekeeping.scheduleAtFixedRate(() -> {
                try {
                    reporter.report();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Stats report failed", e);
                }
            }, interval, interval, TimeUnit.SECONDS);
        }

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.log(Level.INFO, "Shutting down collector...");
            collector.close();
            recorder.flush();
            recorder.close();
            housekeeping.shutdownNow();
            shutdown.countDown();
        }, "collector-shutdown"));

        collector.start(cfg.instruments());

        // REST funding-rate poller: perps only, minute-scale cadence, off the streaming path.
        // Scheduled after start() so the perp tickers (and their funding intervals) are resolved.
        if (cfg.fundingPollSeconds() > 0) {
            FundingPoller fundingPoller = new FundingPoller(recorder, collector.fundingTargets());
            if (fundingPoller.targetCount() > 0) {
                long fp = cfg.fundingPollSeconds();
                housekeeping.scheduleAtFixedRate(() -> {
                    try {
                        fundingPoller.poll();
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Funding poll sweep failed", e);
                    }
                }, 5, fp, TimeUnit.SECONDS);
                LOG.log(Level.INFO, "Funding poller active: {0} perp targets every {1}s",
                        fundingPoller.targetCount(), cfg.fundingPollSeconds());
            }
        }

        // REST open-interest poller: perps only, same cadence/structure as funding.
        if (cfg.oiPollSeconds() > 0) {
            OpenInterestPoller oiPoller = new OpenInterestPoller(recorder, collector.fundingTargets());
            if (oiPoller.targetCount() > 0) {
                long oi = cfg.oiPollSeconds();
                housekeeping.scheduleAtFixedRate(() -> {
                    try {
                        oiPoller.poll();
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Open-interest poll sweep failed", e);
                    }
                }, 5, oi, TimeUnit.SECONDS);
                LOG.log(Level.INFO, "Open-interest poller active: {0} perp targets every {1}s",
                        oiPoller.targetCount(), cfg.oiPollSeconds());
            }
        }

        LOG.log(Level.INFO, "Collector running ({0} instruments), writing to {1}. Ctrl-C to stop.",
                cfg.instruments().size(), cfg.rootDir().toAbsolutePath());
        shutdown.await();
    }
}
