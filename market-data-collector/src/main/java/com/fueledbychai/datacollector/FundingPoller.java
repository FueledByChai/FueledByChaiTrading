package com.fueledbychai.datacollector;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

import com.fueledbychai.datacollector.FundingSources.FundingSource;
import com.fueledbychai.datacollector.MarketDataCollector.FundingTarget;
import com.fueledbychai.datarecording.MarketDataRecorder;
import com.fueledbychai.datarecording.RecordedFundingRate;

/**
 * Polls each perp instrument's REST funding endpoint on a fixed cadence and writes a uniform
 * {@link RecordedFundingRate} per observation. Funding moves slowly (settlement every 1–8h), so a
 * coarse poll (minute-scale) captures it without touching the tick-streaming path. Each target is
 * fetched independently — one venue's REST failure (e.g. a geo-blocked Binance call when the proxy
 * is off) is logged and skipped, never aborting the rest of the sweep.
 */
final class FundingPoller {

    private static final Logger LOG = System.getLogger(FundingPoller.class.getName());

    private final MarketDataRecorder recorder;
    private final List<Resolved> resolved = new ArrayList<>();

    FundingPoller(MarketDataRecorder recorder, List<FundingTarget> targets) {
        this.recorder = recorder;
        for (FundingTarget t : targets) {
            FundingSource source = FundingSources.forExchange(t.exchange());
            if (source == null) {
                LOG.log(Level.INFO, "No funding source for {0}; not polling {1}",
                        t.exchange().getExchangeName(), t.ticker().getSymbol());
                continue;
            }
            resolved.add(new Resolved(source, t));
        }
    }

    /** Number of instruments this poller will actually query. */
    int targetCount() {
        return resolved.size();
    }

    /** One sweep across all funding targets. Safe to call from a scheduled thread. */
    void poll() {
        int ok = 0;
        int empty = 0;
        int failed = 0;
        for (Resolved r : resolved) {
            String label = r.target.exchange().getExchangeName() + " " + r.target.ticker().getSymbol();
            try {
                RecordedFundingRate funding = r.source.fetch(r.target.ticker());
                if (funding == null || funding.fundingRate() == null) {
                    empty++;
                    LOG.log(Level.DEBUG, "Funding poll returned no rate for {0}", label);
                    continue;
                }
                recorder.recordFunding(funding);
                ok++;
            } catch (RuntimeException e) {
                failed++;
                LOG.log(Level.WARNING, "Funding poll failed for " + label + ": " + e, e);
            }
        }
        Level level = failed > 0 ? Level.WARNING : Level.INFO;
        LOG.log(level, "funding poll | recorded={0} empty={1} failed={2} of {3}",
                ok, empty, failed, resolved.size());
    }

    private record Resolved(FundingSource source, FundingTarget target) {
    }
}
