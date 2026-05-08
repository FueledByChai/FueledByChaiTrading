/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.broker;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.FillEventListener;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderEventListener;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.ComboTicker;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.time.TimeUpdatedListener;

/**
 *
 *  
 */
public abstract class AbstractBasicBroker implements IBroker {

    protected IBrokerOrderRegistry orderRegistry = new BrokerOrderRegistry();

    public IBrokerOrderRegistry getOrderRegistry() {
        return orderRegistry;
    }

    public void setTestOrderRegistry(IBrokerOrderRegistry orderRegistry) {
        this.orderRegistry = orderRegistry;
    }

    /**
     * Disconnects the broker and releases resources. Subclasses should implement
     * onDisconnect() for custom cleanup. This method is final to guarantee resource
     * cleanup.
     */
    @Override
    public final void disconnect() {
        onDisconnect();
        shutdown();
    }

    /**
     * Subclass hook for custom disconnect logic. Called automatically by
     * disconnect().
     */
    protected abstract void onDisconnect();

    /**
     * Shuts down the event executor service. Should be called when the broker is no
     * longer needed.
     */
    public void shutdown() {
        eventExecutor.shutdown();
    }

    protected Set<OrderEventListener> orderEventListeners = new HashSet<>();
    protected Set<FillEventListener> fillEventListeners = new HashSet<>();
    protected Set<BrokerErrorListener> brokerErrorListeners = new HashSet<>();
    protected Set<TimeUpdatedListener> timeUpdatedListeners = new HashSet<>();
    protected Set<BrokerAccountInfoListener> brokerAccountInfoListeners = new HashSet<>();
    protected static Logger logger = LoggerFactory.getLogger(AbstractBasicBroker.class);
    protected final java.util.concurrent.ExecutorService eventExecutor = java.util.concurrent.Executors
            .newCachedThreadPool();

    @Override
    public void addOrderEventListener(OrderEventListener listener) {
        synchronized (orderEventListeners) {
            orderEventListeners.add(listener);
        }
    }

    @Override
    public void addBrokerErrorListener(BrokerErrorListener listener) {
        synchronized (brokerErrorListeners) {
            brokerErrorListeners.add(listener);
        }
    }

    @Override
    public void addTimeUpdateListener(TimeUpdatedListener listener) {
        synchronized (timeUpdatedListeners) {
            timeUpdatedListeners.add(listener);
        }
    }

    @Override
    public void removeOrderEventListener(OrderEventListener listener) {
        synchronized (orderEventListeners) {
            orderEventListeners.remove(listener);
        }
    }

    @Override
    public void removeBrokerErrorListener(BrokerErrorListener listener) {
        synchronized (brokerErrorListeners) {
            brokerErrorListeners.remove(listener);
        }
    }

    @Override
    public void removeTimeUpdateListener(TimeUpdatedListener listener) {
        synchronized (timeUpdatedListeners) {
            timeUpdatedListeners.remove(listener);
        }
    }

    @Override
    public void addFillEventListener(FillEventListener listener) {
        synchronized (fillEventListeners) {
            fillEventListeners.add(listener);
        }
    }

    @Override
    public void removeFillEventListener(FillEventListener listener) {
        synchronized (fillEventListeners) {
            fillEventListeners.remove(listener);
        }
    }

    /**
     * Submits a listener-notification task to {@link #eventExecutor}, silently
     * dropping the task if the executor has been shut down. Without this guard,
     * any event fired after {@link #disconnect()} (typical when a stale WS
     * reader outlives the broker — see ParadexBroker / HyperliquidBroker leak
     * fixes 2026-05-08) produces a {@link RejectedExecutionException} per
     * event, flooding the error log indefinitely. Post-disconnect listener
     * notification is not useful, so dropping is the correct behavior.
     */
    private void dispatch(Runnable task) {
        if (eventExecutor.isShutdown()) {
            return;
        }
        try {
            eventExecutor.submit(task);
        } catch (RejectedExecutionException ex) {
            // Race: the executor was shut down between the isShutdown() check
            // and submit. Same disposition as the fast path — drop silently.
            logger.debug("Dropped broker event submit after executor shutdown", ex);
        }
    }

    protected void fireOrderEvent(OrderEvent event) {
        logger.info("Firing order event: {}", event);
        synchronized (orderEventListeners) {
            for (OrderEventListener listener : orderEventListeners) {
                dispatch(() -> {
                    try {
                        listener.orderEvent(event);
                    } catch (Exception ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    protected void fireBrokerError(BrokerError error) {
        synchronized (brokerErrorListeners) {
            for (BrokerErrorListener listener : brokerErrorListeners) {
                dispatch(() -> {
                    try {
                        listener.brokerErrorFired(error);
                    } catch (Exception ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    protected void fireAccountEquityUpdated(double equity) {
        synchronized (brokerAccountInfoListeners) {
            for (BrokerAccountInfoListener listener : brokerAccountInfoListeners) {
                dispatch(() -> {
                    try {
                        listener.accountEquityUpdated(equity);
                    } catch (Exception ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    protected void fireAvailableFundsUpdated(double availableFunds) {
        synchronized (brokerAccountInfoListeners) {
            for (BrokerAccountInfoListener listener : brokerAccountInfoListeners) {
                dispatch(() -> {
                    try {
                        listener.availableFundsUpdated(availableFunds);
                    } catch (Exception ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    protected void fireFillEvent(Fill fill) {
        logger.info("Firing fill event: {}", fill);
        synchronized (fillEventListeners) {
            for (FillEventListener listener : fillEventListeners) {
                dispatch(() -> {
                    try {
                        listener.fillReceived(fill);
                    } catch (Exception ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    @Override
    public void aquireLock() {
        throw new UnsupportedOperationException("Not supported"); // To change body of generated methods, choose Tools |
                                                                  // Templates.

    }

    @Override
    public ComboTicker buildComboTicker(Ticker ticker1, Ticker ticker2) {
        throw new UnsupportedOperationException("Not supported"); // To change body of generated methods, choose Tools |
                                                                  // Templates.
    }

    @Override
    public ComboTicker buildComboTicker(Ticker ticker1, int ratio1, Ticker ticker2, int ratio2) {
        throw new UnsupportedOperationException("Not supported"); // To change body of generated methods, choose Tools |
                                                                  // Templates.
    }

    @Override
    public ZonedDateTime getCurrentTime() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    @Override
    public String getFormattedDate(int hour, int minute, int second) {
        throw new UnsupportedOperationException("Not supported"); // To change body of generated methods, choose Tools |
                                                                  // Templates.
    }

    @Override
    public String getFormattedDate(ZonedDateTime date) {
        throw new UnsupportedOperationException("Not supported"); // To change body of generated methods, choose Tools |
                                                                  // Templates.
    }

    @Override
    public void releaseLock() {
        throw new UnsupportedOperationException("Not supported"); // To change body of generated methods, choose Tools |
                                                                  // Templates.
    }

    @Override
    public void addBrokerAccountInfoListener(BrokerAccountInfoListener listener) {
        synchronized (brokerAccountInfoListeners) {
            brokerAccountInfoListeners.add(listener);
        }

    }

    @Override
    public void removeBrokerAccountInfoListener(BrokerAccountInfoListener listener) {
        synchronized (brokerAccountInfoListeners) {
            brokerAccountInfoListeners.remove(listener);
        }

    }

    @Override
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        throw new UnsupportedOperationException("Not supported"); // To change body of generated methods, choose Tools |
                                                                  // Templates.
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        throw new UnsupportedOperationException("Not supported"); // To change body of generated methods, choose Tools |
                                                                  // Templates.
    }

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        throw new UnsupportedOperationException("Not supported"); // To change body of generated methods, choose Tools |
                                                                  // Templates.
    }
}
