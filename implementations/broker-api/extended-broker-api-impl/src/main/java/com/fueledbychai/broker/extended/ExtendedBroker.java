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
package com.fueledbychai.broker.extended;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.Fill;
import com.fueledbychai.broker.order.OrderEvent;
import com.fueledbychai.broker.order.OrderStatus;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.broker.order.TradeDirection;
import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.extended.common.api.ExtendedConfiguration;
import com.fueledbychai.extended.common.api.IExtendedRestApi;
import com.fueledbychai.extended.common.api.RestResponse;
import com.fueledbychai.extended.common.api.order.ExtendedOrder;
import com.fueledbychai.extended.common.api.order.ExtendedOrderStatus;
import com.fueledbychai.extended.common.api.order.Side;
import com.fueledbychai.extended.common.api.ws.ExtendedStreamUrls;
import com.fueledbychai.extended.common.api.ws.ExtendedWebSocketClient;
import com.fueledbychai.time.Span;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.FillDeduper;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryFactory;
import com.fueledbychai.util.Util;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Broker implementation for the Extended (extended.exchange) Starknet perps
 * DEX. Unlike Paradex, Extended requires no JWT/auth scheduler (the REST API is
 * authed internally via the API key) and exposes a single private
 * {@code /account} WebSocket stream.
 */
public class ExtendedBroker extends AbstractBasicBroker {
    protected static Logger logger = LoggerFactory.getLogger(ExtendedBroker.class);
    protected static final String LATENCY_LOGGER = "latency.extended";
    protected static boolean unitTestMode = false;

    protected FillDeduper fillDeduper = new FillDeduper();

    protected IExtendedRestApi restApi;
    protected String wsUrl;
    protected String apiKey;
    protected boolean connected = false;

    /**
     * Set true at the start of {@link #onDisconnect()} so that the WS
     * close-listener (which reconnects on socket close) bails out instead of
     * spawning new connections after an intentional teardown.
     */
    protected volatile boolean disconnecting = false;

    protected ExtendedWebSocketClient accountWSClient;
    protected ExtendedAccountStreamProcessor accountProcessor;

    protected IExtendedTranslator translator;
    protected ITickerRegistry tickerRegistry = TickerRegistryFactory.getInstance(Exchange.EXTENDED);

    /**
     * Default constructor - uses centralized configuration for API
     * initialization.
     */
    public ExtendedBroker() {
        if (!unitTestMode) {
            this.restApi = ExchangeRestApiFactory.getPrivateApi(Exchange.EXTENDED, IExtendedRestApi.class);

            ExtendedConfiguration config = ExtendedConfiguration.getInstance();
            this.wsUrl = config.getWebSocketUrl();
            this.apiKey = config.getApiKey();

            this.translator = ExtendedTranslator.getInstance();

            logger.info(
                    "ExtendedBroker initialized with configuration: Environment: {}, WebSocket URL: {}, Private API Available: {}",
                    config.getEnvironment(), config.getWebSocketUrl(), config.hasPrivateKeyConfiguration());
        }
    }

    /**
     * Constructor for testing or custom configuration.
     *
     * @param restApi custom Extended REST API instance
     */
    public ExtendedBroker(IExtendedRestApi restApi) {
        this.restApi = restApi;
        ExtendedConfiguration config = ExtendedConfiguration.getInstance();
        this.wsUrl = config.getWebSocketUrl();
        this.apiKey = config.getApiKey();
        this.translator = ExtendedTranslator.getInstance();
    }

    @Override
    public String getBrokerName() {
        return "Extended";
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        checkConnected();
        logger.info("Canceling order with ID: {}", id);
        RestResponse cancelOrderResponse;
        try (var s = Span.start("EX_CANCEL_ORDER_BY_ID_API_CALL", id, LATENCY_LOGGER)) {
            cancelOrderResponse = restApi.cancelOrder(id);
        }
        logger.info("Response code: {}", cancelOrderResponse.getHttpCode());
        if (cancelOrderResponse.isSuccessful()) {
            logger.info("Cancel order request for {} successful.", id);
            firePendingCancel(orderRegistry.getOpenOrderById(id));
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
        RestResponse cancelOrderResponse;
        try (var s = Span.start("EX_CANCEL_ORDER_BY_CLIENT_ID_API_CALL", clientOrderId, LATENCY_LOGGER)) {
            cancelOrderResponse = restApi.cancelOrderByExternalId(clientOrderId);
        }
        logger.info("Response code: {}", cancelOrderResponse.getHttpCode());

        if (!cancelOrderResponse.isSuccessful()) {
            return new BrokerRequestResult(false, true, cancelOrderResponse.getBody());
        }
        logger.info("Cancel order request for Client Order ID {} successful.", clientOrderId);
        firePendingCancel(orderRegistry.getOpenOrderByClientId(clientOrderId));
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        checkConnected();
        if (order.getClientOrderId() != null && !order.getClientOrderId().isEmpty()) {
            try (var s = Span.start("EX_CANCEL_ORDER_BY_CLIENT_ID", order.getClientOrderId(), LATENCY_LOGGER)) {
                return cancelOrderByClientOrderId(order.getClientOrderId());
            }
        }
        try (var s = Span.start("EX_CANCEL_ORDER_BY_ID", order.getOrderId(), LATENCY_LOGGER)) {
            return cancelOrder(order.getOrderId());
        }
    }

    @Override
    public BrokerRequestResult cancelOrders(List<OrderTicket> orders) {
        checkConnected();
        if (orders == null || orders.isEmpty()) {
            return new BrokerRequestResult();
        }
        for (OrderTicket order : orders) {
            if (order == null) {
                continue;
            }
            BrokerRequestResult result = cancelOrder(order);
            if (!result.isSuccess()) {
                return result;
            }
        }
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        checkConnected();
        order.setOrderEntryTime(getCurrentTime());
        ExtendedOrder extendedOrder = translator.translateOrder(order);

        String orderId;
        orderRegistry.addOpenOrder(order);
        try (var s = Span.start("EX_PLACE_ORDER_WITH_API", order.getClientOrderId(), LATENCY_LOGGER)) {
            orderId = restApi.placeOrder(extendedOrder);
        }
        logger.info("{} Order for {} placed with ID: {}", order.getDirection(), order.getTicker().getSymbol(), orderId);
        order.setOrderId(orderId);
        // add to the registry again now that we have the exchange's order ID
        orderRegistry.addOpenOrder(order);
        return new BrokerRequestResult();
    }

    @Override
    public BrokerRequestResult modifyOrder(OrderTicket order) {
        throw new UnsupportedOperationException("Modify order not supported by Extended broker");
    }

    @Override
    public String getNextOrderId() {
        try (var s = Span.start("EX_GENERATE_CLIENT_ORDER_ID", "N/A", LATENCY_LOGGER)) {
            return UUID.randomUUID().toString();
        }
    }

    @Override
    public void connect() {
        // Reset the teardown flag so a connect() after disconnect() rewires cleanly.
        disconnecting = false;

        accountProcessor = new ExtendedAccountStreamProcessor(() -> {
            logger.info("Account WebSocket closed, trying to restart...");
            startAccountWSClient();
        });
        accountProcessor.addEventListener(this::onAccountStreamEvent);

        startAccountWSClient();

        connected = true;
    }

    @Override
    protected void onDisconnect() {
        // Set the flag BEFORE closing the socket so the close-listener bails out
        // rather than spawning a fresh connection during teardown.
        disconnecting = true;

        closeQuietly(accountWSClient);
        accountWSClient = null;

        if (accountProcessor != null) {
            try {
                accountProcessor.shutdown();
            } catch (Exception ignored) {
            }
            accountProcessor = null;
        }

        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        checkConnected();
        ExtendedOrder order = restApi.getOrderById(orderId);
        return order == null ? null : translator.translateOrder(order);
    }

    @Override
    public OrderTicket requestOrderStatusByClientOrderId(String clientOrderId) {
        checkConnected();
        ExtendedOrder order = restApi.getOrderByExternalId(clientOrderId);
        return order == null ? null : translator.translateOrder(order);
    }

    @Override
    public BrokerStatus getBrokerStatus() {
        if (restApi == null) {
            logger.warn("Extended REST API is not initialized; broker status unknown.");
            return BrokerStatus.UNKNOWN;
        }
        // Extended exposes no system-status REST in our client.
        return BrokerStatus.OK;
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        return translator.translateOrders(restApi.getOpenOrders());
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<Position> getAllPositions() {
        return restApi.getPositionInfo();
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        throw new UnsupportedOperationException("Cancel all orders by ticker not implemented yet");
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        throw new UnsupportedOperationException("Cancel all orders not implemented yet");
    }

    protected void checkConnected() {
        if (!connected) {
            logger.warn("Extended broker API method called while broker is not connected");
        }
    }

    protected void firePendingCancel(OrderTicket order) {
        if (order == null) {
            return;
        }
        order.setCurrentStatus(OrderStatus.Status.PENDING_CANCEL);
        OrderStatus status = new OrderStatus(OrderStatus.Status.PENDING_CANCEL, order.getOrderId(),
                order.getFilledSize(), order.getSize().subtract(order.getFilledSize()), order.getFilledPrice(),
                order.getTicker(), getCurrentTime());
        super.fireOrderEvent(new OrderEvent(order, status));
    }

    protected String getWebSocketUrl() {
        if (wsUrl != null && !wsUrl.isBlank()) {
            return wsUrl;
        }
        return ExtendedConfiguration.getInstance().getWebSocketUrl();
    }

    public void startAccountWSClient() {
        if (disconnecting) {
            return;
        }
        logger.info("Starting Extended account WebSocket client");
        closeQuietly(accountWSClient);
        String url = new ExtendedStreamUrls(getWebSocketUrl()).account();
        try {
            accountWSClient = new ExtendedWebSocketClient(url, "account", accountProcessor, apiKey);
            accountWSClient.connect();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void closeQuietly(ExtendedWebSocketClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.debug("Error closing old websocket client", e);
            }
        }
    }

    /**
     * Handles a parsed root message from the {@code /account} stream. Switches on
     * the {@code type} field and dispatches to order/fill/balance handlers. Since
     * the exact nested field names are unverified, parsing is best-effort and
     * defensive.
     */
    protected void onAccountStreamEvent(JsonObject root) {
        if (root == null) {
            return;
        }
        String type = getString(root, "type");
        if (type == null) {
            logger.debug("Account stream message has no type, ignoring: {}", root);
            return;
        }
        JsonObject data = (root.has("data") && root.get("data").isJsonObject()) ? root.getAsJsonObject("data") : null;

        switch (type.toUpperCase()) {
        case "ORDER":
            handleOrderUpdate(data);
            break;
        case "TRADE":
            handleTradeUpdate(data);
            break;
        case "BALANCE":
            handleBalanceUpdate(data);
            break;
        default:
            logger.debug("Ignoring unhandled account stream message type: {}", type);
        }
    }

    protected void handleOrderUpdate(JsonObject data) {
        if (data == null) {
            return;
        }
        try {
            String orderId = getString(data, "id");
            String externalId = getString(data, "externalId");
            ExtendedOrderStatus extStatus = ExtendedOrderStatus.fromString(getString(data, "status"));
            OrderStatus.Status status = translator.translateStatusCode(extStatus);

            OrderTicket order = null;
            if (orderId != null) {
                order = orderRegistry.getOpenOrderById(orderId);
                if (order == null) {
                    order = orderRegistry.getCompletedOrderById(orderId);
                }
            }
            if (order == null && externalId != null) {
                order = orderRegistry.getOpenOrderByClientId(externalId);
                if (order == null) {
                    order = orderRegistry.getCompletedOrderByClientId(externalId);
                }
            }
            if (order == null) {
                logger.warn("Received order update for unknown order. id={} externalId={}", orderId, externalId);
                return;
            }

            BigDecimal filledQty = getBigDecimal(data, "filledQty");
            BigDecimal avgPrice = getBigDecimal(data, "averagePrice");
            order.setCurrentStatus(status);
            if (filledQty != null) {
                order.setFilledSize(filledQty);
            }
            if (avgPrice != null) {
                order.setFilledPrice(avgPrice);
            }

            OrderStatus orderStatus = new OrderStatus(status, order.getOrderId(), order.getFilledSize(),
                    order.getRemainingSize(), order.getFilledPrice(), order.getTicker(), getCurrentTime());

            if (status == OrderStatus.Status.FILLED || status == OrderStatus.Status.CANCELED) {
                orderRegistry.addCompletedOrder(order);
            }
            if (status == OrderStatus.Status.FILLED) {
                order.setOrderFilledTime(getCurrentTime());
            }

            super.fireOrderEvent(new OrderEvent(order, orderStatus));
        } catch (Exception e) {
            logger.warn("Failed to handle order update: {}", data, e);
        }
    }

    protected void handleTradeUpdate(JsonObject data) {
        if (data == null) {
            return;
        }
        try {
            Fill fill = new Fill();
            String market = getString(data, "market");
            if (market != null) {
                fill.setTicker(tickerRegistry.lookupByBrokerSymbol(InstrumentType.PERPETUAL_FUTURES, market));
            }
            BigDecimal price = getBigDecimal(data, "price");
            if (price != null) {
                fill.setPrice(price);
            }
            BigDecimal size = getBigDecimal(data, "qty");
            if (size != null) {
                fill.setSize(size);
            }
            fill.setFillId(getString(data, "id"));
            fill.setOrderId(getString(data, "orderId"));
            fill.setClientOrderId(getString(data, "externalId"));
            String side = getString(data, "side");
            if (side != null) {
                fill.setSide(Side.BUY.name().equalsIgnoreCase(side) ? TradeDirection.BUY : TradeDirection.SELL);
            }
            BigDecimal fee = getBigDecimal(data, "fee");
            if (fee != null) {
                fill.setCommission(fee);
            }
            if (data.has("createdTime") && !data.get("createdTime").isJsonNull()) {
                fill.setTime(Util.convertEpochToZonedDateTime(data.get("createdTime").getAsLong()));
            }

            if (fill.isSnapshot() || fillDeduper.firstTime(fill.getFillId())) {
                fireFillEvent(fill);
            } else {
                logger.warn("Duplicate fill received, ignoring: {}", fill);
            }
        } catch (Exception e) {
            logger.warn("Failed to handle trade update: {}", data, e);
        }
    }

    protected void handleBalanceUpdate(JsonObject data) {
        if (data == null) {
            return;
        }
        try {
            BigDecimal equity = getBigDecimal(data, "equity");
            if (equity == null) {
                equity = getBigDecimal(data, "balance");
            }
            if (equity != null) {
                fireAccountEquityUpdated(equity.doubleValue());
            }
        } catch (Exception e) {
            logger.warn("Failed to handle balance update: {}", data, e);
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        return el.getAsString();
    }

    private static BigDecimal getBigDecimal(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsBigDecimal();
        } catch (Exception e) {
            return null;
        }
    }
}
