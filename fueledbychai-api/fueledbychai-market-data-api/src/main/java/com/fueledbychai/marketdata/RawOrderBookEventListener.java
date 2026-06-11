package com.fueledbychai.marketdata;

import com.fueledbychai.data.Ticker;

/**
 * Receives raw, un-throttled order-book updates ({@link RawBookUpdate}) from delta-native
 * venues &mdash; the per-event, sequence-bearing stream, as opposed to the merged periodic
 * snapshots delivered by {@link OrderBookUpdateListener}.
 *
 * <p>Register via {@link IOrderBook#addRawOrderBookEventListener(RawOrderBookEventListener)}.
 * Only venues whose exchange feed is genuinely incremental fire this; snapshot-native venues
 * never do, so a recorder should fall back to {@link OrderBookUpdateListener} there.
 */
public interface RawOrderBookEventListener {

    /**
     * Invoked once per raw feed message (snapshot or delta), on the feed thread, before any
     * throttling. Implementations must be fast and non-blocking.
     *
     * @param ticker the instrument
     * @param update the raw update (full-book anchor or incremental delta) with its sequence
     */
    void onBookUpdate(Ticker ticker, RawBookUpdate update);
}
