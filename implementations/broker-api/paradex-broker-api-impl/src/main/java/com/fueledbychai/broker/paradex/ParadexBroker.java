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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.paradex.common.api.IParadexRestApi;
import com.fueledbychai.paradex.common.api.ParadexApiFactory;
import com.fueledbychai.paradex.common.api.ParadexConfiguration;
import com.fueledbychai.paradex.common.api.RestResponse;
import com.fueledbychai.paradex.common.api.order.ParadexOrder;
import com.fueledbychai.paradex.common.api.ws.ParadexWSClientBuilder;
import com.fueledbychai.paradex.common.api.ws.ParadexWebSocketClient;
import com.fueledbychai.paradex.common.api.ws.accountinfo.AccountWebSocketProcessor;
import com.fueledbychai.paradex.common.api.ws.accountinfo.IAccountUpdate;
import com.fueledbychai.paradex.common.api.ws.fills.ParadexFill;
import com.fueledbychai.paradex.common.api.ws.fills.ParadexFillsWebSocketProcessor;
import com.fueledbychai.paradex.common.api.ws.orderstatus.IParadexOrderStatusUpdate;
import com.fueledbychai.paradex.common.api.ws.orderstatus.OrderStatusWebSocketProcessor;
import com.fueledbychai.util.FillDeduper;

/**
 */
public class ParadexBroker extends AbstractBasicBroker {
    protected static Logger logger = LoggerFactory.getLogger(ParadexBroker.class);
    protected static boolean unitTestMode = false;

    protected static int contractRequestId = 1;
    protected static int executionRequestId = 1;

    protected FillDeduper fillDeduper = new FillDeduper();

    protected IParadexRestApi restApi;
    protected String jwtToken;
    protected int jwtRefreshInSeconds = 60;
    protected boolean connected = false;

    protected ParadexWebSocketClient accountInfoWSClient;
    protected ParadexWebSocketClient orderStatusWSClient;
    protected ParadexWebSocketClient fillsWSClient;
    protected OrderStatusWebSocketProcessor orderStatusProcessor;
    protected AccountWebSocketProcessor accountWebSocketProcessor;
    protected ParadexFillsWebSocketProcessor fillsWebSocketProcessor;

    protected int nextOrderId = -1;

    protected Set<String> filledOrderSet = new HashSet<>();

    protected ScheduledExecutorService authenticationScheduler;

    protected IParadexTranslator translator;

    protected boolean started = false;

    /**
     * Default constructor - uses centralized configuration for API initialization.
     */
    public ParadexBroker() {
        if (!unitTestMode) {
            // Initialize using centralized configuration
            this.restApi = ParadexApiFactory.getPrivateApi();

            // Get JWT refresh interval from configuration
            ParadexConfiguration config = ParadexConfiguration.getInstance();
            this.jwtRefreshInSeconds = config.getJwtRefreshSeconds();

            this.translator = ParadexTranslator.getInstance();

            logger.info("ParadexBroker initialized with configuration: {}", ParadexApiFactory.getConfigurationInfo());
        }
    }

    /**
     * Constructor for testing or custom configuration.
     * 
     * @param restApi custom ParadexRestApi instance
     */
    public ParadexBroker(IParadexRestApi restApi) {
        this.restApi = restApi;
        this.jwtRefreshInSeconds = 60; // default
        this.translator = ParadexTranslator.getInstance();
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        checkConnected();
        logger.info("Canceling order with ID: {}", id);
        RestResponse cancelOrderResponse = restApi.cancelOrder(jwtToken, id);
        logger.info("Response code: {}", cancelOrderResponse.getHttpCode());
        if (cancelOrderResponse.isSuccessful()) {
            logger.info("Cancel order request for {} successful.", id);

            OrderTicket order = orderRegistry.getOpenOrderById(id);
            if (order != null) {
                order.setCurrentStatus(OrderStatus.Status.PENDING_CANCEL);
                OrderStatus status = new OrderStatus(OrderStatus.Status.PENDING_CANCEL, order.getOrderId(),
                        order.getFilledSize(), order.getSize().subtract(order.getFilledSize()), order.getFilledPrice(),
                        order.getTicker(), getCurrentTime());

                OrderEvent event = new OrderEvent(order, status);
                super.fireOrderEvent(event);

            }
        } else {
            logger.error("Failed to cancel order {}: {}", id, cancelOrderResponse.getBody());
            return new BrokerRequestResult(false, true, cancelOrderResponse.getBody());
        }

        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelOrderByClientOrderId(String clientOrderId) {
        checkConnected();
        logger.info("Canceling order with Client Order ID: {}", clientOrderId);
        RestResponse cancelOrderResponse = restApi.cancelOrderByClientOrderId(jwtToken, clientOrderId);
        logger.info("Response code: {}", cancelOrderResponse.getHttpCode());

        if (!cancelOrderResponse.isSuccessful()) {
            return new BrokerRequestResult(false, true, cancelOrderResponse.getBody());
        } else {
            logger.info("Cancel order request for Client Order ID {} successful.", clientOrderId);
            logger.info("Cancel order request for {} successful.", clientOrderId);
            OrderTicket order = orderRegistry.getOpenOrderByClientId(clientOrderId);
            if (order != null) {
                order.setCurrentStatus(OrderStatus.Status.PENDING_CANCEL);
                OrderStatus status = new OrderStatus(OrderStatus.Status.PENDING_CANCEL, order.getOrderId(),
                        order.getFilledSize(), order.getSize().subtract(order.getFilledSize()), order.getFilledPrice(),
                        order.getTicker(), getCurrentTime());

                OrderEvent event = new OrderEvent(order, status);
                super.fireOrderEvent(event);

            }

            return new BrokerRequestResult();
        }

    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        checkConnected();
        if (order.getClientOrderId() != null && !order.getClientOrderId().isEmpty()) {
            return cancelOrderByClientOrderId(order.getClientOrderId());
        } else {
            return cancelOrder(order.getOrderId());
        }
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        checkConnected();
        order.setOrderEntryTime(getCurrentTime());
        ParadexOrder paradexOrder = translator.translateOrder(order);
        String orderId = restApi.placeOrder(jwtToken, paradexOrder);
        logger.info("{} Order for {} placed with ID: {}", order.getDirection(), order.getTicker().getSymbol(), orderId);
        order.setOrderId(orderId);
        orderRegistry.addOpenOrder(order);
        return new BrokerRequestResult();
    }

    @Override
    public String getNextOrderId() {
        // generate a unique client order ID
        return UUID.randomUUID().toString();
    }

    @Override
    public void connect() {
        accountWebSocketProcessor = new AccountWebSocketProcessor(() -> {
            logger.info("Account info WebSocket closed, trying to restart...");
            startAccountInfoWSClient();
        });
        accountWebSocketProcessor.addEventListener(accountInfo -> {
            onParadexAccountInfoEvent(accountInfo);
        });

        orderStatusProcessor = new OrderStatusWebSocketProcessor(() -> {
            logger.info("Order status WebSocket closed, trying to restart...");
            startOrderStatusWSClient();
        });
        orderStatusProcessor.addEventListener(orderStatus -> {
            onParadexOrderStatusEvent(orderStatus);
        });

        fillsWebSocketProcessor = new ParadexFillsWebSocketProcessor(() -> {
            logger.info("Fills WebSocket closed, trying to restart...");
            startFillsWSClient();
        });
        fillsWebSocketProcessor.addEventListener(fill -> {
            onParadexFillEvent(fill);
        });

        startAuthenticationScheduler();
        startAccountInfoWSClient();
        startOrderStatusWSClient();
        startFillsWSClient();

        connected = true;
    }

    @Override
    protected void onDisconnect() {
        stopAuthenticationScheduler();
        connected = false;
    }

    @Override
    public boolean isConnected() {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        checkConnected();
        logger.info("Requesting order status by client order ID: {}", clientOrderId);

        try {
            ParadexOrder paradexOrder = restApi.getOrderByClientOrderId(jwtToken, clientOrderId);
            if (paradexOrder != null) {
                // Convert ParadexOrder back to OrderTicket
                OrderTicket orderTicket = translator.translateOrder(paradexOrder);
                logger.info("Retrieved order status for client ID {}: Status = {}, Order ID = {}", clientOrderId,
                        orderTicket.getCurrentStatus(), orderTicket.getOrderId());
                return orderTicket;
            } else {
                logger.warn("No order found for client order ID: {}", clientOrderId);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error requesting order status for client order ID {}: {}", clientOrderId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public List<Position> getAllPositions() {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    protected void checkConnected() {
    }

    protected void onParadexOrderStatusEvent(IParadexOrderStatusUpdate orderStatus) {
        logger.info("Received order status update: {}", orderStatus);
        OrderStatus status = translator.translateOrderStatus(orderStatus);
        OrderTicket order = orderRegistry.getOpenOrderById(orderStatus.getOrderId());
        if (order == null) {
            order = orderRegistry.getCompletedOrderById(orderStatus.getOrderId());
            if (order == null) {
                logger.warn("Received order status update for unknown order ID: {}", orderStatus.getOrderId());
                return;
            }
        }
        order.setCurrentStatus(status.getStatus());
        order.setFilledPrice(status.getFillPrice());
        order.setFilledSize(status.getFilled());
        // Can't set the order commission here, only on fill events.

        OrderEvent event = new OrderEvent(order, status);
        if (status.getStatus() == OrderStatus.Status.FILLED || status.getStatus() == OrderStatus.Status.CANCELED) {
            orderRegistry.addCompletedOrder(order);
        }

        if (status.getStatus() == OrderStatus.Status.FILLED) {
            order.setOrderFilledTime(status.getTimestamp());
        }

        super.fireOrderEvent(event);
    }

    protected void onParadexFillEvent(ParadexFill paradexFill) {
        logger.info("Received fill event: {}", paradexFill);
        Fill fill = translator.translateFill(paradexFill);
        if (fill.isSnapshot() || fillDeduper.firstTime(fill.getFillId())) {
            fireFillEvent(fill);
        } else {
            logger.warn("Duplicate fill received, ignoring: {}", fill);
        }
    }

    protected void onParadexAccountInfoEvent(IAccountUpdate accountInfo) {
        fireAccountEquityUpdated(accountInfo.getAccountValue());

    }

    private void startAuthenticationScheduler() {
        if (authenticationScheduler == null || authenticationScheduler.isShutdown()) {
            authenticationScheduler = Executors.newSingleThreadScheduledExecutor();

            // Authenticate immediately when starting
            try {
                logger.info("Initial JWT token authentication");
                jwtToken = authenticate();
            } catch (Exception e) {
                logger.error("Failed to obtain initial JWT token", e);
            }

            // Schedule authentication every minute
            authenticationScheduler.scheduleAtFixedRate(() -> {
                try {
                    logger.info("Refreshing JWT token");
                    jwtToken = authenticate();
                } catch (Exception e) {
                    logger.error("Failed to refresh JWT token", e);
                }
            }, jwtRefreshInSeconds, jwtRefreshInSeconds, TimeUnit.SECONDS);

            logger.info("Authentication scheduler started - will refresh JWT token every minute");
        }
    }

    private void stopAuthenticationScheduler() {
        if (authenticationScheduler != null && !authenticationScheduler.isShutdown()) {
            authenticationScheduler.shutdown();
            try {
                if (!authenticationScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    authenticationScheduler.shutdownNow();
                }
                logger.info("Authentication scheduler stopped");
            } catch (InterruptedException e) {
                authenticationScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    protected String authenticate() throws Exception {
        jwtToken = restApi.getJwtToken();
        logger.info("Obtained JWT Token");
        return jwtToken;
    }

    public void startAccountInfoWSClient() {
        logger.info("Starting account info WebSocket client");
        String jwtToken = restApi.getJwtToken();
        String wsUrl = ParadexApiFactory.getWebSocketUrl();

        try {
            accountInfoWSClient = ParadexWSClientBuilder.buildAccountInfoClient(wsUrl, accountWebSocketProcessor,
                    jwtToken);
            accountInfoWSClient.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);

        }

    }

    public void startOrderStatusWSClient() {
        logger.info("Starting order status WebSocket client");
        String jwtToken = restApi.getJwtToken();
        String wsUrl = ParadexApiFactory.getWebSocketUrl();

        try {
            orderStatusWSClient = ParadexWSClientBuilder.buildOrderStatusClient(wsUrl, orderStatusProcessor, jwtToken);
            orderStatusWSClient.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);

        }

    }

    public void startFillsWSClient() {
        logger.info("Starting fills WebSocket client");
        String jwtToken = restApi.getJwtToken();
        String wsUrl = ParadexApiFactory.getWebSocketUrl();

        try {
            fillsWebSocketProcessor = new ParadexFillsWebSocketProcessor(() -> {
                logger.info("Fills WebSocket closed, trying to restart...");
                startFillsWSClient();
            });
            fillsWebSocketProcessor.addEventListener(fill -> {
                onParadexFillEvent(fill);
            });

            fillsWSClient = ParadexWSClientBuilder.buildFillsClient(wsUrl, fillsWebSocketProcessor, jwtToken);
            fillsWSClient.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);

        }

    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        throw new UnsupportedOperationException("Cancel all orders by ticker not implemented yet");
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        throw new UnsupportedOperationException("Cancel all orders not implemented yet");

    }

}
