package com.fueledbychai.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Ticker;

public class OrderBook implements IOrderBook {
    @Override
    public BidSizePair getBestBidWithSize() {
        BidSizePair pair = getBestBid();
        return pair;
    }

    @Override
    public BidSizePair getBestAskWithSize() {
        BidSizePair pair = getBestAsk();
        return pair;
    }

    protected static final Logger logger = LoggerFactory.getLogger(OrderBook.class);
    // Volatile references for lock-free reads with copy-on-write semantics
    protected volatile OrderBookSide buySide;
    protected volatile OrderBookSide sellSide;
    protected volatile boolean initialized = false;
    protected BigDecimal tickSize;
    protected volatile BigDecimal bestBid = BigDecimal.ZERO; // Best bid price
    protected volatile BigDecimal bestAsk = BigDecimal.ZERO; // Best ask price
    protected volatile Double bestBidSize = 0.0; // Best bid size
    protected volatile Double bestAskSize = 0.0; // Best ask size
    protected final List<OrderBookUpdateListener> orderbookUpdateListeners = new CopyOnWriteArrayList<>();
    protected Ticker ticker;
    protected double obiLambda = 0.75; // Default lambda for OBI calculation

    // Executor for asynchronous Level1Quote listener notifications
    protected volatile ExecutorService listenerExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "OrderBook-listener-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * Build a new order book for the specified ticker
     * 
     * @param ticker
     */
    public OrderBook(Ticker ticker) {
        this(ticker, ticker.getMinimumTickSize());

    }

    /**
     * Build a order book agreegated by the specified tickSize
     * 
     * @param ticker
     * @param tickSize
     */
    public OrderBook(Ticker ticker, BigDecimal tickSize) {
        this.ticker = ticker;
        this.buySide = new OrderBookSide(true); // true for descending order
        this.sellSide = new OrderBookSide(false); // false for ascending order
        this.tickSize = tickSize;
    }

    @Override
    public void clearOrderBook() {
        // Create new empty sides atomically
        OrderBookSide newBuySide = new OrderBookSide(true);
        OrderBookSide newSellSide = new OrderBookSide(false);

        // Atomic swap
        this.buySide = newBuySide;
        this.sellSide = newSellSide;
        this.bestBid = BigDecimal.ZERO;
        this.bestAsk = BigDecimal.ZERO;
        this.initialized = false;
    }

    /**
     * Shutdown the listener executor service and cleanup resources. This should be
     * called when the OrderBook is no longer needed to prevent resource leaks.
     */
    public void shutdown() {
        if (listenerExecutor != null && !listenerExecutor.isShutdown()) {
            listenerExecutor.shutdown();
            try {
                // Wait for existing tasks to complete
                if (!listenerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    // Force shutdown if tasks don't complete within timeout
                    listenerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                // Force shutdown if interrupted
                listenerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Atomically updates the order book from a complete snapshot. This method
     * builds the new order book state off to the side and then atomically swaps it
     * in, ensuring readers never see inconsistent state.
     * 
     * @param bids      List of bid entries (price, size pairs)
     * @param asks      List of ask entries (price, size pairs)
     * @param timestamp The timestamp for this update
     */
    @Override
    public synchronized void updateFromSnapshot(List<PriceLevel> bids, List<PriceLevel> asks, ZonedDateTime timestamp) {
        // Build new state off to the side
        OrderBookSide newBuySide = new OrderBookSide(true);
        OrderBookSide newSellSide = new OrderBookSide(false);

        // Populate the new sides with explicit NaN/null checks and warnings
        for (PriceLevel bid : bids) {
            if (bid.getSize() == null || Double.isNaN(bid.getSize())) {
                logger.warn("NaN or null bid size encountered in updateFromSnapshot for price {}. Skipping.", bid.getPrice());
                continue;
            }
            newBuySide.insertDirectly(bid.getPrice(), bid.getSize());
        }
        for (PriceLevel ask : asks) {
            if (ask.getSize() == null || Double.isNaN(ask.getSize())) {
                logger.warn("NaN or null ask size encountered in updateFromSnapshot for price {}. Skipping.", ask.getPrice());
                continue;
            }
            newSellSide.insertDirectly(ask.getPrice(), ask.getSize());
        }

        // Calculate new best prices
        BigDecimal newBestBid = newBuySide.getBestPrice(tickSize);
        BigDecimal newBestAsk = newSellSide.getBestPrice(tickSize);
        Double newBestBidSize = newBuySide.getSizeAtPrice(newBestBid);
        Double newBestAskSize = newSellSide.getSizeAtPrice(newBestAsk);

        // Store old values for change detection
        BigDecimal oldBestBid = this.bestBid;
        BigDecimal oldBestAsk = this.bestAsk;
        Double oldBestBidSize = this.bestBidSize;
        Double oldBestAskSize = this.bestAskSize;
        boolean wasInitialized = this.initialized;

        // Atomic swap of all state
        this.buySide = newBuySide;
        this.sellSide = newSellSide;
        this.bestBid = newBestBid;
        this.bestAsk = newBestAsk;
        this.initialized = true;

        // Notify listeners of changes after the atomic swap
        // Send notifications if there were actual changes or if this is the first
        // meaningful update
        if (wasInitialized) {
            // For already initialized order books, notify only on changes
            if (!newBestBid.equals(oldBestBid) || !newBestBidSize.equals(oldBestBidSize)) {
                notifyOrderBookUpdateListenersNewBid(newBestBid, newBestBidSize, timestamp);
            }
            if (!newBestAsk.equals(oldBestAsk) || !newBestAskSize.equals(oldBestAskSize)) {
                notifyOrderBookUpdateListenersNewAsk(newBestAsk, newBestAskSize, timestamp);
            }
        } else {
            // For first initialization, notify if we have meaningful prices (not zero)
            if (newBestBid.compareTo(BigDecimal.ZERO) > 0) {
                notifyOrderBookUpdateListenersNewBid(newBestBid, newBestBidSize, timestamp);
            }
            if (newBestAsk.compareTo(BigDecimal.ZERO) > 0) {
                notifyOrderBookUpdateListenersNewAsk(newBestAsk, newBestAskSize, timestamp);
            }
        }

        notifyOrderBookUpdateListenersNewOrderBookSnapshot(timestamp);
    }

    /**
     * Convenience method for updating from raw price/size arrays.
     * 
     * @param bidPrices Array of bid prices
     * @param bidSizes  Array of bid sizes (must be same length as bidPrices)
     * @param askPrices Array of ask prices
     * @param askSizes  Array of ask sizes (must be same length as askPrices)
     * @param timestamp The timestamp for this update
     */
    @Override
    public void updateFromSnapshot(BigDecimal[] bidPrices, Double[] bidSizes, BigDecimal[] askPrices, Double[] askSizes,
            ZonedDateTime timestamp) {
        List<PriceLevel> bids = new ArrayList<>();
        List<PriceLevel> asks = new ArrayList<>();

        for (int i = 0; i < bidPrices.length; i++) {
            Double size = bidSizes[i];
            if (size == null || Double.isNaN(size)) {
                logger.warn("NaN or null bid size encountered in updateFromSnapshot (array) for price {}. Skipping.", bidPrices[i]);
                continue;
            }
            bids.add(new PriceLevel(bidPrices[i], size));
        }

        for (int i = 0; i < askPrices.length; i++) {
            Double size = askSizes[i];
            if (size == null || Double.isNaN(size)) {
                logger.warn("NaN or null ask size encountered in updateFromSnapshot (array) for price {}. Skipping.", askPrices[i]);
                continue;
            }
            asks.add(new PriceLevel(askPrices[i], size));
        }

        updateFromSnapshot(bids, asks, timestamp);
    }

    /**
     * Simple class to represent a price/size level for snapshot updates
     */
    public static class PriceLevel {
        private final BigDecimal price;
        private final Double size;

        public PriceLevel(BigDecimal price, Double size) {
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

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public BidSizePair getBestBid() {
        return getBestBid(tickSize);
    }

    @Override
    public synchronized BidSizePair getBestBid(BigDecimal tickSize) {
        BigDecimal price = buySide.getBestPrice(tickSize);
        Double size = buySide.getSizeAtPrice(price);
        return new BidSizePair(price, size);
    }

    @Override
    public BidSizePair getBestAsk() {
        return getBestAsk(tickSize);
    }

    @Override
    public synchronized BidSizePair getBestAsk(BigDecimal tickSize) {
        BigDecimal price = sellSide.getBestPrice(tickSize);
        Double size = sellSide.getSizeAtPrice(price);
        return new BidSizePair(price, size);
    }

    @Override
    public BigDecimal getMidpoint() {
        return getMidpoint(tickSize);
    }

    @Override
    public synchronized BigDecimal getMidpoint(BigDecimal tickSize) {
        BidSizePair bestBidPair = getBestBid(tickSize);
        BidSizePair bestAskPair = getBestAsk(tickSize);
        BigDecimal bestBid = bestBidPair.price;
        BigDecimal bestAsk = bestAskPair.price;

        // If order book is empty (both bid and ask are 0), return 0
        if (bestBid.equals(BigDecimal.ZERO) && bestAsk.equals(BigDecimal.ZERO)) {
            return BigDecimal.ZERO;
        }

        return bestBid.add(bestAsk).divide(BigDecimal.valueOf(2));
    }

    /**
     * Returns a consistent snapshot of bid, ask, and midpoint values. This method
     * ensures all three values are from the same snapshot.
     */
    public synchronized BidAskMidpoint getBidAskMidpoint() {
        return getBidAskMidpoint(tickSize);
    }

    /**
     * Returns a consistent snapshot of bid, ask, and midpoint values with specified
     * tick size. This method ensures all three values are from the same snapshot.
     */
    public synchronized BidAskMidpoint getBidAskMidpoint(BigDecimal tickSize) {
        BidSizePair bidPair = getBestBid(tickSize);
        BidSizePair askPair = getBestAsk(tickSize);
        BigDecimal midpoint = getMidpoint(tickSize);
        return new BidAskMidpoint(bidPair.price, askPair.price, midpoint);
    }

    /**
     * Data class to hold bid, ask, and midpoint values from a consistent snapshot.
     */
    public static class BidAskMidpoint {
        public final BigDecimal bid;
        public final BigDecimal ask;
        public final BigDecimal midpoint;

        public BidAskMidpoint(BigDecimal bid, BigDecimal ask, BigDecimal midpoint) {
            this.bid = bid;
            this.ask = ask;
            this.midpoint = midpoint;
        }
    }

    @Override
    public double calculateWeightedOrderBookImbalance(double lambda) {
        return calculateWeightedOrderBookImbalance(lambda, tickSize);
    }

    @Override
    public double calculateWeightedOrderBookImbalance(double lambda, BigDecimal tickSize) {
        BigDecimal midpoint = getMidpoint(tickSize);
        if (midpoint == null) {
            logger.warn(
                    "OrderBook midpoint is null (no bids or asks). BuySide size: {}, SellSide size: {}. Returning 0 for OBI.",
                    buySide.orders.size(), sellSide.orders.size());
            logger.debug("BuySide keys: {}", buySide.orders.keySet());
            logger.debug("SellSide keys: {}", sellSide.orders.keySet());
        }
        double midPrice = midpoint.doubleValue();
        double totalWeightedBidVolume = buySide.calculateWeightedVolume(midPrice, lambda, tickSize);
        double totalWeightedAskVolume = sellSide.calculateWeightedVolume(midPrice, lambda, tickSize);

        // Avoid division by zero
        double totalWeightedVolume = totalWeightedBidVolume + totalWeightedAskVolume;
        if (totalWeightedVolume == 0) {
            logger.warn("Total weighted volume is zero. BidVol: {}, AskVol: {}", totalWeightedBidVolume,
                    totalWeightedAskVolume);
            return 0.0;
        }

        // Calculate order book imbalance
        return (totalWeightedBidVolume - totalWeightedAskVolume) / totalWeightedVolume * 100.0;
    }

    @Override
    public BigDecimal getCenterOfGravityMidpoint(int levels) {
        return getCenterOfGravityMidpoint(levels, tickSize);
    }

    /**
     * Returns the volume-weighted midpoint for the top N levels at the given tick
     * size.
     */
    @Override
    public BigDecimal getCenterOfGravityMidpoint(int levels, BigDecimal tickSize) {
        // Get top N bids
        Map<BigDecimal, Double> bidLevels = buySide.aggregateOrders(tickSize).entrySet().stream()
                .sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey())) // Descending
                .limit(levels).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Double::sum,
                        java.util.LinkedHashMap::new));

        // Get top N asks
        Map<BigDecimal, Double> askLevels = sellSide.aggregateOrders(tickSize).entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Ascending
                .limit(levels).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Double::sum,
                        java.util.LinkedHashMap::new));

        double bidVolume = bidLevels.values().stream().mapToDouble(Double::doubleValue).sum();
        double askVolume = askLevels.values().stream().mapToDouble(Double::doubleValue).sum();

        double bidWeightedPrice = bidLevels.entrySet().stream()
                .mapToDouble(e -> e.getKey().doubleValue() * e.getValue()).sum();
        double askWeightedPrice = askLevels.entrySet().stream()
                .mapToDouble(e -> e.getKey().doubleValue() * e.getValue()).sum();

        double totalVolume = bidVolume + askVolume;
        if (totalVolume == 0) {
            return null;
        }

        double vwmid = (bidWeightedPrice + askWeightedPrice) / totalVolume;
        return BigDecimal.valueOf(vwmid).setScale(tickSize.scale(), RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal getVWAPMidpoint(int levels) {
        return getVWAPMidpoint(levels, tickSize);
    }

    /**
     * Returns the volume-weighted midpoint for the top N levels at the given tick
     * size.
     */
    @Override
    public BigDecimal getVWAPMidpoint(int levels, BigDecimal tickSize) {
        // Get top N bids
        Map<BigDecimal, Double> bidLevels = buySide.aggregateOrders(tickSize).entrySet().stream()
                .sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey())) // Descending
                .limit(levels).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Double::sum,
                        java.util.LinkedHashMap::new));

        // Get top N asks
        Map<BigDecimal, Double> askLevels = sellSide.aggregateOrders(tickSize).entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Ascending
                .limit(levels).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Double::sum,
                        java.util.LinkedHashMap::new));

        double bidVolume = bidLevels.values().stream().mapToDouble(Double::doubleValue).sum();
        double askVolume = askLevels.values().stream().mapToDouble(Double::doubleValue).sum();

        double bidWeightedPrice = bidLevels.entrySet().stream()
                .mapToDouble(e -> e.getKey().doubleValue() * e.getValue()).sum();
        double askWeightedPrice = askLevels.entrySet().stream()
                .mapToDouble(e -> e.getKey().doubleValue() * e.getValue()).sum();

        double totalVolume = bidVolume + askVolume;
        if (totalVolume == 0) {
            return null;
        }

        double vwmid = (bidWeightedPrice / bidVolume + askWeightedPrice / askVolume) / 2.0;
        return BigDecimal.valueOf(vwmid).setScale(tickSize.scale(), RoundingMode.HALF_UP);
    }

    /**
     * VWAP midpoint where each side is integrated until 'targetUnits' is filled.
     */
    public synchronized BigDecimal getImpactVwapMidpoint(double targetUnits, DepthVwapParams p) {
        if (targetUnits <= 0)
            return getDepthVwapMidpoint(p);

        BidSizePair bidPair = getBestBid(tickSize);
        BidSizePair askPair = getBestAsk(tickSize);
        if (bidPair == null || askPair == null)
            return BigDecimal.ZERO;
        final double bestBid = bidPair.price.doubleValue();
        final double bestAsk = askPair.price.doubleValue();
        final double midTouch = (bestBid + bestAsk) * 0.5;
        final double bpPerTickNear = (tickSize.doubleValue() / midTouch) * 1e4;

        Map<BigDecimal, Double> bidsAgg = buySide.aggregateOrders(tickSize);
        Map<BigDecimal, Double> asksAgg = sellSide.aggregateOrders(tickSize);

        java.util.List<Map.Entry<BigDecimal, Double>> bids = bidsAgg.entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey())).collect(Collectors.toList());
        java.util.List<Map.Entry<BigDecimal, Double>> asks = asksAgg.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).collect(Collectors.toList());

        double vwapBid = vwapToFill(bids, /* isBid */true, bestBid, midTouch, bpPerTickNear, targetUnits, p.bpCap,
                p.tickCap, p.Lmax);
        double vwapAsk = vwapToFill(asks, /* isBid */false, bestAsk, midTouch, bpPerTickNear, targetUnits, p.bpCap,
                p.tickCap, p.Lmax);

        if (Double.isNaN(vwapBid))
            vwapBid = bestBid;
        if (Double.isNaN(vwapAsk))
            vwapAsk = bestAsk;

        double vwmid = 0.5 * (vwapBid + vwapAsk);
        return BigDecimal.valueOf(vwmid).setScale(tickSize.scale(), RoundingMode.HALF_UP);
    }

    public synchronized BigDecimal getDepthVwapMidpoint() {
        return getDepthVwapMidpoint(new DepthVwapParams());
    }

    /** Depth-first VWAP midpoint using slice×multiple coverage and bp/tick caps. */
    public synchronized BigDecimal getDepthVwapMidpoint(DepthVwapParams params) {
        // Safety: ensure we have a book
        BidSizePair bidPair = getBestBid(tickSize);
        BidSizePair askPair = getBestAsk(tickSize);
        if (bidPair == null || askPair == null)
            return BigDecimal.ZERO;
        BigDecimal bestBidPx = bidPair.price;
        BigDecimal bestAskPx = askPair.price;
        if (bestBidPx == null || bestAskPx == null || bestBidPx.signum() == 0 || bestAskPx.signum() == 0) {
            return BigDecimal.ZERO;
        }

        final double bestBid = bestBidPx.doubleValue();
        final double bestAsk = bestAskPx.doubleValue();
        final double midTouch = (bestBid + bestAsk) * 0.5;
        final double bpPerTickNear = (tickSize.doubleValue() / midTouch) * 1e4;
        final double bpCap = Math.max(0.0, params.bpCap);
        final int tickCap = Math.max(0, params.tickCap);
        final int Lmax = Math.max(1, params.Lmax);
        final double targetDepth = Math.max(0.0, params.MxSlice * params.slice);

        // Aggregate maps at current tick size and sort
        Map<BigDecimal, Double> bidsAgg = buySide.aggregateOrders(tickSize);
        Map<BigDecimal, Double> asksAgg = sellSide.aggregateOrders(tickSize);
        java.util.List<Map.Entry<BigDecimal, Double>> bids = bidsAgg.entrySet().stream()
                .sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey())) // best → worse
                .collect(Collectors.toList());
        java.util.List<Map.Entry<BigDecimal, Double>> asks = asksAgg.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // best → worse
                .collect(Collectors.toList());

        // Select depth per side
        SideAccum bidSel = selectDepthSide(bids, /* isBid */ true, bestBid, midTouch, bpPerTickNear, targetDepth, bpCap,
                tickCap, Lmax, params.lambdaDecay);
        SideAccum askSel = selectDepthSide(asks, /* isBid */ false, bestAsk, midTouch, bpPerTickNear, targetDepth,
                bpCap, tickCap, Lmax, params.lambdaDecay);

        // Fallbacks if a side selected nothing
        double vwapBid = (bidSel.sumWQ > 0.0) ? (bidSel.sumWP / bidSel.sumWQ) : bestBid;
        double vwapAsk = (askSel.sumWQ > 0.0) ? (askSel.sumWP / askSel.sumWQ) : bestAsk;

        double vwmid = 0.5 * (vwapBid + vwapAsk);
        return BigDecimal.valueOf(vwmid).setScale(tickSize.scale(), RoundingMode.HALF_UP);
    }

    @Override
    public void printTopLevels(int levels) {
        printTopLevels(levels, tickSize);
    }

    @Override
    public void printTopLevels(int levels, BigDecimal tickSize) {
        logger.info("Top " + levels + " levels of Buy Side:");
        buySide.printTopLevels(levels, tickSize);

        logger.info("Top " + levels + " levels of Sell Side:");
        sellSide.printTopLevels(levels, tickSize);
    }

    @Override
    public void addOrderBookUpdateListener(OrderBookUpdateListener listener) {
        Exception exception = new Exception();
        logger.debug("### Exception for debugging listener addition", exception);
        orderbookUpdateListeners.add(listener);
    }

    @Override
    public void removeOrderBookUpdateListener(OrderBookUpdateListener listener) {
        orderbookUpdateListeners.remove(listener);
    }

    protected void notifyOrderBookUpdateListenersNewBid(BigDecimal bestBid, Double size, ZonedDateTime timestamp) {
        if (!initialized) {
            // Don't notify listeners of best bid updates until after the initial snapshot
            return;
        }
        // CopyOnWriteArrayList provides thread-safe iteration without
        // ConcurrentModificationException
        // The iteration is performed on a snapshot of the list at the time the iterator
        // was created
        for (OrderBookUpdateListener listener : orderbookUpdateListeners) {
            listenerExecutor.submit(() -> {
                try {
                    listener.bestBidUpdated(ticker, bestBid, size, timestamp);
                } catch (Throwable t) {
                    logger.error("Listener threw exception for {}: {}", ticker, t.getMessage(), t);
                }
            });
        }
    }

    protected void notifyOrderBookUpdateListenersNewAsk(BigDecimal bestAsk, Double size, ZonedDateTime timestamp) {
        if (!initialized) {
            // Don't notify listeners of best ask updates until after the initial snapshot
            return;
        }
        // CopyOnWriteArrayList provides thread-safe iteration without
        // ConcurrentModificationException
        // The iteration is performed on a snapshot of the list at the time the iterator
        // was created
        for (OrderBookUpdateListener listener : orderbookUpdateListeners) {
            listenerExecutor.submit(() -> {
                try {
                    listener.bestAskUpdated(ticker, bestAsk, size, timestamp);
                } catch (Throwable t) {
                    logger.error("Listener threw exception for {}: {}", ticker, t.getMessage(), t);
                }
            });
        }
    }

    protected void notifyOrderBookUpdateListenersImbalance(BigDecimal imbalance, ZonedDateTime timestamp) {
        // CopyOnWriteArrayList provides thread-safe iteration without
        // ConcurrentModificationException
        // The iteration is performed on a snapshot of the list at the time the iterator
        // was created
        for (OrderBookUpdateListener listener : orderbookUpdateListeners) {
            listenerExecutor.submit(() -> {
                try {
                    listener.orderBookImbalanceUpdated(ticker, imbalance, timestamp);
                } catch (Throwable t) {
                    logger.error("Listener threw exception for {}: {}", ticker, t.getMessage(), t);
                }
            });
        }
    }

    /**
     * Notifies listeners with a snapshot (clone) of the current order book state.
     * This ensures listeners receive a consistent, immutable view for each event.
     */
    protected void notifyOrderBookUpdateListenersNewOrderBookSnapshot(ZonedDateTime timestamp) {
        IOrderBook snapshot = this.cloneOrderBook();
        logger.debug("Notifying of new order book snapshot, bestBid: {}, bestAsk: {}", snapshot.getBestBid().price,
                snapshot.getBestAsk().price);
        for (OrderBookUpdateListener listener : orderbookUpdateListeners) {
            listenerExecutor.submit(() -> {
                try {
                    logger.debug("Sending order book snapshot to listener: {}", listener);
                    listener.orderBookUpdated(ticker, snapshot, timestamp);
                } catch (Throwable t) {
                    logger.error("Listener threw exception for {}: {}", ticker, t.getMessage(), t);
                }
            });
        }
    }

    /**
     * Creates a deep clone of the current OrderBook for safe event notification.
     * Only public API state is copied; listeners and executors are not cloned.
     */
    public synchronized IOrderBook cloneOrderBook() {
        OrderBook clone = new OrderBook(this.ticker, this.tickSize);
        // Clone buy side
        for (Map.Entry<BigDecimal, Double> entry : this.buySide.orders.entrySet()) {
            clone.buySide.insertDirectly(entry.getKey(), entry.getValue());
        }
        // Clone sell side
        for (Map.Entry<BigDecimal, Double> entry : this.sellSide.orders.entrySet()) {
            clone.sellSide.insertDirectly(entry.getKey(), entry.getValue());
        }
        clone.bestBid = this.bestBid;
        clone.bestAsk = this.bestAsk;
        clone.bestBidSize = this.bestBidSize;
        clone.bestAskSize = this.bestAskSize;
        clone.initialized = this.initialized;
        return clone;
    }

    @Override
    public String toString() {
        return "OrderBook [buySide=" + buySide + ", sellSide=" + sellSide + ", ticker=" + ticker + "]";
    }

    protected class OrderBookSide {
        public BigDecimal getBestPrice(BigDecimal tickSize) {
            Map<BigDecimal, Double> aggregatedOrders = aggregateOrders(tickSize);
            return aggregatedOrders.keySet().stream().sorted(descending ? (price1, price2) -> price2.compareTo(price1)
                    : (price1, price2) -> price1.compareTo(price2)).findFirst().orElse(BigDecimal.ZERO);
        }

        public Double getSizeAtPrice(BigDecimal price) {
            // Aggregate orders by tick size and return the size for the given price
            BigDecimal tickSize = OrderBook.this.tickSize;
            Map<BigDecimal, Double> aggregatedOrders = aggregateOrders(tickSize);
            return aggregatedOrders.getOrDefault(price, 0.0);
        }

        private final ConcurrentHashMap<BigDecimal, Double> orders;
        private final boolean descending;

        OrderBookSide(boolean descending) {
            this.orders = new ConcurrentHashMap<>();
            this.descending = descending;
        }

        public void insert(BigDecimal price, Double size, ZonedDateTime timestamp) {
            if (size == null || Double.isNaN(size)) {
                logger.warn("Attempted to insert NaN or null size for price {}. Ignoring order.", price);
                return;
            }
            orders.put(price, size);
            updateBestPriceAndSize(timestamp);
        }

        /**
         * Insert an order directly without triggering best price updates. Used for bulk
         * operations like snapshot loading.
         */
        public void insertDirectly(BigDecimal price, Double size) {
            if (size == null || Double.isNaN(size)) {
                logger.warn("Attempted to insert NaN or null size for price {} (insertDirectly). Ignoring order.", price);
                return;
            }
            orders.put(price, size);
        }

        public void update(BigDecimal price, Double size, ZonedDateTime timestamp) {
            if (size == null || Double.isNaN(size)) {
                logger.warn("Attempted to update with NaN or null size for price {}. Ignoring update.", price);
                return;
            }
            orders.put(price, size);
            updateBestPriceAndSize(timestamp);
        }

        public void remove(BigDecimal price, ZonedDateTime timestamp) {
            orders.remove(price);
            updateBestPriceAndSize(timestamp);
        }

        public void clear() {
            orders.clear();
        }

        public void printTopLevels(int levels, BigDecimal tickSize) {
            Map<BigDecimal, Double> aggregatedOrders = aggregateOrders(tickSize);
            List<Map.Entry<BigDecimal, Double>> sortedOrders = aggregatedOrders.entrySet().stream()
                    .sorted((entry1, entry2) -> descending ? entry2.getKey().compareTo(entry1.getKey())
                            : entry1.getKey().compareTo(entry2.getKey()))
                    .limit(levels).collect(Collectors.toList());

            for (Map.Entry<BigDecimal, Double> entry : sortedOrders) {
                logger.info("Price: " + entry.getKey() + ", Size: " + entry.getValue());
            }
        }

        public BigDecimal[] getBestPriceAndSize(BigDecimal tickSize) {
            Map<BigDecimal, Double> aggregatedOrders = aggregateOrders(tickSize);
            BigDecimal bestPrice = aggregatedOrders.keySet().stream()
                    .sorted(descending ? (price1, price2) -> price2.compareTo(price1)
                            : (price1, price2) -> price1.compareTo(price2))
                    .findFirst().orElse(BigDecimal.ZERO);
            Double bestSize = aggregatedOrders.getOrDefault(bestPrice, 0.0);
            return new BigDecimal[] { bestPrice, BigDecimal.valueOf(bestSize) };
        }

        public double calculateWeightedVolume(double midPrice, double lambda, BigDecimal tickSize) {
            Map<BigDecimal, Double> aggregatedOrders = aggregateOrders(tickSize);
            return aggregatedOrders.entrySet().stream().mapToDouble(entry -> {
                double price = entry.getKey().doubleValue();
                double size = entry.getValue();
                double distance = descending ? midPrice - price : price - midPrice;
                double weight = Math.exp(-lambda * distance);
                return weight * size;
            }).sum();
        }

        public Map<BigDecimal, Double> aggregateOrders(BigDecimal tickSize) {
            int scale = tickSize.scale();
            return orders.entrySet().stream().collect(Collectors.toMap(entry -> {
                if (descending) {
                    // For bids, round down
                    return entry.getKey().divide(tickSize).setScale(scale, RoundingMode.DOWN).multiply(tickSize)
                            .setScale(scale, RoundingMode.DOWN);
                } else {
                    // For asks, round up
                    return entry.getKey().divide(tickSize).setScale(scale, RoundingMode.UP).multiply(tickSize)
                            .setScale(scale, RoundingMode.UP);
                }
            }, Map.Entry::getValue, Double::sum, ConcurrentHashMap::new));
        }

        protected void updateBestPriceAndSize(ZonedDateTime timestamp) {
            BigDecimal bestPrice = orders.keySet().stream()
                    .sorted(descending ? (price1, price2) -> price2.compareTo(price1)
                            : (price1, price2) -> price1.compareTo(price2))
                    .findFirst().orElse(BigDecimal.ZERO);
            Double bestSize = orders.getOrDefault(bestPrice, 0.0);

            if (descending) {
                // Update best bid
                synchronized (OrderBook.this) {
                    if (!bestPrice.equals(bestBid)) {
                        logger.debug("Best Bid updated from {} to {}", bestBid, bestPrice);
                        notifyOrderBookUpdateListenersNewBid(bestPrice, bestSize, timestamp);
                    }
                    bestBid = bestPrice;
                    bestBidSize = bestSize;
                }
            } else {
                // Update best ask
                synchronized (OrderBook.this) {
                    if (!bestPrice.equals(bestAsk)) {
                        logger.debug("Best Ask updated from {} to {}", bestAsk, bestPrice);
                        notifyOrderBookUpdateListenersNewAsk(bestPrice, bestSize, timestamp);
                    }
                    bestAsk = bestPrice;
                    bestAskSize = bestSize;
                }
            }
        }

        @Override
        public String toString() {
            // Sort entries by price: ascending for buy side, descending for sell side
            List<Map.Entry<BigDecimal, Double>> sortedEntries = orders.entrySet().stream()
                    .sorted(descending ? (e1, e2) -> e2.getKey().compareTo(e1.getKey())
                            : (e1, e2) -> e1.getKey().compareTo(e2.getKey()))
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("OrderBookSide [");
            for (Map.Entry<BigDecimal, Double> entry : sortedEntries) {
                sb.append("{Price: ").append(entry.getKey()).append(", Size: ").append(entry.getValue()).append("}, ");
            }
            if (!sortedEntries.isEmpty()) {
                sb.setLength(sb.length() - 2); // Remove trailing comma and space
            }
            sb.append("]");
            return sb.toString();
        }

    }

    public static final class DepthVwapParams {
        /** cumulative depth multiple of your per-level slice (e.g., 3–8) */
        public double MxSlice = 5.0;
        /** your per-level quote size (units) */
        public double slice = 1.0;
        /** hard cap on levels inspected per side */
        public int Lmax = 10;
        /** max distance from touch to include (basis points) */
        public double bpCap = 15.0;
        /** max ticks from touch to include */
        public int tickCap = 6;
        /** level decay: 0 = none; else exp(-lambda*i) */
        public double lambdaDecay = 0.0;
    }

    private static final class SideAccum {
        double sumWP = 0.0; // Σ (w * price * qty)
        double sumWQ = 0.0; // Σ (w * qty)
        double cumQ = 0.0; // cumulative qty (unweighted)
    }

    private SideAccum selectDepthSide(java.util.List<Map.Entry<BigDecimal, Double>> levels, boolean isBid,
            double bestPx, double midTouch, double bpPerTickNear, double targetDepth, double bpCap, int tickCap,
            int Lmax, double lambdaDecay) {
        SideAccum a = new SideAccum();
        int used = 0;
        for (Map.Entry<BigDecimal, Double> e : levels) {
            if (used >= Lmax)
                break;
            double px = e.getKey().doubleValue();
            double qty = e.getValue();

            double ticksFromTouch = Math.abs((px - bestPx) / (tickSize.doubleValue()));
            if (ticksFromTouch > tickCap)
                break;
            double bpFromTouch = Math.abs(px - bestPx) / midTouch * 1e4;
            if (bpFromTouch > bpCap)
                break;

            double w = (lambdaDecay <= 0.0) ? 1.0 : Math.exp(-lambdaDecay * used);
            a.sumWP += w * px * qty;
            a.sumWQ += w * qty;
            a.cumQ += qty;
            used++;

            if (a.cumQ >= targetDepth)
                break;
        }
        return a;
    }

    private double vwapToFill(java.util.List<Map.Entry<BigDecimal, Double>> levels, boolean isBid, double bestPx,
            double midTouch, double bpPerTickNear, double targetUnits, double bpCap, int tickCap, int Lmax) {
        double rem = targetUnits;
        double sumPQ = 0.0;
        double sumQ = 0.0;
        int used = 0;

        for (Map.Entry<BigDecimal, Double> e : levels) {
            if (used >= Lmax || rem <= 0.0)
                break;
            double px = e.getKey().doubleValue();
            double qtyAvail = e.getValue();

            double ticksFromTouch = Math.abs((px - bestPx) / (tickSize.doubleValue()));
            if (ticksFromTouch > tickCap)
                break;
            double bpFromTouch = Math.abs(px - bestPx) / midTouch * 1e4;
            if (bpFromTouch > bpCap)
                break;

            double take = Math.min(rem, qtyAvail);
            sumPQ += px * take;
            sumQ += take;
            rem -= take;
            used++;
        }
        return (sumQ > 0.0) ? (sumPQ / sumQ) : Double.NaN;
    }
}
