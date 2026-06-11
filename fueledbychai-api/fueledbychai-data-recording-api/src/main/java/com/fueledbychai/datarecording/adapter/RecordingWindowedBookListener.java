package com.fueledbychai.datarecording.adapter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fueledbychai.data.Ticker;
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
 * Bridges a <i>bounded-window</i>, <i>un-sequenced</i> raw book feed (e.g. Hibachi {@code
 * live_book}) into the recording contract. Each frame is a top-N window of absolute-size levels
 * per side: a {@code Snapshot} frame is the full window, an {@code Update} frame carries only the
 * changed levels (size&nbsp;0 = removed) plus the per-side window bounds.
 *
 * <p>Unlike {@link RecordingBookEventListener} (which uses {@link
 * com.fueledbychai.datarecording.BookEpochSequencer} on the exchange sequence), this venue has no
 * sequence number, so gap detection by {@code seq+1} is impossible &mdash; and unnecessary: the
 * feed is self-healing because each level update is an <i>absolute</i> set, not a relative delta.
 * A dropped frame just leaves one level briefly stale until its next update. The only epoch
 * boundary is a {@code Snapshot} (or reconnect), so {@code bookEpoch} is bumped there and every
 * subsequent delta carries the window bounds the reconstruction needs to prune scroll-out levels.
 *
 * <p>Deltas seen before the first {@code Snapshot} are stamped {@code bookEpoch == 0} so the
 * normalization layer can exclude that un-anchored span.
 */
public final class RecordingWindowedBookListener implements RawOrderBookEventListener {

    private final MarketDataRecorder recorder;
    private final Map<String, Long> epochByStream = new ConcurrentHashMap<>();

    public RecordingWindowedBookListener(MarketDataRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void onBookUpdate(Ticker ticker, RawBookUpdate update) {
        String exchange = ticker.getExchange().getExchangeName();
        String symbol = ticker.getSymbol();
        long recv = RecordTimes.nowMicros();
        long evt = RecordTimes.micros(update.getTimestamp());
        String key = exchange + "|" + symbol;
        RawBookUpdate.Window window = update.getWindow();

        if (update.isSnapshot()) {
            // A fresh full window — open a new contiguous epoch (1 on the first snapshot).
            long epoch = epochByStream.merge(key, 1L, Long::sum);
            List<Level> bids = new ArrayList<>();
            List<Level> asks = new ArrayList<>();
            for (RawBookUpdate.Entry e : update.getEntries()) {
                (e.side() == RawBookUpdate.Side.BUY ? bids : asks).add(new Level(e.price(), e.size()));
            }
            // Best-first per the contract (bids high->low, asks low->high).
            bids.sort(Comparator.comparing(Level::price).reversed());
            asks.sort(Comparator.comparing(Level::price));
            recorder.recordBookSnapshot(new RecordedBookSnapshot(
                    exchange, symbol, recv, evt, null, epoch, true, bids, asks));
        } else {
            long epoch = epochByStream.getOrDefault(key, 0L);
            for (RawBookUpdate.Entry e : update.getEntries()) {
                boolean bid = e.side() == RawBookUpdate.Side.BUY;
                BookSide side = bid ? BookSide.BID : BookSide.ASK;
                BigDecimal start = window == null ? null : (bid ? window.bidStartPrice() : window.askStartPrice());
                BigDecimal end = window == null ? null : (bid ? window.bidEndPrice() : window.askEndPrice());
                recorder.recordBookEvent(new RecordedBookEvent(
                        exchange, symbol, recv, evt, null, null, epoch,
                        side, e.price(), e.size(), toAction(e.action()), start, end));
            }
        }
    }

    private static EventAction toAction(RawBookUpdate.Action action) {
        return switch (action) {
            case INSERT -> EventAction.ADD;
            case UPDATE -> EventAction.CHANGE;
            case DELETE -> EventAction.DELETE;
        };
    }
}
