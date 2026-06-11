package com.fueledbychai.datarecording.adapter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.datarecording.MarketDataRecorder;
import com.fueledbychai.datarecording.RecordTimes;
import com.fueledbychai.datarecording.RecordedBookSnapshot;
import com.fueledbychai.datarecording.RecordedBookSnapshot.Level;
import com.fueledbychai.marketdata.IOrderBook;
import com.fueledbychai.marketdata.IOrderBook.BidSizePair;
import com.fueledbychai.marketdata.OrderBookUpdateListener;

/**
 * Bridges the library's snapshot-only {@link OrderBookUpdateListener} into the recording
 * contract: on each book update it captures the top {@code depth} levels of each side as a
 * {@link RecordedBookSnapshot} and hands it to a {@link MarketDataRecorder}.
 *
 * <p>The public market-data API emits full book state with no sequence number, so each
 * captured snapshot is marked as an {@code anchor} with {@code exchangeSeq == null} and
 * {@code bookEpoch == 0}. True incremental deltas (with epochs) arrive via the separate
 * raw-delta tap on delta-native venues, not through this adapter.
 */
public final class RecordingOrderBookListener implements OrderBookUpdateListener {

    private final MarketDataRecorder recorder;
    private final int depth;

    /**
     * @param recorder sink to write snapshots to
     * @param depth    number of levels per side to capture
     */
    public RecordingOrderBookListener(MarketDataRecorder recorder, int depth) {
        this.recorder = recorder;
        this.depth = depth;
    }

    @Override
    public void orderBookUpdated(Ticker ticker, IOrderBook book, ZonedDateTime timestamp) {
        long recv = RecordTimes.nowMicros();
        long evt = RecordTimes.micros(timestamp);
        RecordedBookSnapshot snapshot = new RecordedBookSnapshot(
                ticker.getExchange().getExchangeName(),
                ticker.getSymbol(),
                recv,
                evt,
                null,
                0L,
                true,
                toLevels(book.getBids(depth)),
                toLevels(book.getAsks(depth)));
        recorder.recordBookSnapshot(snapshot);
    }

    // Higher-frequency BBO/imbalance callbacks are not recorded here — the full-book
    // snapshot above already captures top-of-book state. Left as no-ops by design.
    @Override
    public void bestBidUpdated(Ticker ticker, BigDecimal bestBid, Double bidSize, ZonedDateTime timeStamp) {
    }

    @Override
    public void bestAskUpdated(Ticker ticker, BigDecimal bestAsk, Double askSize, ZonedDateTime timeStamp) {
    }

    @Override
    public void orderBookImbalanceUpdated(Ticker ticker, BigDecimal imbalance, ZonedDateTime timeStamp) {
    }

    private static List<Level> toLevels(List<BidSizePair> pairs) {
        List<Level> levels = new ArrayList<>(pairs == null ? 0 : pairs.size());
        if (pairs != null) {
            for (BidSizePair p : pairs) {
                if (p == null || p.getPrice() == null) {
                    continue;
                }
                double size = p.getSize() == null ? 0.0 : p.getSize();
                levels.add(new Level(p.getPrice(), size));
            }
        }
        return levels;
    }
}
