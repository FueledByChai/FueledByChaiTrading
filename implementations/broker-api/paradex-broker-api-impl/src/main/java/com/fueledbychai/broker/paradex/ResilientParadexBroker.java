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

import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerRequestResult.FailureType;
import com.fueledbychai.broker.ForwardingBroker;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.broker.IBrokerOrderRegistry;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderStatus.Status;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.FueledByChaiException;
import com.fueledbychai.data.ResponseException;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.time.Span;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * A resilient wrapper around ParadexBroker that adds Resilience4j patterns
 * optimized for automated trading applications.
 * 
 * This class uses trading-focused resilience patterns: - RETRY: Applied to all
 * operations for handling transient network issues - BULKHEAD: Prevents
 * resource exhaustion without blocking critical operations - CIRCUIT BREAKERS:
 * Used for monitoring only - do NOT block critical operations
 * 
 * Key Design Principle: Trading operations (place/modify/cancel) must ALWAYS be
 * attempted for risk management, even during exchange degradation. Circuit
 * breakers that prevent these operations are counterproductive in trading
 * scenarios.
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

    // Resilience4j components for modify operations
    private final Retry modifyOrderRetry;
    private final CircuitBreaker modifyOrderCircuitBreaker;
    private final Bulkhead modifyOrderBulkhead;

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
        // NOTE: Circuit breakers are created for monitoring but NOT used for blocking
        // critical trading operations (place/modify/cancel). Trading apps need these
        // operations to work even during exchange degradation for risk management.
        this.placeOrderRetry = createRetryForOrders("placeOrder");
        this.placeOrderCircuitBreaker = createCircuitBreakerForOrders("placeOrder"); // For monitoring only
        this.placeOrderBulkhead = createBulkheadForOrders("placeOrder");

        this.cancelOrderRetry = createRetryForOrders("cancelOrder");
        this.cancelOrderCircuitBreaker = createCriticalCircuitBreakerForOrders("cancelOrder"); // For monitoring only

        this.modifyOrderRetry = createRetryForOrders("modifyOrder");
        this.modifyOrderCircuitBreaker = createCriticalCircuitBreakerForOrders("modifyOrder"); // For monitoring only
        this.modifyOrderBulkhead = createBulkheadForOrders("modifyOrder");

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
     * Creates a circuit breaker configuration for order operations. NOTE: For
     * trading applications, circuit breakers can be counterproductive. Consider
     * using DISABLED circuit breakers or very permissive settings.
     */
    private CircuitBreaker createCircuitBreakerForOrders(String name) {
        // Option 1: Disabled circuit breaker (always allows calls)
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom().slidingWindowSize(100) // Large window
                .failureRateThreshold(95.0f) // Very high threshold (95% failures needed)
                .waitDurationInOpenState(Duration.ofSeconds(5)) // Short wait time
                .permittedNumberOfCallsInHalfOpenState(10) // More test calls
                .slowCallRateThreshold(95.0f) // Very high slow call threshold
                .slowCallDurationThreshold(Duration.ofSeconds(30)) // Allow slow calls
                .build();

        return CircuitBreaker.of(name, circuitBreakerConfig);
    }

    /**
     * Creates a very permissive circuit breaker for critical operations like
     * cancel/modify. In trading, these operations must be attempted even if the
     * service appears degraded.
     */
    private CircuitBreaker createCriticalCircuitBreakerForOrders(String name) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom().slidingWindowSize(50) // Larger window
                                                                                                        // for better
                                                                                                        // assessment
                .failureRateThreshold(90.0f) // 90% failure rate needed to open
                .waitDurationInOpenState(Duration.ofSeconds(2)) // Very short wait time
                .permittedNumberOfCallsInHalfOpenState(5) // More test calls
                .slowCallRateThreshold(90.0f) // Allow most slow calls
                .slowCallDurationThreshold(Duration.ofSeconds(15)) // Allow slower operations
                .automaticTransitionFromOpenToHalfOpenEnabled(true) // Auto recovery
                .build();

        return CircuitBreaker.of(name + "-critical", circuitBreakerConfig);
    }

    /**
     * Creates a bulkhead configuration for order operations.
     */
    private Bulkhead createBulkheadForOrders(String name) {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom().maxConcurrentCalls(40)
                .maxWaitDuration(Duration.ofSeconds(5)).build();

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

        modifyOrderRetry.getEventPublisher().onRetry(event -> logger.warn("Retry attempt {} for modifyOrder: {}",
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

        modifyOrderCircuitBreaker.getEventPublisher()
                .onStateTransition(event -> logger.info("ModifyOrder circuit breaker state transition: {} -> {}",
                        event.getStateTransition().getFromState(), event.getStateTransition().getToState()));

        orderStatusCircuitBreaker.getEventPublisher()
                .onStateTransition(event -> logger.info("OrderStatus circuit breaker state transition: {} -> {}",
                        event.getStateTransition().getFromState(), event.getStateTransition().getToState()));

        // Bulkhead event listeners
        placeOrderBulkhead.getEventPublisher()
                .onCallPermitted(event -> logger.debug("PlaceOrder bulkhead call permitted"));

        placeOrderBulkhead.getEventPublisher()
                .onCallRejected(event -> logger.warn("PlaceOrder bulkhead call rejected - too many concurrent calls"));

        modifyOrderBulkhead.getEventPublisher()
                .onCallPermitted(event -> logger.debug("ModifyOrder bulkhead call permitted"));

        modifyOrderBulkhead.getEventPublisher()
                .onCallRejected(event -> logger.warn("ModifyOrder bulkhead call rejected - too many concurrent calls"));
    }

    /**
     * Resets all circuit breakers to CLOSED state. This can be useful for recovery
     * when circuit breakers are stuck in OPEN state due to transient issues that
     * have been resolved.
     */
    public void resetCircuitBreakers() {
        logger.info("Resetting all circuit breakers to CLOSED state");
        placeOrderCircuitBreaker.reset();
        cancelOrderCircuitBreaker.reset();
        modifyOrderCircuitBreaker.reset();
        orderStatusCircuitBreaker.reset();
        logger.info("All circuit breakers have been reset");
    }

    /**
     * Gets the current state of all circuit breakers for monitoring purposes.
     */
    public String getCircuitBreakerStatus() {
        return String.format(
                "Circuit Breaker Status - PlaceOrder: %s, CancelOrder: %s, ModifyOrder: %s, OrderStatus: %s",
                placeOrderCircuitBreaker.getState(), cancelOrderCircuitBreaker.getState(),
                modifyOrderCircuitBreaker.getState(), orderStatusCircuitBreaker.getState());
    }

    /**
     * Emergency cancel that bypasses circuit breaker. Use this when circuit breaker
     * is OPEN but you need to cancel orders (e.g., risk management, expired
     * orders). This attempts to reset the circuit breaker and perform the cancel
     * operation.
     */
    public BrokerRequestResult emergencyCancelOrder(String id) {
        logger.warn("Emergency cancel requested for order {} - bypassing circuit breaker protection", id);

        // Check if we can attempt a health check first
        if (isServiceHealthy()) {
            logger.info("Service appears healthy, resetting cancel circuit breaker for order {}", id);
            cancelOrderCircuitBreaker.reset();

            // Now try the normal cancel operation
            return cancelOrder(id);
        } else {
            logger.warn("Service may be unhealthy, attempting direct cancel for order {} without circuit breaker", id);

            // Direct call without circuit breaker protection
            try {
                return delegate.cancelOrder(id);
            } catch (Exception e) {
                logger.error("Emergency cancel failed for order {}: {}", id, e.getMessage(), e);
                throw new FueledByChaiException("Emergency cancel failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Emergency cancel by client order ID that bypasses circuit breaker.
     */
    public BrokerRequestResult emergencyCancelOrderByClientOrderId(String clientOrderId) {
        logger.warn("Emergency cancel by client ID requested for {} - bypassing circuit breaker protection",
                clientOrderId);

        // Check if we can attempt a health check first
        if (isServiceHealthy()) {
            logger.info("Service appears healthy, resetting cancel circuit breaker for client order {}", clientOrderId);
            cancelOrderCircuitBreaker.reset();

            // Now try the normal cancel operation
            return cancelOrderByClientOrderId(clientOrderId);
        } else {
            logger.warn(
                    "Service may be unhealthy, attempting direct cancel for client order {} without circuit breaker",
                    clientOrderId);

            // Direct call without circuit breaker protection
            try {
                return delegate.cancelOrderByClientOrderId(clientOrderId);
            } catch (Exception e) {
                logger.error("Emergency cancel by client ID failed for {}: {}", clientOrderId, e.getMessage(), e);
                throw new FueledByChaiException("Emergency cancel by client ID failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Performs a simple health check to see if the underlying service is
     * responsive. This can be used to determine if circuit breakers should be
     * reset.
     */
    private boolean isServiceHealthy() {
        try {
            // Check if other circuit breakers are in a healthy state
            if (orderStatusCircuitBreaker.getState() == CircuitBreaker.State.CLOSED
                    || placeOrderCircuitBreaker.getState() == CircuitBreaker.State.CLOSED) {
                // If other operations are working, the service is likely healthy
                logger.debug("Health check passed - other circuit breakers are closed");
                return true;
            }

            // If all circuit breakers are open/half-open, we can't easily test health
            // In this case, we'll be conservative and assume unhealthy
            logger.debug("Health check inconclusive - all circuit breakers are open/half-open");
            return false;
        } catch (Exception e) {
            logger.debug("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        // Ensure order has a client order ID for reconciliation
        if (order.getClientOrderId() == null || order.getClientOrderId().trim().isEmpty()) {
            try (var s = Span.start("PD_GENERATE_CLIENT_ORDER_ID", order.getTicker().getSymbol())) {
                String clientOrderId = generateClientOrderId(order);
                order.setClientOrderId(clientOrderId);
                logger.debug("Generated client order ID {} for order {}", clientOrderId, order.getTicker().getSymbol());
            }
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
            // For critical place operations, use retry + bulkhead (no circuit breaker
            // blocking)
            // Trading applications need to place orders for risk management even if
            // exchange seems degraded
            result = Decorators.ofSupplier(orderPlacementSupplier).withRetry(placeOrderRetry)
                    .withBulkhead(placeOrderBulkhead).decorate().get();

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

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        // Ensure order has a client order ID for reconciliation
        if (order.getClientOrderId() == null || order.getClientOrderId().trim().isEmpty()) {
            try (var s = Span.start("PD_GENERATE_CLIENT_ORDER_ID", order.getTicker().getSymbol())) {
                String clientOrderId = generateClientOrderId(order);
                order.setClientOrderId(clientOrderId);
                logger.debug("Generated client order ID {} for modify order {}", clientOrderId,
                        order.getTicker().getSymbol());
            }
        }

        boolean orderModified = false;
        Exception lastException = null;

        // Create resilient supplier for the order modification
        Supplier<BrokerRequestResult> orderModificationSupplier = () -> {
            try {
                return delegate.modifyOrder(order);
            } catch (Exception e) {
                // Special handling for Paradex error: 400 INVALID_REQUEST_PARAMETER or
                // ORDER_IS_NOT_OPEN
                if (e instanceof ResponseException) {
                    ResponseException re = (ResponseException) e;
                    if (re.getStatusCode() == 400 && re.getMessage() != null) {
                        if (re.getMessage().contains("INVALID_REQUEST_PARAMETER")) {
                            if (re.getMessage().contains("no order parameters changed")) {
                                logger.warn(
                                        "Paradex modifyOrder: No order parameters changed for {}. Returning failed BrokerRequestResult and skipping reconciliation.",
                                        order.getClientOrderId());
                                return new BrokerRequestResult(false, false,
                                        "Modify failed: no order parameters changed",
                                        FailureType.NO_ORDER_PARAMS_CHANGED);
                            } else if (re.getMessage().contains("size: must be a non-negative non-zero number.")) {
                                logger.warn(
                                        "Paradex modifyOrder: Invalid size for {}. Returning failed BrokerRequestResult and skipping reconciliation.",
                                        order.getClientOrderId());
                                return new BrokerRequestResult(false, false,
                                        "Modify failed: size must be a non-negative non-zero number.",
                                        FailureType.INVALID_SIZE);
                            }
                        } else if (re.getMessage().contains("ORDER_IS_NOT_OPEN")
                                && re.getMessage().contains("status: cannot be modified.")) {
                            logger.warn(
                                    "Paradex modifyOrder: Order is not open for {}. Returning failed BrokerRequestResult and skipping reconciliation.",
                                    order.getClientOrderId());
                            return new BrokerRequestResult(false, false,
                                    "Modify failed: order is not open (cannot be modified)",
                                    FailureType.ORDER_NOT_OPEN);
                        }
                    } else if (re.getStatusCode() == 404) {
                        logger.warn(
                                "Paradex modifyOrder: Order not found for {}. Returning failed BrokerRequestResult and skipping reconciliation.",
                                order.getClientOrderId());
                        return new BrokerRequestResult(false, false, "Modify failed: order not found",
                                FailureType.ORDER_NOT_FOUND);
                    }
                }
                logger.error("Error modifying order for {}: ID: {} message: {}", order.getTicker().getSymbol(),
                        order.getClientOrderId(), e.getMessage(), e);
                throw e;
            }
        };

        BrokerRequestResult result = null;
        try {
            // For critical modify operations, use retry + bulkhead (no circuit breaker
            // blocking)
            // Trading applications need to modify orders for risk management even if
            // exchange seems degraded
            result = Decorators.ofSupplier(orderModificationSupplier).withRetry(modifyOrderRetry)
                    .withBulkhead(modifyOrderBulkhead).decorate().get();

            // If the result is a failed result for NO_ORDER_PARAMS_CHANGED, return
            // immediately (skip reconciliation)
            if (result != null && !result.isSuccess()
                    && result.getFailureType() == FailureType.NO_ORDER_PARAMS_CHANGED) {
                return result;
            }

            orderModified = true;
            logger.debug("Successfully modified order for {} with resilience patterns", order.getClientOrderId());

        } catch (Exception e) {
            lastException = e;
            logger.warn("Order modification failed for {} after all retries: {}", order.getClientOrderId(),
                    e.getMessage());
        }

        // If order modification failed or we're unsure of the result, attempt
        // reconciliation
        if (!orderModified || lastException != null) {
            logger.info("Attempting order reconciliation for {} due to modification failure or uncertainty",
                    order.getClientOrderId());

            ReconciliationResult reconcileResult = reconcileOrderStatus(order);

            switch (reconcileResult) {
            case ORDER_FOUND:
                logger.info("Order reconciliation successful for {} - order was actually modified with ID: {}",
                        order.getClientOrderId(), order.getOrderId());
                // If the reconciled order is already final, surface that to the caller so they
                // can
                // respond appropriately (e.g., filled or canceled). Otherwise treat as success.
                Status status = order.getCurrentStatus();
                if (status != null) {
                    switch (status) {
                    case FILLED:
                        return new BrokerRequestResult(false, false, "Modify failed: order already FILLED",
                                FailureType.ORDER_ALREADY_FILLED);
                    case CANCELED:
                        return new BrokerRequestResult(false, false, "Modify failed: order already CANCELED",
                                FailureType.ORDER_ALREADY_CANCELED);
                    default:
                        break;
                    }
                }

                return new BrokerRequestResult();

            case ORDER_NOT_FOUND:
                logger.error("Order reconciliation confirmed for {} - order was not found on server",
                        order.getClientOrderId());
                // We confirmed the order wasn't found, safe to rethrow original exception
                if (lastException != null) {
                    if (lastException instanceof RuntimeException) {
                        throw (RuntimeException) lastException;
                    } else {
                        throw new RuntimeException("Order modification failed - order not found on server",
                                lastException);
                    }
                }
                return new BrokerRequestResult(false, false, "Modify failed: order not found on server",
                        FailureType.ORDER_NOT_FOUND);

            case VERIFICATION_FAILED:
                logger.error(
                        "Order reconciliation failed for {} - unable to verify order status due to network/API issues. Order state unknown!",
                        order.getClientOrderId());
                // We don't know if the order was modified or not - this is dangerous!
                // Throw a specific exception to alert calling code
                throw new RuntimeException(
                        "Order modification failed and reconciliation could not verify order status. "
                                + "Order may or may not have been modified on server. Manual verification required for client order ID: "
                                + order.getClientOrderId(),
                        lastException);

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
            } catch (ResponseException e) {
                // Check if this is the "ORDER_IS_CLOSED" error
                if (e.getStatusCode() == 400 && e.getMessage() != null
                        && e.getMessage().toLowerCase().contains("order is closed")) {
                    logger.info(
                            "Order {} is already closed, checking actual order status to determine if canceled or filled",
                            id);

                    try {
                        // Check the actual order status to see if it was canceled or filled
                        OrderTicket orderStatus = requestOrderStatus(id);
                        if (orderStatus != null && orderStatus.getCurrentStatus() != null) {
                            String status = orderStatus.getCurrentStatus().toString().toUpperCase();
                            if (status.contains("CANCEL")) {
                                logger.info(
                                        "Order {} was already canceled, treating cancellation request as successful",
                                        id);
                                return new BrokerRequestResult(true, "Order already canceled");
                            } else if (status.contains("FILLED") || status.contains("COMPLETE")) {
                                logger.warn("Order {} was already filled, cannot cancel a filled order", id);
                                return new BrokerRequestResult(false, "Cannot cancel order - order was already filled");
                            } else {
                                logger.warn("Order {} is closed with status {}, treating as cancellation failure", id,
                                        status);
                                return new BrokerRequestResult(false, "Order is closed with status: " + status);
                            }
                        } else {
                            logger.warn("Could not retrieve order status for closed order {}, assuming it was canceled",
                                    id);
                            return new BrokerRequestResult(true, "Order is closed (status unknown, assuming canceled)");
                        }
                    } catch (Exception statusException) {
                        logger.warn("Failed to check order status for closed order {}: {}", id,
                                statusException.getMessage());
                        // If we can't check the status, we should not assume it was successfully
                        // canceled
                        throw e; // Re-throw the original exception
                    }
                }
                logger.error("Error canceling order {}: {}", id, e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                logger.error("Error canceling order {}: {}", id, e.getMessage(), e);
                throw e;
            }
        };

        // No circuit breaker for cancel operations - always attempt cancellation for
        // risk management

        try {
            // For critical cancel operations, only use retry (no circuit breaker blocking)
            // Trading applications need to attempt cancels even if exchange seems degraded
            return Decorators.ofSupplier(cancelOrderSupplier).withRetry(cancelOrderRetry).decorate().get();
        } catch (Exception e) {
            logger.error("Cancel operation failed after all retries for order {}: {}", id, e.getMessage());
            throw e;
        }
    }

    @Override
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        boolean cancelSuccessful = false;
        Exception lastException = null;

        // Create resilient supplier for the order cancellation
        Supplier<BrokerRequestResult> cancelOrderSupplier = () -> {
            try {
                return delegate.cancelOrderByClientOrderId(clientOrderId);
            } catch (ResponseException e) {
                // Check if this is the "ORDER_IS_CLOSED" error
                if (e.getStatusCode() == 400 && e.getMessage() != null
                        && e.getMessage().toLowerCase().contains("order is closed")) {
                    logger.info(
                            "Order with client ID {} is already closed, checking actual order status to determine if canceled or filled",
                            clientOrderId);

                    try {
                        // Check the actual order status to see if it was canceled or filled
                        OrderTicket orderStatus = requestOrderStatusByClientOrderId(clientOrderId);
                        if (orderStatus != null && orderStatus.getCurrentStatus() != null) {
                            String status = orderStatus.getCurrentStatus().toString().toUpperCase();
                            if (status.contains("CANCEL")) {
                                logger.info(
                                        "Order with client ID {} was already canceled, treating cancellation request as successful",
                                        clientOrderId);
                                return new BrokerRequestResult(true, "Order already canceled");
                            } else if (status.contains("FILLED") || status.contains("COMPLETE")) {
                                logger.warn("Order with client ID {} was already filled, cannot cancel a filled order",
                                        clientOrderId);
                                return new BrokerRequestResult(false, "Cannot cancel order - order was already filled");
                            } else {
                                logger.warn(
                                        "Order with client ID {} is closed with status {}, treating as cancellation failure",
                                        clientOrderId, status);
                                return new BrokerRequestResult(false, "Order is closed with status: " + status);
                            }
                        } else {
                            logger.warn(
                                    "Could not retrieve order status for closed order with client ID {}, assuming it was canceled",
                                    clientOrderId);
                            return new BrokerRequestResult(true, "Order is closed (status unknown, assuming canceled)");
                        }
                    } catch (Exception statusException) {
                        logger.warn("Failed to check order status for closed order with client ID {}: {}",
                                clientOrderId, statusException.getMessage());
                        // If we can't check the status, we should not assume it was successfully
                        // canceled
                        throw e; // Re-throw the original exception
                    }
                }
                logger.error("Error canceling order by client ID {}: {}", clientOrderId, e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                logger.error("Error canceling order by client ID {}: {}", clientOrderId, e.getMessage(), e);
                throw e;
            }
        };

        // No circuit breaker for cancel operations - always attempt cancellation for
        // risk management

        BrokerRequestResult result = null;
        try {
            // For critical cancel operations, only use retry (no circuit breaker blocking)
            // Trading applications need to attempt cancels even if exchange seems degraded
            result = Decorators.ofSupplier(cancelOrderSupplier).withRetry(cancelOrderRetry).decorate().get();

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
        if (order.getClientOrderId() != null && !order.getClientOrderId().trim().isEmpty()) {
            return cancelOrderByClientOrderId(order.getClientOrderId());
        } else {
            return cancelOrder(order.getOrderId());
        }
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

    // Circuit Breaker Management Methods

    /**
     * Manually resets the place order circuit breaker to CLOSED state. Use this
     * when you know the service is healthy and want to bypass the circuit breaker
     * protection.
     */
    public void resetPlaceOrderCircuitBreaker() {
        placeOrderCircuitBreaker.reset();
        logger.info("Place order circuit breaker manually reset to CLOSED state");
    }

    /**
     * Manually resets the cancel order circuit breaker to CLOSED state. Use this
     * when you know the service is healthy and want to bypass the circuit breaker
     * protection.
     */
    public void resetCancelOrderCircuitBreaker() {
        cancelOrderCircuitBreaker.reset();
        logger.info("Cancel order circuit breaker manually reset to CLOSED state");
    }

    /**
     * Manually resets the order status circuit breaker to CLOSED state. Use this
     * when you know the service is healthy and want to bypass the circuit breaker
     * protection.
     */
    public void resetOrderStatusCircuitBreaker() {
        orderStatusCircuitBreaker.reset();
        logger.info("Order status circuit breaker manually reset to CLOSED state");
    }

    /**
     * Resets all circuit breakers to CLOSED state. Use this after confirming the
     * remote service is operational.
     */
    public void resetAllCircuitBreakers() {
        resetPlaceOrderCircuitBreaker();
        resetCancelOrderCircuitBreaker();
        resetOrderStatusCircuitBreaker();
        logger.info("All circuit breakers manually reset to CLOSED state");
    }

    /**
     * Gets detailed metrics for the place order circuit breaker.
     * 
     * @return circuit breaker metrics
     */
    public CircuitBreaker.Metrics getPlaceOrderCircuitBreakerMetrics() {
        return placeOrderCircuitBreaker.getMetrics();
    }

    /**
     * Gets detailed metrics for the cancel order circuit breaker.
     * 
     * @return circuit breaker metrics
     */
    public CircuitBreaker.Metrics getCancelOrderCircuitBreakerMetrics() {
        return cancelOrderCircuitBreaker.getMetrics();
    }

    /**
     * Gets detailed metrics for the order status circuit breaker.
     * 
     * @return circuit breaker metrics
     */
    public CircuitBreaker.Metrics getOrderStatusCircuitBreakerMetrics() {
        return orderStatusCircuitBreaker.getMetrics();
    }

}