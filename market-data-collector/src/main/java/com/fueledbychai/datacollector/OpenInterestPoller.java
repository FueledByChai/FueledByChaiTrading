package com.fueledbychai.datacollector;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

import com.fueledbychai.datacollector.MarketDataCollector.FundingTarget;
import com.fueledbychai.datacollector.OpenInterestSources.OpenInterestSource;
import com.fueledbychai.datarecording.MarketDataRecorder;
import com.fueledbychai.datarecording.RecordedOpenInterest;

/**
 * Polls each perp instrument's REST open-interest endpoint on a fixed cadence and writes a uniform
 * {@link RecordedOpenInterest} per observation. Same shape as {@link FundingPoller} — slow,
 * off the streaming path, one target failure logged and skipped, not aborting the sweep. Reuses
 * the collector's perp {@link FundingTarget} list (funding and OI cover the same instruments).
 */
final class OpenInterestPoller {

    private static final Logger LOG = System.getLogger(OpenInterestPoller.class.getName());

    private final MarketDataRecorder recorder;
    private final List<Resolved> resolved = new ArrayList<>();

    OpenInterestPoller(MarketDataRecorder recorder, List<FundingTarget> targets) {
        this.recorder = recorder;
        for (FundingTarget t : targets) {
            OpenInterestSource source = OpenInterestSources.forExchange(t.exchange());
            if (source == null) {
                LOG.log(Level.INFO, "No open-interest source for {0}; not polling {1}",
                        t.exchange().getExchangeName(), t.ticker().getSymbol());
                continue;
            }
            resolved.add(new Resolved(source, t));
        }
    }

    int targetCount() {
        return resolved.size();
    }

    void poll() {
        int ok = 0;
        int empty = 0;
        int failed = 0;
        for (Resolved r : resolved) {
            String label = r.target.exchange().getExchangeName() + " " + r.target.ticker().getSymbol();
            try {
                RecordedOpenInterest oi = r.source.fetch(r.target.ticker());
                if (oi == null || oi.openInterest() == null) {
                    empty++;
                    LOG.log(Level.DEBUG, "Open-interest poll returned nothing for {0}", label);
                    continue;
                }
                recorder.recordOpenInterest(oi);
                ok++;
            } catch (RuntimeException e) {
                failed++;
                LOG.log(Level.WARNING, "Open-interest poll failed for " + label + ": " + e, e);
            }
        }
        Level level = failed > 0 ? Level.WARNING : Level.INFO;
        LOG.log(level, "open-interest poll | recorded={0} empty={1} failed={2} of {3}",
                ok, empty, failed, resolved.size());
    }

    private record Resolved(OpenInterestSource source, FundingTarget target) {
    }
}
