package com.fueledbychai.hibachi.common.api.ws.market;

/**
 * Listener interfaces for Hibachi market WebSocket topics.
 */
public final class HibachiMarketListeners {

    private HibachiMarketListeners() {
    }

    @FunctionalInterface
    public interface MarkPriceListener {
        void onMarkPrice(HibachiMarkPriceUpdate update);
    }

    @FunctionalInterface
    public interface AskBidListener {
        void onAskBid(HibachiAskBidUpdate update);
    }

    @FunctionalInterface
    public interface OrderBookListener {
        void onOrderBook(HibachiOrderBookUpdate update);
    }

    @FunctionalInterface
    public interface TradeListener {
        void onTrade(HibachiTradeUpdate update);
    }
}
