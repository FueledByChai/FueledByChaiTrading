package com.fueledbychai.datarecording.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fueledbychai.data.Ticker;
import com.fueledbychai.datarecording.BookEpochSequencer;
import com.fueledbychai.datarecording.BookSide;
import com.fueledbychai.datarecording.EventAction;
import com.fueledbychai.datarecording.MarketDataRecorder;
import com.fueledbychai.datarecording.RecordTimes;
import com.fueledbychai.datarecording.RecordedBookEvent;
import com.fueledbychai.datarecording.RecordedBookSnapshot;
import com.fueledbychai.datarecording.RecordedBookSnapshot.Level;
import com.fueledbychai.marketdata.RawBookUpdate;
import com.fueledbychai.marketdata.RawOrderBookEventListener;

/**
 * Bridges the raw, sequence-bearing {@link RawOrderBookEventListener} stream from delta-native
 * venues into the recording contract. Each delta message becomes a batch of
 * {@link RecordedBookEvent} rows (the OFI / cancel-rate primitive); each snapshot becomes a
 * {@link RecordedBookSnapshot} resync anchor.
 *
 * <p>Maintains one {@link BookEpochSequencer} per {@code (exchange, symbol)} to stamp
 * {@code bookEpoch} and detect gaps from the exchange sequence number. The raw events are
 * recorded as truth regardless of validity &mdash; the persisted {@code exchangeSeq} +
 * {@code bookEpoch} let the downstream normalization layer recompute {@code book_valid} and
 * exclude corrupted spans.
 *
 * <p>Use this <i>instead of</i> {@link RecordingOrderBookListener} for delta-native venues
 * (avoids redundant throttled snapshots); use {@link RecordingOrderBookListener} for
 * snapshot-native venues that never fire raw events.
 */
public final class RecordingBookEventListener implements RawOrderBookEventListener {

    private final MarketDataRecorder recorder;
    private final Map<String, BookEpochSequencer> sequencers = new ConcurrentHashMap<>();

    public RecordingBookEventListener(MarketDataRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void onBookUpdate(Ticker ticker, RawBookUpdate update) {
        String exchange = ticker.getExchange().getExchangeName();
        String symbol = ticker.getSymbol();
        long seq = update.getSequence();
        long prevSeq = update.getPrevSequence();
        Long exchangeSeq = seq >= 0 ? seq : null;
        Long prevExchangeSeq = prevSeq >= 0 ? prevSeq : null;
        long recv = RecordTimes.nowMicros();
        long evt = RecordTimes.micros(update.getTimestamp());

        BookEpochSequencer sequencer = sequencers.computeIfAbsent(exchange + "|" + symbol,
                k -> new BookEpochSequencer());

        if (update.isSnapshot()) {
            BookEpochSequencer.Result r = sequencer.anchor(seq);
            List<Level> bids = new ArrayList<>();
            List<Level> asks = new ArrayList<>();
            for (RawBookUpdate.Entry e : update.getEntries()) {
                (e.side() == RawBookUpdate.Side.BUY ? bids : asks).add(new Level(e.price(), e.size()));
            }
            // Best-first ordering per the contract (bids high->low, asks low->high).
            bids.sort(Comparator.comparing(Level::price).reversed());
            asks.sort(Comparator.comparing(Level::price));
            recorder.recordBookSnapshot(new RecordedBookSnapshot(
                    exchange, symbol, recv, evt, exchangeSeq, r.epoch(), true, bids, asks));
        } else {
            // Prev-id-continuity venues (Binance pu, OKX prevSeqId) report the id this delta must
            // follow; +1-style venues (Paradex) leave prevSeq == NO_SEQUENCE and fall back to seq+1.
            BookEpochSequencer.Result r = prevSeq >= 0 ? sequencer.delta(seq, prevSeq) : sequencer.delta(seq);
            long epoch = r.epoch();
            for (RawBookUpdate.Entry e : update.getEntries()) {
                recorder.recordBookEvent(new RecordedBookEvent(
                        exchange, symbol, recv, evt, exchangeSeq, prevExchangeSeq, epoch,
                        toBookSide(e.side()), e.price(), e.size(), toAction(e.action()),
                        // Full-depth/sequenced feeds carry no top-N window bounds.
                        null, null));
            }
        }
    }

    private static BookSide toBookSide(RawBookUpdate.Side side) {
        return side == RawBookUpdate.Side.BUY ? BookSide.BID : BookSide.ASK;
    }

    private static EventAction toAction(RawBookUpdate.Action action) {
        return switch (action) {
            case INSERT -> EventAction.ADD;
            case UPDATE -> EventAction.CHANGE;
            case DELETE -> EventAction.DELETE;
        };
    }
}
