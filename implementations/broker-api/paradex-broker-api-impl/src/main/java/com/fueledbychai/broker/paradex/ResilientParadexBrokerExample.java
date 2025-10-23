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

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Ticker;

/**
 * Example demonstrating how to use ResilientParadexBroker for fault-tolerant
 * trading operations.
 * 
 * This class shows the recommended approach for creating resilient trading
 * applications that can handle transient failures gracefully using Resilience4j
 * patterns.
 */
public class ResilientParadexBrokerExample {
    private static final Logger logger = LoggerFactory.getLogger(ResilientParadexBrokerExample.class);

    public static void main(String[] args) {
        // Example 1: Using ResilientParadexBroker with default configuration
        exampleWithDefaultConfiguration();

        // Example 2: Using ResilientParadexBroker with custom ParadexBroker
        exampleWithCustomDelegate();

        // Example 3: Monitoring resilience patterns
        exampleWithMonitoring();

        // Example 4: Order reconciliation feature
        exampleWithOrderReconciliation();
    }

    /**
     * Example 1: Basic usage with default configuration
     */
    private static void exampleWithDefaultConfiguration() {
        logger.info("=== Example 1: Basic Usage ===");

        // Create a resilient broker using default configuration
        // This automatically creates an underlying ParadexBroker with centralized
        // configuration
        ResilientParadexBroker resilientBroker = new ResilientParadexBroker();

        try {
            // Connect to the broker
            resilientBroker.connect();

            // Create an order
            OrderTicket order = createSampleOrder("BTC-USD", TradeDirection.BUY, "1.0", "50000.00");

            // Place the order - this will automatically retry on transient failures
            resilientBroker.placeOrder(order);

            logger.info("Order placed successfully with ID: {}", order.getOrderId());

        } catch (Exception e) {
            logger.error("Failed to place order after all retry attempts: {}", e.getMessage(), e);
        } finally {
            resilientBroker.disconnect();
        }
    }

    /**
     * Example 2: Using a custom ParadexBroker instance as delegate
     */
    private static void exampleWithCustomDelegate() {
        logger.info("=== Example 2: Custom Delegate ===");

        // Create a custom ParadexBroker (e.g., with custom configuration)
        ParadexBroker customBroker = new ParadexBroker();

        // Wrap it with resilience patterns
        ResilientParadexBroker resilientBroker = new ResilientParadexBroker(customBroker);

        try {
            resilientBroker.connect();

            // Create multiple orders to demonstrate bulk processing
            OrderTicket[] orders = { createSampleOrder("ETH-USD", TradeDirection.BUY, "10.0", "3000.00"),
                    createSampleOrder("SOL-USD", TradeDirection.SELL, "100.0", "150.00"),
                    createSampleOrder("ADA-USD", TradeDirection.BUY, "1000.0", "0.50") };

            for (OrderTicket order : orders) {
                try {
                    resilientBroker.placeOrder(order);
                    logger.info("Order placed: {} {} {} at {}", order.getDirection(), order.getSize(),
                            order.getTicker().getSymbol(), order.getLimitPrice());
                } catch (Exception e) {
                    logger.error("Failed to place order for {}: {}", order.getTicker().getSymbol(), e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error in bulk order processing: {}", e.getMessage(), e);
        } finally {
            resilientBroker.disconnect();
        }
    }

    /**
     * Example 3: Monitoring resilience patterns
     */
    private static void exampleWithMonitoring() {
        logger.info("=== Example 3: Monitoring Resilience Patterns ===");

        ResilientParadexBroker resilientBroker = new ResilientParadexBroker();

        try {
            resilientBroker.connect();

            // Monitor circuit breaker state
            logger.info("Initial circuit breaker state: {}", resilientBroker.getPlaceOrderCircuitBreakerState());

            // Monitor retry metrics
            var retryMetrics = resilientBroker.getPlaceOrderRetryMetrics();
            logger.info("Retry metrics - Successful calls: {}, Failed calls: {}",
                    retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                    retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt());

            // Place an order
            OrderTicket order = createSampleOrder("BTC-USD", TradeDirection.BUY, "0.1", "45000.00");
            resilientBroker.placeOrder(order);

            // Check metrics after operation
            retryMetrics = resilientBroker.getPlaceOrderRetryMetrics();
            logger.info("After order placement - Successful calls: {}, Failed calls: {}, Total retries: {}",
                    retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                    retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt(),
                    retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt());

            logger.info("Final circuit breaker state: {}", resilientBroker.getPlaceOrderCircuitBreakerState());

        } catch (Exception e) {
            logger.error("Error in monitoring example: {}", e.getMessage(), e);
        } finally {
            resilientBroker.disconnect();
        }
    }

    /**
     * Example 4: Demonstrating order reconciliation feature
     */
    private static void exampleWithOrderReconciliation() {
        logger.info("=== Example 4: Order Reconciliation ===");

        ResilientParadexBroker resilientBroker = new ResilientParadexBroker();

        try {
            resilientBroker.connect();

            // Create an order with a specific client order ID for tracking
            OrderTicket order = createSampleOrder("BTC-USD", TradeDirection.BUY, "0.5", "48000.00");

            // Set a custom client order ID for better tracking
            String clientOrderId = "MY_ORDER_" + System.currentTimeMillis();
            order.setClientOrderId(clientOrderId);

            logger.info("Placing order with client ID: {}", clientOrderId);

            try {
                // Place the order - if it fails, reconciliation will automatically attempt
                // to check if the order was actually placed on the server
                resilientBroker.placeOrder(order);

                logger.info("Order placement completed. Final order ID: {}, Status: {}", order.getOrderId(),
                        order.getCurrentStatus());

                // Demonstrate manual order status checking
                if (order.getOrderId() != null && !order.getOrderId().isEmpty()) {
                    logger.info("Order successfully placed and tracked with server ID: {}", order.getOrderId());
                } else {
                    logger.warn("Order placement may have had issues - no server order ID available");
                }

            } catch (Exception e) {
                logger.error("Order placement failed completely: {}", e.getMessage());

                // Even if placement failed, you can manually check order status using client
                // order ID
                try {
                    OrderTicket retrievedOrder = resilientBroker.requestOrderStatusByClientOrderId(clientOrderId);
                    if (retrievedOrder != null) {
                        logger.info("Manual reconciliation found order on server: ID = {}, Status = {}",
                                retrievedOrder.getOrderId(), retrievedOrder.getCurrentStatus());
                    } else {
                        logger.info("Manual reconciliation confirmed order was not placed on server");
                    }
                } catch (Exception reconciliationError) {
                    logger.error("Manual reconciliation also failed: {}", reconciliationError.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error in reconciliation example: {}", e.getMessage(), e);
        } finally {
            resilientBroker.disconnect();
        }

        logger.info("Key benefits of reconciliation:");
        logger.info("1. Automatic detection of successfully placed orders despite API errors");
        logger.info("2. Reduced false negatives in order placement");
        logger.info("3. Better order tracking and audit trail");
        logger.info("4. Resilient handling of network timeouts and server errors");
    }

    /**
     * Helper method to create sample orders
     */
    private static OrderTicket createSampleOrder(String symbol, TradeDirection direction, String size, String price) {
        OrderTicket order = new OrderTicket();
        order.setTicker(new Ticker(symbol));
        order.setDirection(direction);
        order.setSize(new BigDecimal(size));
        order.setLimitPrice(new BigDecimal(price));
        order.setType(OrderTicket.Type.LIMIT);
        order.setDuration(OrderTicket.Duration.DAY);
        return order;
    }
}