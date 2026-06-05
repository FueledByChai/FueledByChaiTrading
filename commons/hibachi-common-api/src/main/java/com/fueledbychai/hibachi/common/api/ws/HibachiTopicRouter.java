package com.fueledbychai.hibachi.common.api.ws;

/**
 * Hibachi market WebSocket topic identifiers.
 *
 * <p>Wire values match the Python SDK at {@code hibachi_xyz/types.py:163-172}.
 */
public final class HibachiTopicRouter {

    public static final String TOPIC_MARK_PRICE = "mark_price";
    public static final String TOPIC_SPOT_PRICE = "spot_price";
    public static final String TOPIC_FUNDING_RATE_ESTIMATION = "funding_rate_estimation";
    public static final String TOPIC_TRADES = "trades";
    public static final String TOPIC_KLINES = "klines";
    public static final String TOPIC_ORDERBOOK = "orderbook";
    public static final String TOPIC_ASK_BID_PRICE = "ask_bid_price";
    /**
     * MM-partner-only L2 channel: 10 levels delivered at ~5ms (vs the
     * ~250-300ms {@link #TOPIC_ORDERBOOK} heartbeat). Same message schema as
     * {@code orderbook}, so it routes through the identical book handler.
     * Selected via {@code hibachi.market.data.live.book=true}.
     */
    public static final String TOPIC_LIVE_BOOK = "live_book";

    /** Topics that compose the framework's Level1 snapshot for Hibachi. */
    public static final String[] LEVEL1_TOPICS = {
            TOPIC_ASK_BID_PRICE, TOPIC_MARK_PRICE,
            TOPIC_FUNDING_RATE_ESTIMATION, TOPIC_TRADES
    };

    /** Topic that drives the framework's Level2 (order book) feed. */
    public static final String LEVEL2_TOPIC = TOPIC_ORDERBOOK;

    /** Topic that drives the framework's order-flow (time and sales) feed. */
    public static final String ORDER_FLOW_TOPIC = TOPIC_TRADES;

    private HibachiTopicRouter() {
    }
}
