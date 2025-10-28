/**
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.fueledbychai.broker.paradex;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.ForwardingBroker;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.broker.IBrokerOrderRegistry;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.ResponseException;
import com.fueledbychai.data.Ticker;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * A resilient wrapper around ParadexBroker that adds Resilience4j patterns for
 * handling transient failures and protecting against cascading failures.
 * 
 * This class acts as a decorator, delegating all operations to the underlying
 * ParadexBroker while adding retry, circuit breaker, and bulkhead patterns for
 * critical operations like order placement.
 */
public class ResilientParadexBroker extends ForwardingBroker {
    private static final Logger logger = LoggerFactory.getLogger(ResilientParadexBroker.class);

    protected IBroker delegate;
    protected IBrokerOrderRegistry orderRegistry;

    // Resilience4j components for order operations
    private final Retry placeOrderRetry;
    private final CircuitBreaker placeOrderCircuitBreaker;
    private final Bulkhead placeOrderBulkhead;

    // Resilience4j components for cancel operations
    private final Retry cancelOrderRetry;
    private final CircuitBreaker cancelOrderCircuitBreaker;

    // Resilience4j components for order status requests
    private final Retry orderStatusRetry;
    private final CircuitBreaker orderStatusCircuitBreaker;

    /**
     * Constructor that wraps an existing ParadexBroker with resilience patterns.
     * 
     * @param delegate the ParadexBroker instance to wrap
     */
    public ResilientParadexBroker(ParadexBroker delegate) {
        super(delegate);
        this.delegate = delegate;
        this.orderRegistry = delegate.getOrderRegistry();

        // Initialize resilience components
        this.placeOrderRetry = createRetryForOrders("placeOrder");
        this.placeOrderCircuitBreaker = createCircuitBreakerForOrders("placeOrder");
        this.placeOrderBulkhead = createBulkheadForOrders("placeOrder");

        this.cancelOrderRetry = createRetryForOrders("cancelOrder");
        this.cancelOrderCircuitBreaker = createCircuitBreakerForOrders("cancelOrder");

        this.orderStatusRetry = createRetryForOrders("orderStatus");
        this.orderStatusCircuitBreaker = createCircuitBreakerForOrders("orderStatus");

        setupEventListeners();

        // Forward events from delegate to our listeners
        // delegate.addOrderEventListener(this::fireOrderEvent);
        // delegate.addFillEventListener(this::fireFillEvent);
        // Note: Account equity updates are handled by delegate firing directly,
        // no need to forward since both extend AbstractBasicBroker

        logger.info("ResilientParadexBroker initialized with resilience patterns");
    }

    /**
     * Default constructor that creates a new ParadexBroker internally.
     */
    public ResilientParadexBroker() {
        this(new ParadexBroker());
    }

    /**
     * Creates a retry configuration optimized for order operations.
     */
    private Retry createRetryForOrders(String name) {
        RetryConfig retryConfig = RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(500))
                .retryOnException(this::shouldRetryOnException).build();

        return Retry.of(name, retryConfig);
    }

    /**
     * Creates a circuit breaker configuration for order operations.
     */
    private CircuitBreaker createCircuitBreakerForOrders(String name) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom().slidingWindowSize(10)
                .failureRateThreshold(50.0f).waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3).slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(5)).build();

        return CircuitBreaker.of(name, circuitBreakerConfig);
    }

    /**
     * Creates a bulkhead configuration for order operations.
     */
    private Bulkhead createBulkheadForOrders(String name) {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom().maxConcurrentCalls(5)
                .maxWaitDuration(Duration.ofSeconds(10)).build();

        return Bulkhead.of(name, bulkheadConfig);
    }

    /**
     * Determines whether an exception should trigger a retry.
     */
    private boolean shouldRetryOnException(Throwable exception) {
        // Retry on network issues and certain HTTP errors
        if (exception instanceof IOException) {
            logger.debug("Retrying due to IOException: {}", exception.getMessage());
            return true;
        }

        if (exception instanceof FueledByChaiException) {
            // Check if it wraps an IOException
            Throwable cause = exception.getCause();
            if (cause instanceof IOException) {
                logger.debug("Retrying due to FueledByChaiException wrapping IOException: {}", exception.getMessage());
                return true;
            }
        }

        if (exception instanceof ResponseException) {
            ResponseException responseEx = (ResponseException) exception;
            if (responseEx.getCause() instanceof IOException) {
                logger.debug("Retrying due to ResponseException wrapping IOException: {}", exception.getMessage());
                return true;
            }
            int statusCode = responseEx.getStatusCode();
            // Retry on server errors (5xx), rate limiting (429), and some client errors
            boolean shouldRetry = statusCode >= 500 || statusCode == 429 || statusCode == 503 || statusCode == 502;
            if (shouldRetry) {
                logger.debug("Retrying due to HTTP status {}: {}", statusCode, exception.getMessage());
            }
            return shouldRetry;
        }

        return false;
    }

    /**
     * Sets up event listeners for monitoring resilience patterns.
     */
    private void setupEventListeners() {
        // Retry event listeners
        placeOrderRetry.getEventPublisher().onRetry(event -> logger.warn("Retry attempt {} for placeOrder: {}",
                event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));

        cancelOrderRetry.getEventPublisher().onRetry(event -> logger.warn("Retry attempt {} for cancelOrder: {}",
                event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));

        orderStatusRetry.getEventPublisher().onRetry(event -> logger.warn("Retry attempt {} for orderStatus: {}",
                event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));

        // Circuit breaker event listeners
        placeOrderCircuitBreaker.getEventPublisher()
                .onStateTransition(event -> logger.info("PlaceOrder circuit breaker state transition: {} -> {}",
                        event.getStateTransition().getFromState(), event.getStateTransition().getToState()));

        cancelOrderCircuitBreaker.getEventPublisher()
                .onStateTransition(event -> logger.info("CancelOrder circuit breaker state transition: {} -> {}",
                        event.getStateTransition().getFromState(), event.getStateTransition().getToState()));

        orderStatusCircuitBreaker.getEventPublisher()
                .onStateTransition(event -> logger.info("OrderStatus circuit breaker state transition: {} -> {}",
                        event.getStateTransition().getFromState(), event.getStateTransition().getToState()));

        // Bulkhead event listeners
        placeOrderBulkhead.getEventPublisher()
                .onCallPermitted(event -> logger.debug("PlaceOrder bulkhead call permitted"));

        placeOrderBulkhead.getEventPublisher()
                .onCallRejected(event -> logger.warn("PlaceOrder bulkhead call rejected - too many concurrent calls"));
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        // Ensure order has a client order ID for reconciliation
        if (order.getClientOrderId() == null || order.getClientOrderId().trim().isEmpty()) {
            String clientOrderId = generateClientOrderId(order);
            order.setClientOrderId(clientOrderId);
            logger.debug("Generated client order ID {} for order {}", clientOrderId, order.getTicker().getSymbol());
        }

        boolean orderPlaced = false;
        Exception lastException = null;

        // Create resilient supplier for the order placement
        Supplier<BrokerRequestResult> orderPlacementSupplier = () -> {
            try {
                return delegate.placeOrder(order);
            } catch (Exception e) {
                logger.error("Error placing order for {}: {}", order.getTicker().getSymbol(), e.getMessage(), e);
                throw e;
            }
        };

        BrokerRequestResult result = null;
        try {
            // Apply resilience patterns: retry -> circuit breaker -> bulkhead
            result = Decorators.ofSupplier(orderPlacementSupplier).withRetry(placeOrderRetry)
                    .withCircuitBreaker(placeOrderCircuitBreaker).withBulkhead(placeOrderBulkhead).decorate().get();

            orderPlaced = true;
            logger.debug("Successfully placed order for {} with resilience patterns", order.getTicker().getSymbol());

        } catch (Exception e) {
            lastException = e;
            logger.warn("Order placement failed for {} after all retries: {}", order.getTicker().getSymbol(),
                    e.getMessage());
        }

        // If order placement failed or we're unsure of the result, attempt
        // reconciliation
        if (!orderPlaced || lastException != null) {
            logger.info("Attempting order reconciliation for {} due to placement failure or uncertainty",
                    order.getTicker().getSymbol());

            ReconciliationResult reconcileResult = reconcileOrderStatus(order);

            switch (reconcileResult) {
            case ORDER_FOUND:
                logger.info("Order reconciliation successful for {} - order was actually placed with ID: {}",
                        order.getTicker().getSymbol(), order.getOrderId());
                // Order was successfully placed despite the exception, so don't rethrow
                return new BrokerRequestResult();

            case ORDER_NOT_FOUND:
                logger.error("Order reconciliation confirmed for {} - order was definitely not placed on server",
                        order.getTicker().getSymbol());
                // We confirmed the order wasn't placed, safe to rethrow original exception
                if (lastException != null) {
                    if (lastException instanceof RuntimeException) {
                        throw (RuntimeException) lastException;
                    } else {
                        throw new RuntimeException("Order placement failed - confirmed order not on server",
                                lastException);
                    }
                }
                return new BrokerRequestResult();

            case VERIFICATION_FAILED:
                logger.error(
                        "Order reconciliation failed for {} - unable to verify order status due to network/API issues. Order state unknown!",
                        order.getTicker().getSymbol());
                // We don't know if the order was placed or not - this is dangerous!
                // Throw a specific exception to alert calling code
                throw new RuntimeException("Order placement failed and reconciliation could not verify order status. "
                        + "Order may or may not have been placed on server. Manual verification required for client order ID: "
                        + order.getClientOrderId(), lastException);

            default:
                throw new RuntimeException("Unexpected reconciliation result: " + reconcileResult);
            }
        }

        return result != null ? result : new BrokerRequestResult();
    }

    /**
     * Generates a unique client order ID for tracking purposes.
     * 
     * @param order the order ticket
     * @return a unique client order ID
     */
    private String generateClientOrderId(OrderTicket order) {
        return String.format("%s-%d-%d", order.getTicker().getSymbol().replace("-", ""), System.currentTimeMillis(),
                System.nanoTime() % 10000);
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        // Create resilient supplier for the order cancellation
        Supplier<BrokerRequestResult> cancelOrderSupplier = () -> {
            try {
                return delegate.cancelOrder(id);
            } catch (Exception e) {
                logger.error("Error canceling order {}: {}", id, e.getMessage(), e);
                throw e;
            }
        };

        // Apply resilience patterns: retry -> circuit breaker
        return Decorators.ofSupplier(cancelOrderSupplier).withRetry(cancelOrderRetry)
                .withCircuitBreaker(cancelOrderCircuitBreaker).decorate().get();
    }

    @Override
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        boolean cancelSuccessful = false;
        Exception lastException = null;

        // Create resilient supplier for the order cancellation
        Supplier<BrokerRequestResult> cancelOrderSupplier = () -> {
            try {
                return delegate.cancelOrderByClientOrderId(clientOrderId);
            } catch (Exception e) {
                logger.error("Error canceling order by client ID {}: {}", clientOrderId, e.getMessage(), e);
                throw e;
            }
        };

        BrokerRequestResult result = null;
        try {
            // Apply resilience patterns: retry -> circuit breaker
            result = Decorators.ofSupplier(cancelOrderSupplier).withRetry(cancelOrderRetry)
                    .withCircuitBreaker(cancelOrderCircuitBreaker).decorate().get();

            cancelSuccessful = true;
            logger.debug("Successfully canceled order with client ID {} using resilience patterns", clientOrderId);

        } catch (Exception e) {
            lastException = e;
            logger.warn("Order cancellation failed for client ID {} after all retries: {}", clientOrderId,
                    e.getMessage());
        }

        // If cancellation failed, attempt to check order status for reconciliation
        if (!cancelSuccessful || lastException != null) {
            logger.info("Attempting to reconcile order status for client ID {} due to cancellation failure",
                    clientOrderId);

            try {
                OrderTicket orderStatus = requestOrderStatusByClientOrderId(clientOrderId);
                if (orderStatus != null) {
                    logger.info("Order reconciliation for client ID {}: Order ID = {}, Status = {}", clientOrderId,
                            orderStatus.getOrderId(), orderStatus.getCurrentStatus());

                    // Check if the order is already canceled or filled (i.e., cancellation not
                    // needed)
                    if (orderStatus.getCurrentStatus() != null
                            && (orderStatus.getCurrentStatus().toString().contains("CANCEL")
                                    || orderStatus.getCurrentStatus().toString().contains("FILLED"))) {
                        logger.info(
                                "Order with client ID {} is already in final state: {}, treating cancellation as successful",
                                clientOrderId, orderStatus.getCurrentStatus());
                        return new BrokerRequestResult();
                    }
                } else {
                    logger.info(
                            "Order reconciliation for client ID {} - no order found on server, may have been already canceled or expired",
                            clientOrderId);
                    // If no order is found, it might have been successfully canceled or never
                    // existed
                    return new BrokerRequestResult();
                }
            } catch (Exception reconciliationException) {
                logger.warn("Failed to reconcile order status for client ID {}: {}", clientOrderId,
                        reconciliationException.getMessage());
            }

            // If we reach here, cancellation failed and reconciliation didn't show the
            // order as canceled
            if (lastException != null) {
                if (lastException instanceof RuntimeException) {
                    throw (RuntimeException) lastException;
                } else {
                    throw new RuntimeException("Order cancellation failed and reconciliation unsuccessful",
                            lastException);
                }
            }
        }

        return result != null ? result : new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        return cancelOrder(order.getOrderId());
    }

    @Override
    public String getNextOrderId() {
        return delegate.getNextOrderId();
    }

    @Override
    public void connect() {
        delegate.connect();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        return delegate.requestOrderStatus(orderId);
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        return delegate.getOpenOrders();
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        delegate.cancelAndReplaceOrder(originalOrderId, newOrder);
    }

    @Override
    public List<Position> getAllPositions() {
        return delegate.getAllPositions();
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        return delegate.cancelAllOrders(ticker);
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        return delegate.cancelAllOrders();
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        // Create resilient supplier for the order status request
        Supplier<OrderTicket> orderStatusSupplier = () -> {
            try {
                return delegate.requestOrderStatusByClientOrderId(clientOrderId);
            } catch (Exception e) {
                logger.error("Error requesting order status for client order ID {}: {}", clientOrderId, e.getMessage(),
                        e);
                throw e;
            }
        };

        // Apply resilience patterns: retry -> circuit breaker
        return Decorators.ofSupplier(orderStatusSupplier).withRetry(orderStatusRetry)
                .withCircuitBreaker(orderStatusCircuitBreaker).decorate().get();
    }

    /**
     * Enum representing the possible outcomes of order reconciliation.
     */
    protected enum ReconciliationResult {
        /** Order was found on the server and successfully reconciled */
        ORDER_FOUND,
        /**
         * Order was confirmed NOT to exist on the server (safe to assume placement
         * failed)
         */
        ORDER_NOT_FOUND,
        /** Unable to verify order status due to network/API issues (unknown state) */
        VERIFICATION_FAILED
    }

    /**
     * Attempts to reconcile an order by checking its status via client order ID.
     * This method is used when placeOrder fails to determine if the order was
     * actually placed.
     * 
     * @param order the order ticket to reconcile
     * @return ReconciliationResult indicating the outcome of reconciliation
     */
    protected ReconciliationResult reconcileOrderStatus(OrderTicket order) {
        if (order.getClientOrderId() == null || order.getClientOrderId().trim().isEmpty()) {
            logger.warn("Cannot reconcile order - no client order ID available for {}", order.getTicker().getSymbol());
            return ReconciliationResult.VERIFICATION_FAILED;
        }

        try {
            logger.info("Attempting to reconcile order status for client order ID: {}", order.getClientOrderId());
            OrderTicket retrievedOrder = requestOrderStatusByClientOrderId(order.getClientOrderId());

            if (retrievedOrder != null) {
                // Order was found on the server, update our local order with server info
                order.setOrderId(retrievedOrder.getOrderId());
                order.setCurrentStatus(retrievedOrder.getCurrentStatus());
                order.setFilledSize(retrievedOrder.getFilledSize());
                order.setFilledPrice(retrievedOrder.getFilledPrice());

                logger.info("Successfully reconciled order for client ID {}: Server Order ID = {}, Status = {}",
                        order.getClientOrderId(), order.getOrderId(), order.getCurrentStatus());
                return ReconciliationResult.ORDER_FOUND;
            } else {
                logger.info(
                        "Order reconciliation completed - confirmed order does not exist on server for client ID: {}",
                        order.getClientOrderId());
                return ReconciliationResult.ORDER_NOT_FOUND;
            }
        } catch (ResponseException e) {
            // Distinguish between client errors (4xx) and server errors (5xx)
            if (e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
                if (e.getStatusCode() == 404) {
                    logger.info(
                            "Order reconciliation completed - server confirmed order does not exist for client ID: {}",
                            order.getClientOrderId());
                    return ReconciliationResult.ORDER_NOT_FOUND;
                } else {
                    logger.warn(
                            "Client error during reconciliation for client ID {}: {} - treating as verification failure",
                            order.getClientOrderId(), e.getMessage());
                    return ReconciliationResult.VERIFICATION_FAILED;
                }
            } else {
                logger.warn("Server error during reconciliation for client ID {}: {} - order status unknown",
                        order.getClientOrderId(), e.getMessage());
                return ReconciliationResult.VERIFICATION_FAILED;
            }
        } catch (Exception e) {
            logger.warn("Unexpected error during reconciliation for client ID {}: {} - order status unknown",
                    order.getClientOrderId(), e.getMessage());
            return ReconciliationResult.VERIFICATION_FAILED;
        }
    }

    /**
     * Provides access to the underlying delegate for testing or advanced use cases.
     * 
     * @return the wrapped ParadexBroker instance
     */
    public IBroker getDelegate() {
        return delegate;
    }

    /**
     * Gets the current state of the place order circuit breaker.
     * 
     * @return the circuit breaker state
     */
    public CircuitBreaker.State getPlaceOrderCircuitBreakerState() {
        return placeOrderCircuitBreaker.getState();
    }

    /**
     * Gets the current state of the cancel order circuit breaker.
     * 
     * @return the circuit breaker state
     */
    public CircuitBreaker.State getCancelOrderCircuitBreakerState() {
        return cancelOrderCircuitBreaker.getState();
    }

    /**
     * Gets metrics from the place order retry component.
     * 
     * @return retry metrics
     */
    public Retry.Metrics getPlaceOrderRetryMetrics() {
        return placeOrderRetry.getMetrics();
    }

    /**
     * Gets metrics from the cancel order retry component.
     * 
     * @return retry metrics
     */
    public Retry.Metrics getCancelOrderRetryMetrics() {
        return cancelOrderRetry.getMetrics();
    }

}