package com.fueledbychai.marketdata;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import com.fueledbychai.marketdata.OrderBook.DepthVwapParams;
import com.fueledbychai.marketdata.OrderBook.PriceLevel;

public interface IOrderBook {
    /**
     * Simple pair class for price and size.
     */
    public static class BidSizePair {
        public final BigDecimal price;
        public final Double size;

        public BidSizePair(BigDecimal price, Double size) {
            this.price = price;
            this.size = size;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public Double getSize() {
            return size;
        }
    }

    /**
     * Returns both the best bid price and its size as a pair.
     */
    BidSizePair getBestBidWithSize();

    /**
     * Returns both the best ask price and its size as a pair.
     */
    BidSizePair getBestAskWithSize();

    void clearOrderBook();

    boolean isInitialized();

    BidSizePair getBestBid();

    BidSizePair getBestBid(BigDecimal tickSize);

    BidSizePair getBestAsk();

    BidSizePair getBestAsk(BigDecimal tickSize);

    /**
     * Top {@code depth} bid levels, best (highest price) first. Lets callers see
     * past the touch — e.g. to compute the market spread with their own resting
     * orders removed. The default implementation returns only the BBO so existing
     * {@link IOrderBook} implementors (mocks/tests) keep compiling; the real
     * {@code OrderBook} overrides it with true depth.
     */
    default List<BidSizePair> getBids(int depth) {
        BidSizePair b = getBestBid();
        return (b == null || b.price == null) ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(b);
    }

    /**
     * Top {@code depth} ask levels, best (lowest price) first. See {@link #getBids(int)}.
     */
    default List<BidSizePair> getAsks(int depth) {
        BidSizePair a = getBestAsk();
        return (a == null || a.price == null) ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(a);
    }

    BigDecimal getMidpoint();

    BigDecimal getMidpoint(BigDecimal tickSize);

    double calculateWeightedOrderBookImbalance(double lambda);

    double calculateWeightedOrderBookImbalance(double lambda, BigDecimal tickSize);

    BigDecimal getCenterOfGravityMidpoint(int levels);

    /**
     * Returns the volume-weighted midpoint for the top N levels at the given tick
     * size.
     */
    BigDecimal getCenterOfGravityMidpoint(int levels, BigDecimal tickSize);

    BigDecimal getVWAPMidpoint(int levels);

    /**
     * Returns the volume-weighted midpoint for the top N levels at the given tick
     * size.
     */
    BigDecimal getVWAPMidpoint(int levels, BigDecimal tickSize);

    void printTopLevels(int levels);

    void printTopLevels(int levels, BigDecimal tickSize);

    void addOrderBookUpdateListener(OrderBookUpdateListener listener);

    void removeOrderBookUpdateListener(OrderBookUpdateListener listener);

    /**
     * Registers a listener for raw, un-throttled, sequence-bearing book updates
     * ({@link RawBookUpdate}). Only delta-native venue implementations fire these; the
     * default is a no-op so snapshot-native implementations and mocks keep compiling.
     */
    default void addRawOrderBookEventListener(RawOrderBookEventListener listener) {
    }

    /** Removes a raw order-book event listener. Default no-op. */
    default void removeRawOrderBookEventListener(RawOrderBookEventListener listener) {
    }

    /**
     * Atomically updates the order book from a complete snapshot. This method
     * ensures readers never see inconsistent state during updates.
     * 
     * @param bids      List of bid entries (price, size pairs)
     * @param asks      List of ask entries (price, size pairs)
     * @param timestamp The timestamp for this update
     */
    void updateFromSnapshot(List<PriceLevel> bids, List<PriceLevel> asks, ZonedDateTime timestamp);

    /**
     * Convenience method for updating from raw price/size arrays.
     * 
     * @param bidPrices Array of bid prices
     * @param bidSizes  Array of bid sizes (must be same length as bidPrices)
     * @param askPrices Array of ask prices
     * @param askSizes  Array of ask sizes (must be same length as askPrices)
     * @param timestamp The timestamp for this update
     */
    void updateFromSnapshot(BigDecimal[] bidPrices, Double[] bidSizes, BigDecimal[] askPrices, Double[] askSizes,
            ZonedDateTime timestamp);

    BigDecimal getImpactVwapMidpoint(double targetUnits, DepthVwapParams p);

    BigDecimal getDepthVwapMidpoint(DepthVwapParams params);

}