package com.fueledbychai.datacollector;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.datarecording.MarketDataRecorder;
import com.fueledbychai.datarecording.adapter.RecordingBookEventListener;
import com.fueledbychai.datarecording.adapter.RecordingOrderFlowListener;
import com.fueledbychai.datarecording.adapter.RecordingWindowedBookListener;
import com.fueledbychai.marketdata.OrderFlowListener;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.RawOrderBookEventListener;
import com.fueledbychai.marketdata.RawOrderBookSubscribable;
import com.fueledbychai.paradex.common.ParadexTickerRegistry;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;

/**
 * Wires each configured {@link InstrumentSpec} to its venue {@link QuoteEngine} and records
 * the public trade tape into a {@link MarketDataRecorder}. One {@link RecordingOrderFlowListener}
 * is attached per instrument; the listener tags every trade with its source exchange/symbol so
 * the recorder routes it to the right partition.
 *
 * <p>L2 book is recorded via {@link RawOrderBookSubscribable}: Hibachi's windowed {@code
 * live_book} frames go to {@link RecordingWindowedBookListener}; every other venue's sequenced
 * per-level deltas go to {@link RecordingBookEventListener}. A venue without the capability is
 * logged and continues with trades only.
 */
public final class MarketDataCollector implements AutoCloseable {

    private static final Logger LOG = System.getLogger(MarketDataCollector.class.getName());

    private final MarketDataRecorder recorder;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final List<FundingTarget> fundingTargets = new ArrayList<>();
    private final Set<QuoteEngine> startedEngines = new LinkedHashSet<>();

    public MarketDataCollector(MarketDataRecorder recorder) {
        this.recorder = recorder;
    }

    /** Subscribe every spec. Unresolvable venues/symbols are logged and skipped, not fatal. */
    public void start(List<InstrumentSpec> specs) {
        for (InstrumentSpec spec : specs) {
            try {
                wire(spec);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Failed to wire " + spec + "; skipping: " + e, e);
            }
        }
        LOG.log(Level.INFO, "MarketDataCollector active: {0} of {1} instruments (trades + L2 book)",
                subscriptions.size(), specs.size());
    }

    private void wire(InstrumentSpec spec) {
        Exchange exchange = spec.exchange();
        String exchangeName = exchange.getExchangeName();
        if (!QuoteEngine.isRegistered(exchange)) {
            LOG.log(Level.WARNING, "No QuoteEngine registered for {0}; skipping", exchangeName);
            return;
        }
        Ticker ticker = resolveTicker(spec);
        if (ticker == null) {
            LOG.log(Level.WARNING, "Could not resolve {0} {1} ({2}); skipping",
                    exchangeName, spec.commonSymbol(), spec.instrumentType());
            return;
        }
        QuoteEngine engine = QuoteEngine.getInstance(exchange);
        if (!engine.started()) {
            engine.startEngine();
        }
        startedEngines.add(engine);

        OrderFlowListener tradeListener = new RecordingOrderFlowListener(recorder);
        engine.subscribeOrderFlow(ticker, tradeListener);
        subscriptions.add(new Subscription(engine, ticker, tradeListener));
        LOG.log(Level.INFO, "Recording trade tape: {0} {1}", exchangeName, ticker.getSymbol());

        // Perps carry funding; register them for the REST funding poller (spot has none).
        if (spec.instrumentType() == InstrumentType.PERPETUAL_FUTURES) {
            fundingTargets.add(new FundingTarget(exchange, ticker));
        }

        wireBook(spec, engine, ticker);
    }

    /**
     * Attach the L2 book recorder if the engine exposes the raw feed. Hibachi's {@code
     * live_book} is windowed and un-sequenced; every other venue is a sequenced per-level
     * delta stream — so each gets the matching adapter (see the design-note archetype table).
     */
    private void wireBook(InstrumentSpec spec, QuoteEngine engine, Ticker ticker) {
        String exchangeName = spec.exchange().getExchangeName();
        if (!(engine instanceof RawOrderBookSubscribable raw)) {
            LOG.log(Level.WARNING, "{0} exposes no raw order book; recording trades only", exchangeName);
            return;
        }
        RawOrderBookEventListener bookListener = spec.exchange().equals(Exchange.HIBACHI)
                ? new RecordingWindowedBookListener(recorder)
                : new RecordingBookEventListener(recorder);
        raw.subscribeRawOrderBook(ticker, bookListener);
        LOG.log(Level.INFO, "Recording L2 book: {0} {1} ({2})",
                exchangeName, ticker.getSymbol(), bookListener.getClass().getSimpleName());
    }

    private static Ticker resolveTicker(InstrumentSpec spec) {
        Exchange exchange = spec.exchange();
        // Paradex resolves through its own registry; everything else via the SPI factory
        // (mirrors how chaiwala's MarketDataLayer special-cases Paradex).
        if (exchange.equals(Exchange.PARADEX)) {
            return ParadexTickerRegistry.getInstance()
                    .lookupByCommonSymbol(spec.instrumentType(), spec.commonSymbol());
        }
        if (!TickerRegistryFactory.isRegistered(exchange)) {
            return null;
        }
        ITickerRegistry registry = TickerRegistryFactory.getInstance(exchange);
        return registry.lookupByCommonSymbol(spec.instrumentType(), spec.commonSymbol());
    }

    @Override
    public void close() {
        for (Subscription s : subscriptions) {
            try {
                s.engine().unsubscribeOrderFlow(s.ticker(), s.listener());
            } catch (RuntimeException e) {
                LOG.log(Level.DEBUG, "Error unsubscribing " + s.ticker(), e);
            }
        }
        subscriptions.clear();
        for (QuoteEngine engine : startedEngines) {
            try {
                engine.stopEngine();
            } catch (RuntimeException e) {
                LOG.log(Level.DEBUG, "Error stopping engine", e);
            }
        }
        startedEngines.clear();
    }

    /**
     * Perp instruments resolved during {@link #start}, for the REST funding poller. Populated
     * only after {@code start()} runs (it resolves the tickers). The {@link Ticker} carries the
     * per-asset funding interval via {@link Ticker#getFundingRateInterval()}.
     */
    public List<FundingTarget> fundingTargets() {
        return List.copyOf(fundingTargets);
    }

    /** A resolved perp instrument (venue + ticker) eligible for funding-rate polling. */
    public record FundingTarget(Exchange exchange, Ticker ticker) {
    }

    private record Subscription(QuoteEngine engine, Ticker ticker, OrderFlowListener listener) {
    }
}
