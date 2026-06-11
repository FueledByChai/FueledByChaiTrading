package com.fueledbychai.marketdata;

import com.fueledbychai.data.Ticker;

/**
 * Capability mixin for {@link QuoteEngine}s that can stream the raw, per-frame order-book
 * event feed (sequenced deltas or windowed frames) to a {@link RawOrderBookEventListener}.
 *
 * <p>Not every venue exposes this, so callers should check {@code instanceof
 * RawOrderBookSubscribable} before subscribing. Implemented by the Binance (futures/spot),
 * OKX, Hibachi, and Paradex engines.
 */
public interface RawOrderBookSubscribable {

    /**
     * Begin delivering raw order-book frames for {@code ticker} to {@code listener}, starting
     * the underlying book feed if it is not already running.
     */
    void subscribeRawOrderBook(Ticker ticker, RawOrderBookEventListener listener);
}
