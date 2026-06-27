/**
 * MIT License
 *
 * Copyright (c) 2015  FueledByChai Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package com.fueledbychai.marketdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.Ticker;

/**
 * @author FueledByChai Contributors
 *
 * 
 */
public abstract class QuoteEngine implements IQuoteEngine {

    protected static final Logger logger = LoggerFactory.getLogger(QuoteEngine.class);

    // Thread pool for handling quote notifications
    private final ThreadPoolExecutor quoteExecutor;
    // Upper bound on the dispatch backlog. The pool previously used an UNBOUNDED
    // queue (Executors.newFixedThreadPool), so when dispatch fell behind — or
    // duplicate subscriptions multiplied the submit rate across WS reconnects —
    // the queue grew without bound and exhausted the heap (OOM, 2026-06-25:
    // ~1.7M queued tasks). Market data is latest-wins, so we cap the queue and
    // drop the stalest tasks under saturation.
    private static final int QUOTE_QUEUE_CAPACITY = 8192;
    private final AtomicLong droppedQuoteTasks = new AtomicLong();
    private volatile long lastDropLogMs = 0L;

    // --- dispatch instrumentation: diagnose queue saturation root cause ---
    // Distinguishes the three failure modes the saturation WARN lumps together:
    //   (a) sheer volume    -> high fires/s, listener counts STABLE
    //   (b) slow listener   -> moderate fires/s but queued/active climbing, drops rising
    //   (c) duplicate subs   -> listener counts GROW over time (the WS-reconnect amplifier)
    // Logged every 30s at INFO so a few minutes of logs tells us which fix matters.
    private final AtomicLong l1Fires = new AtomicLong();
    private final AtomicLong l2Fires = new AtomicLong();
    private final AtomicLong orderFlowFires = new AtomicLong();
    private long lastStatsMs = 0L;
    private long lastL1Fires = 0L;
    private long lastL2Fires = 0L;
    private long lastOfFires = 0L;
    private long lastDroppedStat = 0L;

    private static final Map<Class<? extends QuoteEngine>, QuoteEngine> instances = new ConcurrentHashMap<>();
    private static final Map<Exchange, Class<? extends QuoteEngine>> registry = new ConcurrentHashMap<>();
    private static volatile boolean providersLoaded = false;
    ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();

    // Custom ThreadFactory for naming quote processing threads
    private static class QuoteThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "quote-engine-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    public static synchronized QuoteEngine getInstance(Class<? extends QuoteEngine> clazz) {
        return instances.computeIfAbsent(clazz, c -> {
            try {
                return c.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate QuoteEngine: " + c.getName(), e);
            }
        });
    }

    public static void registerQuoteEngine(Exchange exchange, Class<? extends QuoteEngine> clazz) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("QuoteEngine class is required");
        }
        Class<? extends QuoteEngine> existing = registry.putIfAbsent(exchange, clazz);
        if (existing != null && !existing.equals(clazz)) {
            throw new IllegalStateException(
                    "QuoteEngine already registered for " + exchange.getExchangeName() + ": " + existing.getName());
        }
    }

    private static void ensureProvidersLoaded() {
        if (providersLoaded) {
            return;
        }
        synchronized (QuoteEngine.class) {
            if (providersLoaded) {
                return;
            }
            ServiceLoader<QuoteEngineProvider> loader = ServiceLoader.load(QuoteEngineProvider.class);
            for (QuoteEngineProvider provider : loader) {
                try {
                    registerQuoteEngine(provider.getExchange(), provider.getQuoteEngineClass());
                } catch (RuntimeException e) {
                    logger.warn("Failed to register QuoteEngine provider: {}", provider.getClass().getName(), e);
                }
            }
            providersLoaded = true;
        }
    }

    public static boolean isRegistered(Exchange exchange) {
        if (exchange == null) {
            return false;
        }
        ensureProvidersLoaded();
        return registry.containsKey(exchange);
    }

    public static QuoteEngine getInstance(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange is required");
        }
        ensureProvidersLoaded();
        Class<? extends QuoteEngine> clazz = registry.get(exchange);
        if (clazz == null) {
            throw new IllegalStateException("No QuoteEngine registered for exchange " + exchange.getExchangeName());
        }
        return getInstance(clazz);
    }

    protected List<ErrorListener> errorListeners;
    protected Map<Ticker, List<Level1QuoteListener>> level1ListenerMap = Collections
            .synchronizedMap(new HashMap<Ticker, List<Level1QuoteListener>>());

    // List of global Level1 listeners that receive all Level1 quotes
    protected List<Level1QuoteListener> globalLevel1ListenerList = Collections
            .synchronizedList(new ArrayList<Level1QuoteListener>());

    protected Map<Ticker, List<Level2QuoteListener>> level2ListenerMap = Collections
            .synchronizedMap(new HashMap<Ticker, List<Level2QuoteListener>>());
    protected Map<Ticker, List<OrderFlowListener>> orderFlowListenerMap = Collections
            .synchronizedMap(new HashMap<Ticker, List<OrderFlowListener>>());

    protected List<OrderFlowListener> globalOrderFlowListenerList = Collections
            .synchronizedList(new ArrayList<OrderFlowListener>());

    public QuoteEngine() {
        this(500); // Default to 500 threads
    }

    /**
     * Constructor that allows specifying the number of threads for quote processing
     * 
     * @param threadPoolSize The number of threads to use for processing quotes
     */
    public QuoteEngine(int threadPoolSize) {
        errorListeners = new ArrayList<ErrorListener>();
        // Bounded dispatch queue with a drop-OLDEST policy. A quote stuck behind
        // thousands of newer quotes is stale and worthless, so under saturation we
        // discard the stalest queued task to make room for the newest. This caps
        // memory regardless of upstream submit rate (the unbounded predecessor
        // OOM'd). allowCoreThreadTimeOut lets the (large) pool shrink back when
        // idle instead of holding hundreds of threads forever.
        quoteExecutor = new ThreadPoolExecutor(
                threadPoolSize, threadPoolSize,
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(QUOTE_QUEUE_CAPACITY),
                new QuoteThreadFactory(),
                (r, exec) -> {
                    // Saturated: drop the oldest queued (stalest) task, enqueue newest.
                    // Count + throttle-log so the cap is never silent.
                    exec.getQueue().poll();
                    long total = droppedQuoteTasks.incrementAndGet();
                    long now = System.currentTimeMillis();
                    if (now - lastDropLogMs > 5000L) {
                        lastDropLogMs = now;
                        logger.warn("QuoteEngine dispatch queue saturated (capacity={}, threads={}); "
                                + "dropping stalest quote tasks to cap memory (total dropped={}). "
                                + "Likely a slow listener or duplicate subscriptions.",
                                QUOTE_QUEUE_CAPACITY, threadPoolSize, total);
                    }
                    if (!exec.isShutdown()) {
                        exec.getQueue().offer(r);
                    }
                });
        quoteExecutor.allowCoreThreadTimeOut(true);
        logger.info("Initialized QuoteEngine with {} threads, bounded dispatch queue (capacity={})",
                threadPoolSize, QUOTE_QUEUE_CAPACITY);
        monitor.scheduleAtFixedRate(() -> {
            logger.debug("Quote Engine pool={}, active={}, queued={}, dropped={}",
                    quoteExecutor.getPoolSize(), quoteExecutor.getActiveCount(),
                    quoteExecutor.getQueue().size(), droppedQuoteTasks.get());
        }, 0, 1, TimeUnit.SECONDS);
        // Per-stream-type dispatch stats (volume vs slow-listener vs duplicate-subs).
        monitor.scheduleAtFixedRate(this::logDispatchStats, 30, 30, TimeUnit.SECONDS);
    }

    /** Count listeners across a per-ticker listener map (the duplicate-subscription tell). */
    private static int totalListeners(Map<Ticker, ? extends List<?>> map) {
        int n = 0;
        synchronized (map) {
            for (List<?> listeners : map.values()) {
                if (listeners != null) {
                    n += listeners.size();
                }
            }
        }
        return n;
    }

    /**
     * Periodic INFO summary to root-cause dispatch saturation. Watch over a few minutes:
     * listener counts climbing = duplicate subscriptions (WS-reconnect amplifier); stable
     * counts with high fires/s = volume; rising queued/dropped with modest fires/s = slow listener.
     */
    private void logDispatchStats() {
        long now = System.currentTimeMillis();
        long f1 = l1Fires.get();
        long f2 = l2Fires.get();
        long fof = orderFlowFires.get();
        long drp = droppedQuoteTasks.get();
        double secs = (lastStatsMs == 0L) ? 30.0 : Math.max(0.001, (now - lastStatsMs) / 1000.0);
        int l1Listeners = (globalLevel1ListenerList == null ? 0 : globalLevel1ListenerList.size())
                + totalListeners(level1ListenerMap);
        int l2Listeners = totalListeners(level2ListenerMap);
        int ofListeners = (globalOrderFlowListenerList == null ? 0 : globalOrderFlowListenerList.size())
                + totalListeners(orderFlowListenerMap);
        logger.info("QuoteEngine dispatch stats: L1 {}/s (listeners={}), L2 {}/s (listeners={}), "
                + "OrderFlow {}/s (listeners={}), dropped +{} (total={}), queued={}, active={}, pool={}",
                Math.round((f1 - lastL1Fires) / secs), l1Listeners,
                Math.round((f2 - lastL2Fires) / secs), l2Listeners,
                Math.round((fof - lastOfFires) / secs), ofListeners,
                (drp - lastDroppedStat), drp,
                quoteExecutor.getQueue().size(), quoteExecutor.getActiveCount(), quoteExecutor.getPoolSize());
        lastStatsMs = now;
        lastL1Fires = f1;
        lastL2Fires = f2;
        lastOfFires = fof;
        lastDroppedStat = drp;
    }

    public void addErrorListener(ErrorListener listener) {
        synchronized (errorListeners) {
            errorListeners.add(listener);
        }
    }

    public void removeErrorListener(ErrorListener listener) {
        synchronized (errorListeners) {
            errorListeners.remove(listener);
        }
    }

    public void fireErrorEvent(QuoteError error) {
        synchronized (errorListeners) {
            for (int i = 0; i < errorListeners.size(); i++) {
                ((ErrorListener) errorListeners.get(i)).quoteEngineError(error);
            }
        }
    }

    @Override
    public void subscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        synchronized (level1ListenerMap) {
            List<Level1QuoteListener> listeners = level1ListenerMap.get(ticker);
            if (listeners == null) {
                listeners = Collections.synchronizedList(new ArrayList<Level1QuoteListener>());
                level1ListenerMap.put(ticker, listeners);
            }
            synchronized (listeners) {
                listeners.add(listener);
            }
        }
    }

    @Override
    public void subscribeGlobalLevel1(Level1QuoteListener listener) {
        synchronized (globalLevel1ListenerList) {
            globalLevel1ListenerList.add(listener);
        }
    }

    @Override
    public void unsubscribeGlobalLevel1(Level1QuoteListener listener) {
        synchronized (globalLevel1ListenerList) {
            globalLevel1ListenerList.remove(listener);
        }
    }

    @Override
    public void unsubscribeLevel1(Ticker ticker, Level1QuoteListener listener) {
        synchronized (level1ListenerMap) {
            List<Level1QuoteListener> listeners = level1ListenerMap.get(ticker);
            if (listeners != null) {
                synchronized (listeners) {
                    listeners.remove(listener);
                }
            }
        }
    }

    @Override
    public void subscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        synchronized (orderFlowListenerMap) {
            List<OrderFlowListener> listeners = orderFlowListenerMap.get(ticker);
            if (listeners == null) {
                listeners = Collections.synchronizedList(new ArrayList<OrderFlowListener>());
                orderFlowListenerMap.put(ticker, listeners);
            }
            synchronized (listeners) {
                listeners.add(listener);
            }
        }
    }

    @Override
    public void unsubscribeOrderFlow(Ticker ticker, OrderFlowListener listener) {
        synchronized (orderFlowListenerMap) {
            List<OrderFlowListener> listeners = orderFlowListenerMap.get(ticker);
            if (listeners != null) {
                synchronized (listeners) {
                    listeners.remove(listener);
                }
            }
        }
    }

    @Override
    public void subscribeGlobalOrderFlow(OrderFlowListener listener) {
        synchronized (globalOrderFlowListenerList) {
            globalOrderFlowListenerList.add(listener);
        }
    }

    @Override
    public void unsubscribeGlobalOrderFlow(OrderFlowListener listener) {
        synchronized (globalOrderFlowListenerList) {
            globalOrderFlowListenerList.remove(listener);
        }
    }

    @Override
    public void fireLevel1Quote(final ILevel1Quote quote) {
        l1Fires.incrementAndGet();
        synchronized (level1ListenerMap) {
            // Fire to global listeners
            if (globalLevel1ListenerList != null) {
                for (final Level1QuoteListener listener : globalLevel1ListenerList) {
                    try {
                        quoteExecutor.submit(() -> {
                            try {
                                listener.quoteRecieved(quote);
                            } catch (Exception ex) {
                                logger.warn("Error processing Level1 quote for global listener", ex);
                            }
                        });
                    } catch (Exception ex) {
                        logger.warn("Error submitting Level1 quote task for global listener", ex);
                    }
                }
            }

            List<Level1QuoteListener> listeners = level1ListenerMap.get(quote.getTicker());
            if (listeners == null) {
                return;
            }
            for (final Level1QuoteListener listener : listeners) {
                try {
                    synchronized (listeners) {
                        quoteExecutor.submit(() -> {
                            try {
                                listener.quoteRecieved(quote);
                            } catch (Exception ex) {
                                logger.warn("Error processing Level1 quote for listener", ex);
                            }
                        });
                    }
                } catch (Exception ex) {
                    // don't let 1 listener blowing up prevent other listeners from getting the
                    // quote.
                    logger.warn("Error submitting Level1 quote task", ex);
                }
            }
        }
    }

    @Override
    public void fireMarketDepthQuote(ILevel2Quote quote) {
        l2Fires.incrementAndGet();
        synchronized (level2ListenerMap) {
            List<Level2QuoteListener> listeners = level2ListenerMap.get(quote.getTicker());
            if (listeners == null) {
                return;
            }
            for (Level2QuoteListener listener : listeners) {
                try {
                    synchronized (listeners) {
                        quoteExecutor.submit(() -> {
                            try {
                                listener.level2QuoteReceived(quote);
                            } catch (Throwable ex) {
                                logger.error("Error processing Level2 quote for listener", ex);
                            }
                        });
                    }
                } catch (Throwable ex) {
                    logger.error("Error submitting Level2 quote task", ex);
                }
            }
        }
    }

    @Override
    public void fireOrderFlow(OrderFlow orderFlow) {
        orderFlowFires.incrementAndGet();
        synchronized (orderFlowListenerMap) {
            // Fire to global listeners
            if (globalOrderFlowListenerList != null) {
                for (final OrderFlowListener listener : globalOrderFlowListenerList) {
                    try {
                        quoteExecutor.submit(() -> {
                            try {
                                listener.orderflowReceived(orderFlow);
                            } catch (Exception ex) {
                                logger.warn("Error processing OrderFlow for global listener", ex);
                            }
                        });
                    } catch (Exception ex) {
                        logger.warn("Error submitting OrderFlow task for global listener", ex);
                    }
                }
            }

            List<OrderFlowListener> listeners = orderFlowListenerMap.get(orderFlow.getTicker());
            if (listeners == null) {
                return;
            }
            for (OrderFlowListener listener : listeners) {
                try {
                    synchronized (listeners) {
                        quoteExecutor.submit(() -> {
                            try {
                                listener.orderflowReceived(orderFlow);
                            } catch (Exception ex) {
                                logger.warn("Error processing OrderFlow for listener", ex);
                            }
                        });
                    }
                } catch (Exception ex) {
                    logger.warn("Error submitting OrderFlow task", ex);
                }
            }
        }
    }

    public void subscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        synchronized (ticker) {
            List<Level2QuoteListener> listeners = level2ListenerMap.get(ticker);
            if (listeners == null) {
                listeners = Collections.synchronizedList(new ArrayList<Level2QuoteListener>());
                level2ListenerMap.put(ticker, listeners);
            }
            listeners.add(listener);
        }
    }

    public void unsubscribeMarketDepth(Ticker ticker, Level2QuoteListener listener) {
        synchronized (ticker) {
            List<Level2QuoteListener> listeners = level2ListenerMap.get(ticker);
            if (listeners != null) {
                listeners.remove(listener);
            }
        }
    }

    /**
     * Shuts down the quote processing thread pool. Should be called when the
     * QuoteEngine is no longer needed to prevent resource leaks.
     */
    public void shutdown() {
        if (quoteExecutor != null && !quoteExecutor.isShutdown()) {
            logger.info("Shutting down quote processing thread pool");
            quoteExecutor.shutdown();
        }
    }

    /**
     * Immediately shuts down the quote processing thread pool. Should be called in
     * emergency situations to force immediate shutdown.
     */
    public void shutdownNow() {
        if (quoteExecutor != null && !quoteExecutor.isShutdown()) {
            logger.info("Force shutting down quote processing thread pool");
            quoteExecutor.shutdownNow();
        }
    }

    /**
     * Checks if the quote processing thread pool has been shut down
     * 
     * @return true if the thread pool has been shut down, false otherwise
     */
    public boolean isShutdown() {
        return quoteExecutor == null || quoteExecutor.isShutdown();
    }

    @Override
    public ILevel1Quote requestLevel1Snapshot(Ticker ticker) {
        throw new UnsupportedOperationException(
                getDataProviderName() + " does not support requestLevel1Snapshot");
    }
}
